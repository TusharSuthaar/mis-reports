package money.epsilon.brokerage.sharing.service;

import java.time.LocalDate;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import money.epsilon.brokerage.mapping.service.AliasService;
import money.epsilon.brokerage.partner.domain.PartnerStatus;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerInput;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges a duplicate partner (a second spelling of the same person) into the right one: its client shares and RM links
 * move over, its spelling becomes an alias so the next file resolves to the right partner, and it is marked inactive.
 * Refused when the duplicate's shares reach a locked month (the locked split would no longer match its partner).
 */
@Service
public class PartnerMergeService {

    private final PartnerService partners;
    private final AliasService aliases;
    private final ChangeWindow window;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final money.epsilon.brokerage.partner.service.PartnerRmLinkService rmLinks;

    PartnerMergeService(PartnerService partners, AliasService aliases, ChangeWindow window, JdbcClient jdbc, AuditService audit,
                        money.epsilon.brokerage.partner.service.PartnerRmLinkService rmLinks) {
        this.rmLinks = rmLinks;
        this.partners = partners;
        this.aliases = aliases;
        this.window = window;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record MergeResult(long fromId, String fromName, long intoId, String intoName, int shares, int rmLinks, int aliases) {
    }

    @Transactional
    public MergeResult merge(long fromId, long intoId, String reason) {
        if (fromId == intoId) throw new RuleViolationException("Pick a different partner to merge into");
        PartnerView from = partners.get(fromId);
        PartnerView into = partners.get(intoId);
        if (from.type() != into.type()) {
            throw new RuleViolationException("Both must be the same kind of partner (" + from.name() + " is " + from.type()
                    + ", " + into.name() + " is " + into.type() + ")");
        }
        if (from.status() == PartnerStatus.INACTIVE) throw new RuleViolationException(from.name() + " is already inactive");

        LocalDate firstShare = jdbc.sql("SELECT min(valid_from) FROM client_partner_share WHERE partner_id = ?")
                .param(fromId).query(LocalDate.class).optional().orElse(null);
        if (firstShare != null) window.beforeChange(firstShare);

        int clash = jdbc.sql("""
                        SELECT count(*) FROM client_partner_share a JOIN client_partner_share b
                          ON a.client_id = b.client_id AND a.product_type = b.product_type AND a.partner_role = b.partner_role
                         AND a.valid_from <= coalesce(b.valid_to, 'infinity') AND b.valid_from <= coalesce(a.valid_to, 'infinity')
                        WHERE a.partner_id = ? AND b.partner_id = ?""")
                .params(fromId, intoId).query(Integer.class).single();
        if (clash > 0) throw new RuleViolationException(clash + " client(s) have both partners at the same time; fix those clients first");

        int shares = jdbc.sql("UPDATE client_partner_share SET partner_id = ? WHERE partner_id = ?").params(intoId, fromId).update();
        int links = rmLinks.moveAll(fromId, intoId);
        int moved = aliases.retarget(EntityType.PARTNER, fromId, intoId);
        boolean added = aliases.remember(ClientService.INVESTWELL, EntityType.PARTNER, from.name(), intoId);
        partners.update(fromId, new PartnerInput(null, null, null, null, null, null, PartnerStatus.INACTIVE));

        MergeResult result = new MergeResult(fromId, from.name(), intoId, into.name(), shares, links, moved + (added ? 1 : 0));
        audit.record("PARTNER", fromId, "MERGE", null, new MergeAudit(result, reason));
        return result;
    }

    record MergeAudit(MergeResult merge, String reason) {
    }
}
