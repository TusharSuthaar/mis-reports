package money.epsilon.brokerage.partner.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.org.service.EmployeeExited;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.partner.domain.Partner;
import money.epsilon.brokerage.partner.domain.PartnerRmLink;
import money.epsilon.brokerage.partner.domain.PartnerStatus;
import money.epsilon.brokerage.partner.repository.PartnerRepository;
import money.epsilon.brokerage.partner.repository.PartnerRmLinkRepository;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.dated.DatedHistory;
import money.epsilon.brokerage.platform.dated.Timeline;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Which RMs a partner works with, over time. */
@Service
public class PartnerRmLinkService {

    static final String AUDIT_LINK = "PARTNER_RM_LINK";

    private final PartnerRepository partners;
    private final PartnerRmLinkRepository links;
    private final EmployeeService employees;
    private final AuditService audit;

    PartnerRmLinkService(PartnerRepository partners, PartnerRmLinkRepository links, EmployeeService employees,
                         AuditService audit) {
        this.partners = partners;
        this.links = links;
        this.employees = employees;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<LinkView> links(long partnerId) {
        findPartner(partnerId);
        return links.findByPartnerIdOrderByValidFromDesc(partnerId).stream().map(this::view).toList();
    }

    /** The latest link between the two, if any (open or closed). */
    @Transactional(readOnly = true)
    public Optional<LinkView> latest(long partnerId, long rmId) {
        return links.findFirstByPartnerIdAndRmIdOrderByValidFromDesc(partnerId, rmId).map(this::view);
    }

    /** Links the partner to an RM from {@code from}, optionally until {@code to} (inclusive). */
    @Transactional
    public LinkView link(long partnerId, long rmId, LocalDate from, LocalDate to, String reason) {
        Partner partner = findPartner(partnerId);
        if (partner.getStatus() == PartnerStatus.INACTIVE) throw new RuleViolationException(partner.getName() + " is inactive");
        EmployeeView rm = employees.requireRmEmployedOn(rmId, from);
        Optional<PartnerRmLink> latest = links.findFirstByPartnerIdAndRmIdOrderByValidFromDesc(partnerId, rmId);
        if (latest.filter(l -> l.getValidTo() == null).isPresent()) {
            throw new RuleViolationException(partner.getName() + " is already linked to " + rm.name() + " since "
                    + latest.get().getValidFrom().format(Timeline.DISPLAY));
        }
        DatedHistory.makeRoom(latest, from, links, audit, AUDIT_LINK, this::auditView, reason);
        LocalDate end = to;
        if (rm.exitedOn() != null && (end == null || end.isAfter(rm.exitedOn()))) end = rm.exitedOn();
        PartnerRmLink saved = DatedHistory.add(new PartnerRmLink(partnerId, rmId, from, end, reason), links, audit, AUDIT_LINK, this::auditView);
        return view(saved);
    }

    @Transactional
    public void endLink(long partnerId, long linkId, LocalDate lastDay, String reason) {
        PartnerRmLink link = links.findById(linkId).filter(l -> l.getPartnerId().equals(partnerId))
                .orElseThrow(() -> new NotFoundException("Link " + linkId + " not found"));
        DatedHistory.end(link, lastDay, links, audit, AUDIT_LINK, this::auditView, reason);
    }

    /**
     * Hands every RM link of a merged duplicate to the partner it was merged into. Refused when both are linked to the
     * same RM at the same time. @return how many links moved
     */
    @Transactional
    public int moveAll(long fromPartnerId, long intoPartnerId) {
        List<PartnerRmLink> from = links.findByPartnerIdOrderByValidFromDesc(fromPartnerId);
        List<PartnerRmLink> into = links.findByPartnerIdOrderByValidFromDesc(intoPartnerId);
        for (PartnerRmLink a : from) {
            for (PartnerRmLink b : into) {
                boolean overlap = a.getRmId().equals(b.getRmId())
                        && !a.getValidFrom().isAfter(b.getValidTo() == null ? LocalDate.MAX : b.getValidTo())
                        && !b.getValidFrom().isAfter(a.getValidTo() == null ? LocalDate.MAX : a.getValidTo());
                if (overlap) throw new RuleViolationException("Both partners are linked to the same RM at the same time; end one link first");
            }
        }
        for (PartnerRmLink a : from) {
            Object before = auditView(a);
            a.moveTo(intoPartnerId);
            links.saveAndFlush(a);
            audit.record(AUDIT_LINK, a.getId(), "MOVE", before, auditView(a));
        }
        return from.size();
    }

    /** An RM who leaves stops working with partners on their last day. */
    @EventListener
    public void onEmployeeExited(EmployeeExited event) {
        if (!links.findByRmIdAndValidFromAfter(event.employeeId(), event.lastDay()).isEmpty()) {
            throw new RuleViolationException(event.employeeName() + " has a partner link starting after the exit date");
        }
        List<Long> partnersToHandOver = new java.util.ArrayList<>();
        for (PartnerRmLink open : links.findByRmIdAndValidToIsNull(event.employeeId())) {
            DatedHistory.end(open, event.lastDay(), links, audit, AUDIT_LINK, this::auditView, event.reason());
            partnersToHandOver.add(open.getPartnerId());
        }
        // R-61: an RM replacement works with the same partners from the next day (a house book has no partners).
        if (event.replacedBy() == null || employees.get(event.replacedBy()).type() != money.epsilon.brokerage.org.domain.EmployeeType.RM) return;
        LocalDate from = event.lastDay().plusDays(1);
        String reason = event.reason() == null ? "Took over from " + event.employeeName() : event.reason();
        for (Long partnerId : partnersToHandOver) {
            boolean alreadyLinked = links.findFirstByPartnerIdAndRmIdOrderByValidFromDesc(partnerId, event.replacedBy())
                    .filter(l -> l.getValidTo() == null).isPresent();
            if (!alreadyLinked && findPartner(partnerId).getStatus() != PartnerStatus.INACTIVE) {
                link(partnerId, event.replacedBy(), from, null, reason);
            }
        }
    }

    private Partner findPartner(long id) {
        return partners.findById(id).orElseThrow(() -> new NotFoundException("Partner " + id + " not found"));
    }

    private LinkAudit auditView(PartnerRmLink l) {
        return new LinkAudit(l.getPartnerId(), l.getRmId(), l.getValidFrom(), l.getValidTo(), l.getReason());
    }

    private LinkView view(PartnerRmLink l) {
        EmployeeView rm = employees.get(l.getRmId());
        return new LinkView(l.getId(), l.getPartnerId(), rm.id(), rm.empCode(), rm.name(), l.getValidFrom(), l.getValidTo(),
                l.getReason(), l.getChangedBy(), l.getChangedAt());
    }

    public record LinkView(Long id, Long partnerId, Long rmId, String rmCode, String rmName, LocalDate validFrom,
                           LocalDate validTo, String reason, String changedBy, Instant changedAt) {
    }

    public record LinkAudit(Long partnerId, Long rmId, LocalDate validFrom, LocalDate validTo, String reason) {
    }
}
