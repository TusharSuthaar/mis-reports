package money.epsilon.brokerage.insurance.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.mapping.service.AliasService;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import money.epsilon.brokerage.insurance.domain.InsuranceCategoryRules;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.BusinessType;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.Category;
import money.epsilon.brokerage.insurance.repository.InsurancePolicyRepository;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgChartService;
import money.epsilon.brokerage.org.service.OrgChartService.OrgChart;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerInput;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.EditVersion;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.platform.web.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insurance policies (R-94, R-95): typed on the Insurance screen or uploaded as the insurance team's "Policy level data"
 * sheet. Every policy is mapped to a client by PAN (or picked by client code / name on the screen) and takes that client's
 * RM from the client master; the POSP is a partner of type POSP. Region, branch and RD come from the RM's place in the org
 * that month. Every change is audited.
 */
@Service
public class InsuranceService {

    static final String AUDIT = "INSURANCE_POLICY";
    /** The source of a confirmed sheet name in remembered matches (R-100). */
    static final String ALIAS_SOURCE = "INSURANCE";
    static final String SHEET = "Policy level data";
    static final String H_MONTH = "Business Month", H_POLICY = "Policy No", H_POSP = "POSP Name", H_INSURER = "Insurance Company",
            H_CAT = "CAT", H_CLIENT = "Client", H_PAN = "PAN", H_SUB = "Insurance Sub Type", H_PRODUCT = "Product Name", H_PPT = "PPT",
            H_PT = "Policy Term (PT)", H_SA = "Sum Assured", H_OD = "OD Premium", H_TP = "TP Premium", H_TOTAL = "Total Premium",
            H_TYPE = "Business Type", H_EXP_PCT = "% Expect Comm.", H_ACT_PCT = "Actual Comm.%", H_EXP = "Expect Comm.",
            H_ACT = "Actual Comm.", H_AGREED = "Agreed % sharing with POSP", H_POSP_COMM = "POSP Ideal Commission";
    static final List<String> H_NET = List.of("Net Premium", "Net Premuim");
    static final List<String> H_REMARKS = List.of("Remarks", "Reamrks");
    /** POSP names in the sheet that mean "no POSP". */
    static final Set<String> NO_POSP = Set.of("NA", "N/A", "NONE", "DIRECT", "SELF", "-");
    static final BigDecimal HUNDRED = new BigDecimal("100");

    private final InsurancePolicyRepository policies;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final PanCipher panCipher;
    private final AuditService audit;
    private final ChangeWindow months;
    private final AliasService aliases;

    InsuranceService(InsurancePolicyRepository policies, ClientService clients, EmployeeService employees, OrgChartService org,
                     PartnerService partners, PanCipher panCipher, AuditService audit, ChangeWindow months,
                     AliasService aliases) {
        this.policies = policies;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.panCipher = panCipher;
        this.audit = audit;
        this.months = months;
        this.aliases = aliases;
    }

    // ---- list --------------------------------------------------------------------------------------------------------

    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long pospId, String insurer, Category category,
                         boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public List<PolicyView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<InsurancePolicy> found = policies.search(from, to, f.includeCancelled(), InsurancePolicy.Status.ACTIVE, f.rmId(), f.pospId()).stream()
                .filter(p -> f.category() == null || f.category() == p.getCategory())
                .filter(p -> f.insurer() == null || f.insurer().isBlank() || Text.key(p.getInsurer()).equals(Text.key(f.insurer())))
                .toList();
        List<PolicyView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientCode(), q) || contains(v.policyNo(), q)
                || contains(v.productName(), q) || contains(v.pospName(), q) || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    @Transactional(readOnly = true)
    public PolicyView get(long id) {
        return views(List.of(find(id))).get(0);
    }

    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return InsuranceWorkbook.build(search(f));
    }

    /** Insurers and products already used, for the screen's suggestions. */
    public record Choices(List<String> insurers, List<String> products, List<String> subTypes) {
    }

    @Transactional(readOnly = true)
    public Choices choices() {
        Set<String> insurers = new LinkedHashSet<>(), products = new LinkedHashSet<>(), subTypes = new LinkedHashSet<>();
        policies.findTop500ByStatusOrderByIdDesc(InsurancePolicy.Status.ACTIVE).forEach(p -> {
            insurers.add(p.getInsurer());
            products.add(p.getProductName());
            if (p.getSubType() != null) subTypes.add(p.getSubType());
        });
        return new Choices(sorted(insurers), sorted(products), sorted(subTypes));
    }

    private static List<String> sorted(Set<String> s) {
        return s.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /**
     * What the screen fills in for a new policy (R-94), from the latest policy with the same insurer and product (category,
     * sub type, expected %) and the POSP's latest policy (agreed sharing %). Nulls where there is none.
     */
    public record Defaults(Category category, String subType, BigDecimal expectedPercent, BigDecimal pospShare) {
    }

    @Transactional(readOnly = true)
    public Defaults defaults(String insurer, String productName, Long pospId) {
        List<InsurancePolicy> recent = policies.findTop500ByStatusOrderByIdDesc(InsurancePolicy.Status.ACTIVE);
        InsurancePolicy same = recent.stream().filter(p -> Text.key(p.getInsurer()).equals(Text.key(insurer))
                && Text.key(p.getProductName()).equals(Text.key(productName))).findFirst().orElse(null);
        BigDecimal share = pospId == null ? null : recent.stream().filter(p -> pospId.equals(p.getPospId()) && p.getPospShare() != null)
                .map(InsurancePolicy::getPospShare).findFirst().orElse(null);
        return new Defaults(same == null ? null : same.getCategory(), same == null ? null : same.getSubType(),
                same == null ? null : same.getExpectedPercent(), share);
    }

    // ---- entry -------------------------------------------------------------------------------------------------------

    /** A policy as typed on the screen. The RM is the client's (R-94); {@code rmId} is only for a client that has none yet. */
    public record PolicyInput(LocalDate month, Long pospId, String newPospName, String insurer, Category category, String subType,
                              String productName, String policyNo, BusinessType businessType, String ppt, Integer policyTerm,
                              BigDecimal sumAssured, BigDecimal odPremium, BigDecimal tpPremium, BigDecimal totalPremium,
                              BigDecimal netPremium, BigDecimal expectedPercent, BigDecimal expectedComm, BigDecimal actualPercent,
                              BigDecimal actualComm, BigDecimal pospShare, BigDecimal pospComm, String remarks) {
    }

    @Transactional
    public PolicyView create(Long clientId, String newClientName, String newClientPan, Long rmId, PolicyInput in) {
        LocalDate month = month(in.month());
        months.beforeEntryChange(month);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new ValidationException("Find the client by PAN or code, or enter a new client's name");
            if (rmId == null) throw new ValidationException("Choose the new client's RM");
            employees.requireEmployedOn(rmId, month);
            client = clients.addByHand(newClientName, newClientPan, rmId, month, "Insurance policy");
        }
        InsurancePolicy p = new InsurancePolicy(client, InsurancePolicy.Source.SCREEN, null);
        p.fill(details(client, rmId, in, true));
        policies.saveAndFlush(p);
        PolicyView view = get(p.getId());
        audit.record(AUDIT, p.getId(), "CREATE", null, auditView(view));
        return view;
    }

    /** {@code clientId} moves the policy to another client (it was mapped wrongly); null keeps it. */
    @Transactional
    public PolicyView update(long id, Long clientId, Long rmId, PolicyInput in, String ifMatch) {
        InsurancePolicy p = find(id);
        EditVersion.check(p.getUpdatedAt(), ifMatch);
        if (p.getStatus() == InsurancePolicy.Status.CANCELLED) throw new RuleViolationException("This policy is cancelled");
        months.beforeEntryChange(p.getMonth());
        months.beforeEntryChange(month(in.month()));
        PolicyView before = get(id);
        if (clientId != null && !clientId.equals(p.getClientId())) {
            clients.requireExists(clientId);
            p.moveTo(clientId);
        }
        p.fill(details(p.getClientId(), rmId, in, p.getSource() == InsurancePolicy.Source.SCREEN));
        policies.saveAndFlush(p);
        PolicyView after = get(id);
        audit.record(AUDIT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        InsurancePolicy p = find(id);
        if (p.getStatus() == InsurancePolicy.Status.CANCELLED) return;
        months.beforeEntryChange(p.getMonth());
        PolicyView before = get(id);
        p.cancel();
        policies.saveAndFlush(p);
        audit.record(AUDIT, id, "CANCEL", auditView(before), Map.of("reason", Objects.requireNonNullElse(Text.clean(reason), "")));
    }

    /** Checks what was typed and takes the client's RM on the month's last day; a client with no RM gets {@code rmId} from the month. */
    /** {@code screen}: typed on the screen, so held to its category's required fields (R-96); uploaded policies are not. */
    private InsurancePolicy.Details details(long clientId, Long rmId, PolicyInput in, boolean screen) {
        LocalDate month = month(in.month());
        Long rm = clients.rmsOn(List.of(clientId), month.plusMonths(1).minusDays(1)).get(clientId);
        if (rm == null) {
            if (rmId == null) throw new RuleViolationException("This client has no RM in " + YearMonth.from(month) + ": choose the RM (it is saved on the client)");
            employees.requireEmployedOn(rmId, month);
            clients.setRmFrom(clientId, rmId, month, "Insurance policy");
            rm = rmId;
        }
        Long posp = posp(in.pospId(), in.newPospName(), null);
        return checked(screen, new InsurancePolicy.Details(month, rm, posp, Text.clean(in.insurer()), in.category(), Text.clean(in.subType()),
                Text.clean(in.productName()), Text.clean(in.policyNo()), in.businessType(), Text.clean(in.ppt()), in.policyTerm(), in.sumAssured(),
                in.odPremium(), in.tpPremium(), in.totalPremium(), in.netPremium(), in.expectedPercent(), in.expectedComm(), in.actualPercent(),
                in.actualComm(), in.pospShare(), in.pospComm(), Text.clean(in.remarks())));
    }

    private static InsurancePolicy.Details checked(boolean screen, InsurancePolicy.Details d) {
        check(d);
        if (screen) InsuranceCategoryRules.checkScreen(d);
        return d;
    }

    /** Each category's sub types, business types and required fields, for the screen (R-96). */
    public List<InsuranceCategoryRules.Rule> categories() {
        return java.util.Arrays.stream(Category.values()).map(InsuranceCategoryRules.RULES::get).toList();
    }

    static InsurancePolicy.Details check(InsurancePolicy.Details d) {
        if (d.insurer() == null) throw new ValidationException("Enter the insurance company");
        if (d.category() == null) throw new ValidationException("Choose Life, Health, Motor or General");
        if (d.productName() == null) throw new ValidationException("Enter the product name");
        if (d.policyNo() == null) throw new ValidationException("Enter the policy number");
        if (d.businessType() == null) throw new ValidationException("Choose the business type (New, Renewal, Port …)");
        if (d.totalPremium() == null) throw new ValidationException("Enter the total premium");
        if (d.netPremium() == null) throw new ValidationException("Enter the net premium");
        if (d.expectedPercent() == null && d.expectedComm() == null) throw new ValidationException("Enter the expected commission % (or the amount)");
        for (BigDecimal pct : new BigDecimal[]{d.expectedPercent(), d.actualPercent(), d.pospShare()}) {
            if (pct != null && (pct.signum() < 0 || pct.compareTo(HUNDRED) > 0)) throw new ValidationException("Percentages must be between 0 and 100");
        }
        if (d.pospId() != null && d.pospShare() == null && d.pospComm() == null) {
            throw new ValidationException("Enter the % agreed with the POSP (or the POSP's commission)");
        }
        // A POSP's commission above the expected commission is allowed: the sheet has such policies (the expected amount
        // typed low), and the END pays the POSP what the sheet says; the screen warns.
        return d;
    }

    static LocalDate month(LocalDate month) {
        if (month == null) throw new ValidationException("Choose the business month");
        return month.withDayOfMonth(1);
    }

    /** The POSP partner: the one picked, or one with this name (added as a POSP partner when there is none). */
    Long posp(Long pospId, String newName, Map<String, Long> byName) {
        if (pospId != null) {
            PartnerView p = partners.get(pospId);
            if (p.type() != PartnerType.POSP) throw new RuleViolationException(p.name() + " is not a POSP partner");
            return pospId;
        }
        String name = Text.clean(newName);
        if (name == null || NO_POSP.contains(name.toUpperCase())) return null;
        Map<String, Long> known = byName != null ? byName : pospsByName();
        Long id = known.get(Text.key(name));
        if (id == null) {
            id = partners.create(new PartnerInput(PartnerType.POSP, name, null, null, null, null, null)).id();
            known.put(Text.key(name), id);
        }
        return id;
    }

    Map<String, Long> pospsByName() {
        Map<String, Long> m = new HashMap<>();
        partners.list().stream().filter(p -> p.type() == PartnerType.POSP).forEach(p -> m.put(Text.key(p.name()), p.id()));
        return m;
    }

    private InsurancePolicy find(long id) {
        return policies.findById(id).orElseThrow(() -> new NotFoundException("Insurance policy " + id + " not found"));
    }

    // ---- name matches (R-100) ---------------------------------------------------------------------------------------

    /** A policy whose client was found by its name only: the client suggested, waiting for a person to confirm it (R-100). */
    public record NameMatch(long policyId, String policyNo, String insurer, String nameInFile, long clientId, String clientName, String pan,
                            String rmName, BigDecimal expectedComm) {
    }

    /** The month's policies whose client was found by name only and is not confirmed yet. */
    @Transactional(readOnly = true)
    public List<NameMatch> nameMatches(YearMonth month) {
        List<InsurancePolicy> waiting = policies.findByMonthOrderByIdAsc(month.atDay(1)).stream()
                .filter(p -> p.getStatus() == InsurancePolicy.Status.ACTIVE && p.isMatchedByName()).toList();
        if (waiting.isEmpty()) return List.of();
        Map<Long, ClientService.ClientView> views = clients.views(waiting.stream().map(InsurancePolicy::getClientId).distinct().toList());
        return waiting.stream().map(p -> {
            ClientService.ClientView v = views.get(p.getClientId());
            return new NameMatch(p.getId(), p.getPolicyNo(), p.getInsurer(), v == null ? null : v.name(), p.getClientId(), v == null ? null : v.name(),
                    v == null ? null : v.pan(), v == null ? null : v.rmName(), p.getExpectedComm());
        }).toList();
    }

    record NameConfirmed(long policyId, String nameInFile, long clientIdBefore, long clientId) {
    }

    /**
     * Confirms which client a policy found by name is (R-100): the one suggested, or another picked instead. The name is
     * remembered for that client, so the next sheet's rows with it go there without asking. Audited.
     */
    @Transactional
    public List<NameMatch> confirmName(long policyId, long clientId) {
        InsurancePolicy p = policies.findById(policyId).orElseThrow(() -> new NotFoundException("Policy " + policyId + " not found"));
        if (!p.isMatchedByName()) throw new RuleViolationException("This policy's client was not found by name, or is confirmed already");
        months.beforeEntryChange(p.getMonth());
        clients.requireExists(clientId);
        long before = p.getClientId();
        String nameInFile = clients.names(List.of(before)).get(before);
        p.confirmClient(clientId);
        policies.save(p);
        aliases.confirm(ALIAS_SOURCE, EntityType.CLIENT, Text.nameKey(nameInFile), clientId);
        audit.record(AUDIT, p.getId(), "CONFIRM_NAME_MATCH", null, new NameConfirmed(p.getId(), nameInFile, before, clientId));
        return nameMatches(YearMonth.from(p.getMonth()));
    }

    // ---- processing --------------------------------------------------------------------------------------------------

    /** The month's active policies with commission, as processing needs them (R-95): no GST, net = expected commission. */
    @Transactional(readOnly = true)
    public List<InsuranceForLedger> forLedger(YearMonth month) {
        Map<Long, String> pospNames = new HashMap<>();
        return policies.findByMonthOrderByIdAsc(month.atDay(1)).stream()
                .filter(p -> p.getStatus() == InsurancePolicy.Status.ACTIVE && p.getExpectedComm().signum() != 0)
                .map(p -> new InsuranceForLedger(p.getId(), p.getClientId(), p.getPospId(),
                        p.getPospId() == null ? null : pospNames.computeIfAbsent(p.getPospId(), id -> partners.get(id).name()),
                        p.getExpectedComm(), p.getPospComm()))
                .toList();
    }

    public record InsuranceForLedger(long policyId, long clientId, Long pospId, String pospName, BigDecimal expectedComm, BigDecimal pospComm) {
    }

    /** What the END shows for each insurance line: insurer and product. */
    @Transactional(readOnly = true)
    public Map<Long, String> labels(Collection<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        policies.findAllById(ids).forEach(p -> result.put(p.getId(), "Insurance: " + p.getInsurer() + " · " + p.getProductName()));
        return result;
    }

    // ---- views -------------------------------------------------------------------------------------------------------

    private List<PolicyView> views(List<InsurancePolicy> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(InsurancePolicy::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        Map<Long, String> codes = clients.codes(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, String> pospNames = new HashMap<>();
        list.stream().map(InsurancePolicy::getPospId).filter(Objects::nonNull).distinct().forEach(id -> pospNames.put(id, partners.get(id).name()));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        return list.stream().map(p -> {
            Place place = placesByMonth.computeIfAbsent(p.getMonth(), m -> places(org.asOf(m))).get(p.getRmId());
            String pan = pans.get(p.getClientId());
            String code = codes.get(p.getClientId());
            BigDecimal diffPct = p.getActualPercent() == null || p.getExpectedPercent() == null ? null : p.getExpectedPercent().subtract(p.getActualPercent());
            BigDecimal diffComm = p.getActualComm() == null ? null : p.getActualComm().subtract(p.getExpectedComm());
            return new PolicyView(p.getId(), YearMonth.from(p.getMonth()), p.getClientId(), names.get(p.getClientId()),
                    pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code, p.getRmId(),
                    place == null ? employees.get(p.getRmId()).name() : place.rm(), place == null ? null : place.branch(),
                    place == null ? null : place.region(), place == null ? null : place.rd(), p.getPospId(),
                    p.getPospId() == null ? null : pospNames.get(p.getPospId()), p.getInsurer(), p.getCategory(), p.getSubType(), p.getProductName(),
                    p.getPolicyNo(), p.getBusinessType(), p.getPpt(), p.getPolicyTerm(), p.getSumAssured(), p.getOdPremium(), p.getTpPremium(),
                    p.getTotalPremium(), p.getNetPremium(), p.getExpectedPercent(), p.getExpectedComm(), p.isExpectedAsGiven(), p.getActualPercent(),
                    p.getActualComm(), diffPct, diffComm, p.getPospShare(), p.getPospPercent(), p.getPospComm(), p.isPospAsGiven(), p.getRemarks(),
                    p.getSource(), p.getUploadRow(), p.getStatus(), p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt());
        }).toList();
    }

    private static Map<Long, Place> places(OrgChart chart) {
        Map<Long, Place> result = new HashMap<>();
        chart.regions().forEach(r -> r.branches().forEach(b -> b.people().forEach(p ->
                result.put(p.id(), new Place(p.name(), b.name(), r.name(), r.rd() == null ? null : r.rd().name())))));
        chart.branchesWithoutRegion().forEach(b -> b.people().forEach(p -> result.put(p.id(), new Place(p.name(), b.name(), null, null))));
        return result;
    }

    private static Map<String, Object> auditView(PolicyView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("posp", v.pospName());
        m.put("insurer", v.insurer());
        m.put("product", v.productName());
        m.put("policyNo", v.policyNo());
        m.put("businessType", v.businessType());
        m.put("totalPremium", v.totalPremium());
        m.put("netPremium", v.netPremium());
        m.put("expectedPercent", v.expectedPercent());
        m.put("expectedComm", v.expectedComm());
        m.put("pospShare", v.pospShare());
        m.put("pospComm", v.pospComm());
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record PolicyView(Long id, YearMonth month, Long clientId, String clientName, String pan, String clientCode, Long rmId, String rmName,
                             String branchName, String region, String rd, Long pospId, String pospName, String insurer, Category category,
                             String subType, String productName, String policyNo, BusinessType businessType, String ppt, Integer policyTerm,
                             BigDecimal sumAssured, BigDecimal odPremium, BigDecimal tpPremium, BigDecimal totalPremium, BigDecimal netPremium,
                             BigDecimal expectedPercent, BigDecimal expectedComm, boolean expectedAsGiven, BigDecimal actualPercent,
                             BigDecimal actualComm, BigDecimal diffPercent, BigDecimal diffComm, BigDecimal pospShare, BigDecimal pospPercent,
                             BigDecimal pospComm, boolean pospAsGiven, String remarks, InsurancePolicy.Source source, Integer uploadRow,
                             InsurancePolicy.Status status, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }
}
