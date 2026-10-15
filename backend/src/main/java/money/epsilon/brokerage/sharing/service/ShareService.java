package money.epsilon.brokerage.sharing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.dated.DatedHistory;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.sharing.domain.ClientPartnerShare;
import money.epsilon.brokerage.sharing.repository.ClientPartnerShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which partners share a client's brokerage, and at what %, over time. A client can have one partner per role at a time
 * (a sub-broker and a referral partner); either may be at 0%. How the %s combine into money is set in
 * {@code mis.sharing.order} and applied when brokerage is attributed. Percentages cross the API as 0–100; stored as a
 * fraction (0.8 = 80%).
 */
@Service
public class ShareService {

    public static final String MF = "MF";
    static final String AUDIT_SHARE = "CLIENT_PARTNER_SHARE";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ClientPartnerShareRepository shares;
    private final ClientService clients;
    private final PartnerService partners;
    private final AuditService audit;
    private final ChangeWindow window;

    ShareService(ClientPartnerShareRepository shares, ClientService clients, PartnerService partners, AuditService audit,
                 ChangeWindow window) {
        this.shares = shares;
        this.clients = clients;
        this.partners = partners;
        this.audit = audit;
        this.window = window;
    }

    /** Current MF shares per client (sub-broker first), for the given clients; clients without any are absent. */
    @Transactional(readOnly = true)
    public Map<Long, List<ShareView>> current(Collection<Long> clientIds) {
        if (clientIds.isEmpty()) return Map.of();
        Map<Long, PartnerView> byId = partnersById();
        Map<Long, List<ShareView>> result = new HashMap<>();
        shares.findByClientIdInAndProductTypeAndValidToIsNull(clientIds, MF).stream()
                .sorted(Comparator.comparing(ClientPartnerShare::getPartnerRole))
                .forEach(s -> result.computeIfAbsent(s.getClientId(), k -> new ArrayList<>()).add(view(s, byId.get(s.getPartnerId()))));
        return result;
    }

    /** MF shares in force on {@code day} per client (sub-broker first); clients without any are absent. */
    @Transactional(readOnly = true)
    public Map<Long, List<ShareView>> on(Collection<Long> clientIds, LocalDate day) {
        if (clientIds.isEmpty()) return Map.of();
        Map<Long, PartnerView> byId = partnersById();
        Map<Long, List<ShareView>> result = new HashMap<>();
        List<Long> ids = List.copyOf(clientIds);
        for (int i = 0; i < ids.size(); i += 1000) {
            shares.findAllOn(ids.subList(i, Math.min(ids.size(), i + 1000)), MF, day).stream()
                    .sorted(Comparator.comparing(ClientPartnerShare::getPartnerRole))
                    .forEach(s -> result.computeIfAbsent(s.getClientId(), k -> new ArrayList<>()).add(view(s, byId.get(s.getPartnerId()))));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ShareView> history(long clientId) {
        clients.requireExists(clientId);
        Map<Long, PartnerView> byId = partnersById();
        return shares.findByClientIdAndProductTypeOrderByValidFromDesc(clientId, MF).stream()
                .map(s -> view(s, byId.get(s.getPartnerId()))).toList();
    }

    /** partner id → number of clients it currently shares on (MF). */
    @Transactional(readOnly = true)
    public Map<Long, Long> clientCountsByPartner() {
        return shares.countOpenByPartner(MF).stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    /**
     * From {@code from}, {@code partnerId} gets {@code percent}% in its role (its partner type) on this client. The
     * previous partner in that role ends the day before; the other role is untouched.
     */
    @Transactional
    public void set(long clientId, long partnerId, BigDecimal percent, LocalDate from, String reason) {
        clients.requireExists(clientId);
        window.beforeChange(from);
        PartnerType role = partners.get(partnerId).type();
        BigDecimal fraction = toFraction(percent);
        DatedHistory.Outcome outcome = DatedHistory.setFrom(
                shares.findFirstByClientIdAndProductTypeAndPartnerRoleOrderByValidFromDesc(clientId, MF, role), from,
                s -> s.getPartnerId().equals(partnerId) && s.getShare().compareTo(fraction) == 0,
                s -> s.correct(partnerId, fraction, reason),
                () -> new ClientPartnerShare(clientId, partnerId, role, MF, fraction, from, reason),
                shares, audit, AUDIT_SHARE, this::auditView, reason);
        if (outcome == DatedHistory.Outcome.UNCHANGED) throw new RuleViolationException("That is already the client's partner and %");
    }

    /** No partner in {@code role} shares this client after {@code lastDay}. */
    @Transactional
    public void end(long clientId, PartnerType role, LocalDate lastDay, String reason) {
        ClientPartnerShare open = shares.findFirstByClientIdAndProductTypeAndPartnerRoleOrderByValidFromDesc(clientId, MF, role)
                .filter(s -> s.getValidTo() == null)
                .orElseThrow(() -> new RuleViolationException("This client has no " + role.name().toLowerCase().replace('_', '-') + " now"));
        window.beforeChange(lastDay.plusDays(1));
        DatedHistory.end(open, lastDay, shares, audit, AUDIT_SHARE, this::auditView, reason);
    }

    /** Percent (0–100) → stored fraction. */
    static BigDecimal toFraction(BigDecimal percent) {
        if (percent == null) throw new IllegalArgumentException("Sharing % is required");
        if (percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) throw new IllegalArgumentException("Sharing % must be between 0 and 100");
        return percent.divide(HUNDRED, 6, RoundingMode.HALF_UP);
    }

    static BigDecimal toPercent(BigDecimal fraction) {
        return fraction.multiply(HUNDRED).stripTrailingZeros();
    }

    private Map<Long, PartnerView> partnersById() {
        return partners.list().stream().collect(Collectors.toMap(PartnerView::id, Function.identity()));
    }

    private ShareView view(ClientPartnerShare s, PartnerView p) {
        return new ShareView(s.getId(), s.getClientId(), s.getPartnerId(), p == null ? "?" : p.name(), s.getPartnerRole(),
                toPercent(s.getShare()), s.getValidFrom(), s.getValidTo(), s.getReason(), s.getChangedBy(), s.getChangedAt());
    }

    private AuditView auditView(ClientPartnerShare s) {
        return new AuditView(s.getClientId(), s.getPartnerId(), s.getPartnerRole(), s.getProductType(), toPercent(s.getShare()),
                s.getValidFrom(), s.getValidTo());
    }

    /** {@code percent} is 0–100. */
    public record ShareView(Long id, Long clientId, Long partnerId, String partnerName, PartnerType partnerRole, BigDecimal percent,
                            LocalDate validFrom, LocalDate validTo, String reason, String changedBy, Instant changedAt) {
    }

    public record AuditView(Long clientId, Long partnerId, PartnerType role, String product, BigDecimal percent,
                            LocalDate validFrom, LocalDate validTo) {
    }
}
