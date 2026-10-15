package money.epsilon.brokerage.sharing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.org.service.BranchService;
import money.epsilon.brokerage.org.service.OrgChartService;
import money.epsilon.brokerage.org.service.OrgChartService.OrgChart;
import money.epsilon.brokerage.partner.domain.PartnerStatus;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.dated.DatedHistory;
import money.epsilon.brokerage.platform.time.BusinessCalendar;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.sharing.domain.BranchPartner;
import money.epsilon.brokerage.sharing.domain.ClientPartnerShare;
import money.epsilon.brokerage.sharing.domain.PartnerProductShare;
import money.epsilon.brokerage.sharing.domain.Product;
import money.epsilon.brokerage.sharing.repository.BranchPartnerRepository;
import money.epsilon.brokerage.sharing.repository.ClientPartnerShareRepository;
import money.epsilon.brokerage.sharing.repository.PartnerProductShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which partners share a client's brokerage, and at what %, over time. A client has at most one partner per role at a time
 * (a sub-broker and a referral partner). The % that counts (rules R-53..R-55):
 * <ol>
 *   <li>a partner whose approval is pending (PENDING) gets 0;</li>
 *   <li>else the client's own % on the link, when one was set (an exception for this client);</li>
 *   <li>else the partner's % for the product on that day; a partner without one gets 0 and is reported.</li>
 * </ol>
 * A branch rule (R-57) adds a partner to every client of a branch (by the client's RM that day): it is listed first in
 * its role, so it is paid before the client's own partners.
 * How the %s combine into money is set in {@code mis.sharing.order}. Percentages cross the API as 0–100; stored as a
 * fraction (0.8 = 80%).
 */
@Service
public class ShareService {

    public static final String MF = Product.MF.code();
    static final String AUDIT_SHARE = "CLIENT_PARTNER_SHARE";
    static final String AUDIT_PRODUCT_SHARE = "PARTNER_PRODUCT_SHARE";
    static final String AUDIT_BRANCH_PARTNER = "BRANCH_PARTNER";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Where the % that counts comes from. */
    public enum Source { CLIENT, PARTNER, PENDING, NO_PARTNER_PERCENT }

    private final ClientPartnerShareRepository shares;
    private final PartnerProductShareRepository productShares;
    private final BranchPartnerRepository branchPartners;
    private final ClientService clients;
    private final PartnerService partners;
    private final OrgChartService org;
    private final BranchService branches;
    private final BusinessCalendar calendar;
    private final AuditService audit;
    private final ChangeWindow window;

    ShareService(ClientPartnerShareRepository shares, PartnerProductShareRepository productShares, BranchPartnerRepository branchPartners,
                 ClientService clients, PartnerService partners, OrgChartService org, BranchService branches, BusinessCalendar calendar,
                 AuditService audit, ChangeWindow window) {
        this.shares = shares;
        this.productShares = productShares;
        this.branchPartners = branchPartners;
        this.org = org;
        this.branches = branches;
        this.calendar = calendar;
        this.clients = clients;
        this.partners = partners;
        this.audit = audit;
        this.window = window;
    }

    /** Current MF shares per client (sub-broker first), for the given clients; clients without any are absent. */
    @Transactional(readOnly = true)
    public Map<Long, List<ShareView>> current(Collection<Long> clientIds) {
        if (clientIds.isEmpty()) return Map.of();
        List<ClientPartnerShare> rows = shares.findByClientIdInAndProductTypeAndValidToIsNull(clientIds, MF);
        LocalDate today = calendar.today();
        Map<Long, BigDecimal> fractions = new HashMap<>(currentPartnerFractions(MF));
        return withBranchRules(byClient(rows, fractions), clientIds, today, fractions);
    }

    /** The client's own partner links only, without branch rules (what Master MIS compares with). */
    @Transactional(readOnly = true)
    public Map<Long, List<ShareView>> currentLinks(Collection<Long> clientIds) {
        if (clientIds.isEmpty()) return Map.of();
        return byClient(shares.findByClientIdInAndProductTypeAndValidToIsNull(clientIds, MF), currentPartnerFractions(MF));
    }

    /** MF shares in force on {@code day} per client (sub-broker first), with the % that counts that day. */
    @Transactional(readOnly = true)
    public Map<Long, List<ShareView>> on(Collection<Long> clientIds, LocalDate day) {
        if (clientIds.isEmpty()) return Map.of();
        List<Long> ids = List.copyOf(clientIds);
        List<ClientPartnerShare> rows = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 1000) {
            rows.addAll(shares.findAllOn(ids.subList(i, Math.min(ids.size(), i + 1000)), MF, day));
        }
        List<BranchPartner> rules = branchPartners.findAllOn(day);
        Set<Long> partnerIds = rows.stream().map(ClientPartnerShare::getPartnerId).collect(Collectors.toCollection(HashSet::new));
        rules.forEach(r -> partnerIds.add(r.getPartnerId()));
        Map<Long, BigDecimal> partnerFractions = partnerIds.isEmpty() ? new HashMap<>()
                : productShares.findAllOn(partnerIds, MF, day).stream()
                        .collect(Collectors.toMap(PartnerProductShare::getPartnerId, PartnerProductShare::getShare, (a, b) -> a, HashMap::new));
        return withBranchRules(byClient(rows, partnerFractions), clientIds, day, partnerFractions);
    }

    /** The client's MF partner history; each row's % is the client's own, else the partner's % on the row's first day. */
    @Transactional(readOnly = true)
    public List<ShareView> history(long clientId) {
        clients.requireExists(clientId);
        Map<Long, PartnerView> byId = partnersById();
        return shares.findByClientIdAndProductTypeOrderByValidFromDesc(clientId, MF).stream()
                .map(s -> view(s, byId.get(s.getPartnerId()), partnerFractionOn(s.getPartnerId(), MF, s.getValidFrom())))
                .toList();
    }

    /** partner id → number of clients it currently shares on (MF). */
    @Transactional(readOnly = true)
    public Map<Long, Long> clientCountsByPartner() {
        return shares.countOpenByPartner(MF).stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    /**
     * From {@code from}, {@code partnerId} shares this client in its role (its partner type). {@code percent} null = the
     * partner's own % per product applies; a number is an exception for this client. The previous partner in that role
     * ends the day before; the other role is untouched.
     */
    @Transactional
    public void set(long clientId, long partnerId, BigDecimal percent, LocalDate from, String reason) {
        if (!apply(clientId, partnerId, percent, from, reason)) throw new RuleViolationException("That is already the client's partner and %");
    }

    /** Like {@link #set} but a no-op when nothing changes. @return whether anything changed */
    @Transactional
    public boolean apply(long clientId, long partnerId, BigDecimal percent, LocalDate from, String reason) {
        clients.requireExists(clientId);
        window.beforeChange(from);
        PartnerType role = partners.get(partnerId).type();
        BigDecimal fraction = percent == null ? null : toFraction(percent);
        DatedHistory.Outcome outcome = DatedHistory.setFrom(
                shares.findFirstByClientIdAndProductTypeAndPartnerRoleOrderByValidFromDesc(clientId, MF, role), from,
                s -> s.getPartnerId().equals(partnerId) && sameFraction(s.getShare(), fraction),
                s -> s.correct(partnerId, fraction, reason),
                () -> new ClientPartnerShare(clientId, partnerId, role, MF, fraction, from, reason),
                shares, audit, AUDIT_SHARE, this::auditView, reason);
        return outcome != DatedHistory.Outcome.UNCHANGED;
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

    // ---- partner % per product -------------------------------------------------------------------------------

    /** Every partner's current % per product (0–100). Partners without any are absent. */
    @Transactional(readOnly = true)
    public Map<Long, Map<Product, BigDecimal>> currentProductPercents() {
        Map<Long, Map<Product, BigDecimal>> result = new HashMap<>();
        for (PartnerProductShare s : productShares.findByValidToIsNull()) {
            Product.find(s.getProductType()).ifPresent(p ->
                    result.computeIfAbsent(s.getPartnerId(), k -> new EnumMap<>(Product.class)).put(p, toPercent(s.getShare())));
        }
        return result;
    }

    /** One partner's product % history, newest first per product. */
    @Transactional(readOnly = true)
    public List<ProductShareView> productHistory(long partnerId) {
        partners.get(partnerId);
        return productShares.findByPartnerIdOrderByProductTypeAscValidFromDesc(partnerId).stream().map(this::productView).toList();
    }

    /** From {@code from}, the partner's % for {@code product}. @return whether anything changed */
    @Transactional
    public boolean setProductPercent(long partnerId, Product product, BigDecimal percent, LocalDate from, String reason) {
        partners.get(partnerId);
        if (product == null) throw new IllegalArgumentException("Choose a product");
        window.beforeChange(from);
        BigDecimal fraction = toFraction(percent);
        DatedHistory.Outcome outcome = DatedHistory.setFrom(
                productShares.findFirstByPartnerIdAndProductTypeOrderByValidFromDesc(partnerId, product.code()), from,
                s -> s.getShare().compareTo(fraction) == 0,
                s -> s.correct(fraction, reason),
                () -> new PartnerProductShare(partnerId, product.code(), fraction, from, reason),
                productShares, audit, AUDIT_PRODUCT_SHARE, this::productAuditView, reason);
        return outcome != DatedHistory.Outcome.UNCHANGED;
    }

    // ---- branch rules (R-57) ---------------------------------------------------------------------------------

    /** Branch rules in force today: branch id → rule. */
    @Transactional(readOnly = true)
    public List<BranchPartnerView> currentBranchPartners() {
        Map<Long, PartnerView> byId = partnersById();
        return branchPartners.findAllOn(calendar.today()).stream().map(r -> branchView(r, byId)).toList();
    }

    @Transactional(readOnly = true)
    public List<BranchPartnerView> branchPartnerHistory(long branchId) {
        branches.get(branchId);
        Map<Long, PartnerView> byId = partnersById();
        return branchPartners.findByBranchIdOrderByValidFromDesc(branchId).stream().map(r -> branchView(r, byId)).toList();
    }

    /** From {@code from}, {@code partnerId} shares in every client of the branch, before their own partners. */
    @Transactional
    public void setBranchPartner(long branchId, long partnerId, LocalDate from, String reason) {
        branches.get(branchId);
        partners.get(partnerId);
        window.beforeChange(from);
        DatedHistory.Outcome outcome = DatedHistory.setFrom(branchPartners.findFirstByBranchIdOrderByValidFromDesc(branchId), from,
                r -> r.getPartnerId().equals(partnerId), r -> r.correct(partnerId, reason),
                () -> new BranchPartner(branchId, partnerId, from, reason),
                branchPartners, audit, AUDIT_BRANCH_PARTNER, this::branchAuditView, reason);
        if (outcome == DatedHistory.Outcome.UNCHANGED) throw new RuleViolationException("That is already the branch's partner");
    }

    /** The branch has no partner for all its clients after {@code lastDay}. */
    @Transactional
    public void endBranchPartner(long branchId, LocalDate lastDay, String reason) {
        BranchPartner open = branchPartners.findFirstByBranchIdOrderByValidFromDesc(branchId).filter(r -> r.getValidTo() == null)
                .orElseThrow(() -> new RuleViolationException("This branch has no partner for all clients now"));
        window.beforeChange(lastDay.plusDays(1));
        DatedHistory.end(open, lastDay, branchPartners, audit, AUDIT_BRANCH_PARTNER, this::branchAuditView, reason);
    }

    /**
     * Adds each client's branch partner (by its RM's branch on {@code day}) first in that partner's role, unless the
     * client already has that very partner. {@code fractions} gains the branch partners' %s if missing.
     */
    private Map<Long, List<ShareView>> withBranchRules(Map<Long, List<ShareView>> result, Collection<Long> clientIds, LocalDate day,
                                                       Map<Long, BigDecimal> fractions) {
        List<BranchPartner> rules = branchPartners.findAllOn(day);
        if (rules.isEmpty() || clientIds.isEmpty()) return result;
        Map<Long, BranchPartner> ruleByBranch = rules.stream().collect(Collectors.toMap(BranchPartner::getBranchId, r -> r));
        Map<Long, Long> branchOfRm = new HashMap<>();
        Map<Long, String> branchName = new HashMap<>();
        OrgChart chart = org.asOf(day);
        chart.regions().forEach(region -> region.branches().forEach(b -> {
            branchName.put(b.id(), b.name());
            b.people().forEach(p -> branchOfRm.put(p.id(), b.id()));
        }));
        chart.branchesWithoutRegion().forEach(b -> {
            branchName.put(b.id(), b.name());
            b.people().forEach(p -> branchOfRm.put(p.id(), b.id()));
        });
        Set<Long> missing = rules.stream().map(BranchPartner::getPartnerId).filter(id -> !fractions.containsKey(id)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            productShares.findAllOn(missing, MF, day).forEach(s -> fractions.put(s.getPartnerId(), s.getShare()));
        }
        Map<Long, PartnerView> byId = partnersById();
        Map<Long, List<ShareView>> out = new HashMap<>(result);
        clients.rmsOn(clientIds, day).forEach((clientId, rmId) -> {
            Long branchId = branchOfRm.get(rmId);
            BranchPartner rule = branchId == null ? null : ruleByBranch.get(branchId);
            if (rule == null) return;
            List<ShareView> own = out.getOrDefault(clientId, List.of());
            if (own.stream().anyMatch(s -> s.partnerId().equals(rule.getPartnerId()))) return;
            PartnerView p = byId.get(rule.getPartnerId());
            ShareView v = view(null, clientId, p, rule.getPartnerId(), null, fractions.get(rule.getPartnerId()), rule.getValidFrom(),
                    rule.getValidTo(), rule.getReason(), rule.getChangedBy(), rule.getChangedAt(), branchName.get(branchId));
            List<ShareView> list = new ArrayList<>(own);
            list.add(v);
            // Role order first; within a role the branch partner comes first (it is paid before the client's own).
            list.sort(Comparator.comparing(ShareView::partnerRole).thenComparing(s -> s.branch() == null));
            out.put(clientId, list);
        });
        return out;
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /** Percent (0–100) → stored fraction. */
    static BigDecimal toFraction(BigDecimal percent) {
        if (percent == null) throw new IllegalArgumentException("Sharing % is required");
        if (percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) throw new IllegalArgumentException("Sharing % must be between 0 and 100");
        return percent.divide(HUNDRED, 6, RoundingMode.HALF_UP);
    }

    static BigDecimal toPercent(BigDecimal fraction) {
        return fraction == null ? null : fraction.multiply(HUNDRED).stripTrailingZeros();
    }

    private static boolean sameFraction(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private Map<Long, List<ShareView>> byClient(List<ClientPartnerShare> rows, Map<Long, BigDecimal> partnerFractions) {
        Map<Long, PartnerView> byId = partnersById();
        Map<Long, List<ShareView>> result = new HashMap<>();
        rows.stream()
                .sorted(Comparator.comparing(ClientPartnerShare::getPartnerRole))
                .forEach(s -> result.computeIfAbsent(s.getClientId(), k -> new ArrayList<>())
                        .add(view(s, byId.get(s.getPartnerId()), partnerFractions.get(s.getPartnerId()))));
        return result;
    }

    private Map<Long, BigDecimal> currentPartnerFractions(String product) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (PartnerProductShare s : productShares.findByValidToIsNull()) {
            if (s.getProductType().equals(product)) result.put(s.getPartnerId(), s.getShare());
        }
        return result;
    }

    private BigDecimal partnerFractionOn(long partnerId, String product, LocalDate day) {
        return productShares.findAllOn(Set.of(partnerId), product, day).stream().findFirst().map(PartnerProductShare::getShare).orElse(null);
    }

    private Map<Long, PartnerView> partnersById() {
        return partners.list().stream().collect(Collectors.toMap(PartnerView::id, Function.identity()));
    }

    /** {@code partnerFraction}: the partner's % for the product on the day in question (null = none set). */
    private ShareView view(ClientPartnerShare s, PartnerView p, BigDecimal partnerFraction) {
        return view(s.getId(), s.getClientId(), p, s.getPartnerId(), s.getShare(), partnerFraction, s.getValidFrom(), s.getValidTo(),
                s.getReason(), s.getChangedBy(), s.getChangedAt(), null);
    }

    private ShareView view(Long id, Long clientId, PartnerView p, Long partnerId, BigDecimal clientFraction, BigDecimal partnerFraction,
                           LocalDate from, LocalDate to, String reason, String changedBy, Instant changedAt, String branch) {
        Source source;
        BigDecimal fraction;
        if (p != null && p.status() == PartnerStatus.PENDING) {
            source = Source.PENDING;
            fraction = BigDecimal.ZERO;
        } else if (clientFraction != null) {
            source = Source.CLIENT;
            fraction = clientFraction;
        } else if (partnerFraction != null) {
            source = Source.PARTNER;
            fraction = partnerFraction;
        } else {
            source = Source.NO_PARTNER_PERCENT;
            fraction = BigDecimal.ZERO;
        }
        return new ShareView(id, clientId, partnerId, p == null ? "?" : p.name(), p == null ? null : p.type(),
                toPercent(fraction), toPercent(clientFraction), source, from, to, reason, changedBy, changedAt, branch);
    }

    private BranchPartnerView branchView(BranchPartner r, Map<Long, PartnerView> byId) {
        PartnerView p = byId.get(r.getPartnerId());
        return new BranchPartnerView(r.getId(), r.getBranchId(), r.getPartnerId(), p == null ? "?" : p.name(), r.getValidFrom(),
                r.getValidTo(), r.getReason(), r.getChangedBy(), r.getChangedAt());
    }

    private BranchAuditView branchAuditView(BranchPartner r) {
        return new BranchAuditView(r.getBranchId(), r.getPartnerId(), r.getValidFrom(), r.getValidTo());
    }

    private AuditView auditView(ClientPartnerShare s) {
        return new AuditView(s.getClientId(), s.getPartnerId(), s.getPartnerRole(), s.getProductType(), toPercent(s.getShare()),
                s.getValidFrom(), s.getValidTo());
    }

    private ProductShareView productView(PartnerProductShare s) {
        return new ProductShareView(s.getId(), s.getPartnerId(), Product.find(s.getProductType()).orElse(null), toPercent(s.getShare()),
                s.getValidFrom(), s.getValidTo(), s.getReason(), s.getChangedBy(), s.getChangedAt());
    }

    private ProductAuditView productAuditView(PartnerProductShare s) {
        return new ProductAuditView(s.getPartnerId(), s.getProductType(), toPercent(s.getShare()), s.getValidFrom(), s.getValidTo());
    }

    /** Partners among {@code partnerIds} that have no % for {@code product} on {@code day}. */
    @Transactional(readOnly = true)
    public Set<Long> withoutProductPercent(Collection<Long> partnerIds, Product product, LocalDate day) {
        if (partnerIds.isEmpty()) return Set.of();
        Set<Long> missing = new HashSet<>(partnerIds);
        productShares.findAllOn(partnerIds, product.code(), day).forEach(s -> missing.remove(s.getPartnerId()));
        return missing;
    }

    /**
     * {@code percent}: the % that counts (0–100); {@code clientPercent}: the client's own % (an exception), null when the
     * partner's % applies; {@code source}: where {@code percent} comes from; {@code branch}: set when the partner comes from
     * that branch's rule rather than the client's own link ({@code id} is then null).
     */
    public record ShareView(Long id, Long clientId, Long partnerId, String partnerName, PartnerType partnerRole, BigDecimal percent,
                            BigDecimal clientPercent, Source source, LocalDate validFrom, LocalDate validTo, String reason,
                            String changedBy, Instant changedAt, String branch) {
    }

    public record BranchPartnerView(Long id, Long branchId, Long partnerId, String partnerName, LocalDate validFrom, LocalDate validTo,
                                    String reason, String changedBy, Instant changedAt) {
    }

    public record BranchAuditView(Long branchId, Long partnerId, LocalDate validFrom, LocalDate validTo) {
    }

    /** {@code percent} null = no client exception (the partner's % applies). */
    public record AuditView(Long clientId, Long partnerId, PartnerType role, String product, BigDecimal percent,
                            LocalDate validFrom, LocalDate validTo) {
    }

    public record ProductShareView(Long id, Long partnerId, Product product, BigDecimal percent, LocalDate validFrom,
                                   LocalDate validTo, String reason, String changedBy, Instant changedAt) {
    }

    public record ProductAuditView(Long partnerId, String product, BigDecimal percent, LocalDate validFrom, LocalDate validTo) {
    }
}
