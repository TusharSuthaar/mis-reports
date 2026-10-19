package money.epsilon.brokerage.sharing.load;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.platform.job.JobProgress;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.sharing.load.MasterClientPlan.RmMatch;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import money.epsilon.brokerage.mapping.service.AliasService;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.sharing.domain.Product;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;
import money.epsilon.brokerage.partner.domain.PartnerStatus;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerInput;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.storage.FileStore;
import money.epsilon.brokerage.sharing.load.MasterShareReader.Result;
import money.epsilon.brokerage.sharing.load.SharePlan.Action;
import money.epsilon.brokerage.sharing.load.SharePlan.Change;
import money.epsilon.brokerage.sharing.load.SharePlan.Existing;
import money.epsilon.brokerage.sharing.load.SharePlan.NewPartner;
import money.epsilon.brokerage.sharing.load.SharePlan.Unmatched;
import money.epsilon.brokerage.sharing.service.ShareService;
import money.epsilon.brokerage.sharing.service.ShareService.ShareView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Master MIS upload: a preview that saves nothing, then a load (as a job). The load adds the clients Master MIS has and
 * the app does not (by PAN; by name for a row without a PAN), sets each client's RM from the chosen date, creates missing
 * partners, sets each partner's % per product from the partner sheets (when the workbook has them), marks partners
 * "under process" as PENDING (and back to ACTIVE when no row says so any more), and sets / ends each client's partner link
 * from that date. Re-running with an updated master applies only the differences.
 */
@Service
public class ShareLoadService {

    static final String STORE_AREA = "share-loads";
    private static final int UNMATCHED_SAMPLE = 200;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FileStore files;
    private final ClientService clients;
    private final PartnerService partners;
    private final ShareService shares;
    private final PanCipher panCipher;
    private final AliasService aliases;
    private final EmployeeService employees;

    ShareLoadService(FileStore files, ClientService clients, PartnerService partners, ShareService shares, PanCipher panCipher,
                     AliasService aliases, EmployeeService employees) {
        this.aliases = aliases;
        this.employees = employees;
        this.files = files;
        this.clients = clients;
        this.partners = partners;
        this.shares = shares;
        this.panCipher = panCipher;
    }

    /**
     * Partner figures are for clients the app already has; partners of {@code newClients} are set right after they are
     * added. {@code rmChanges} lists up to {@value #UNMATCHED_SAMPLE} of the RM changes.
     */
    public record Preview(String token, String fileName, String error, LocalDate from, int rows, int rowsWithPartner, int matchedClients,
                          int toSet, int toEnd, int unchanged, int zeroPct, int bothRoles, int matchedByPanOnly, int conflicts, List<NewPartner> newPartners,
                          int unmatchedCount, List<Unmatched> unmatched, List<String> problems,
                          int newClients, int newClientsWithoutPan, int rmChangeCount, int rmUnchanged, int clientsWithoutRm,
                          List<RmChangeView> rmChanges, List<UnknownRm> unknownRms, PartnerRatesPreview partnerRates) {
    }

    /**
     * What the partner sheets change. {@code sheetPartners}: partners listed there; {@code percentChanges}: product %s that
     * are new or different; {@code followsPartner} / {@code clientExceptions}: client links that will use the partner's % /
     * their own %; {@code pending} / {@code approved}: partners becoming PENDING / ACTIVE again.
     */
    public record PartnerRatesPreview(int sheetPartners, int percentChanges, int followsPartner, int clientExceptions,
                                      List<String> pending, List<String> approved) {
    }

    public record RmChangeView(long clientId, String client, String pan, String fromRm, String toRm) {
    }

    public record UnknownRm(String spelling, String note, int clients) {
    }

    public record LoadResult(String fileName, int clientsCreated, int rmChanged, int sharesSet, int sharesEnded, int unchanged,
                             int partnersCreated, int zeroPct, int unmatched, int productPercentsSet, int clientExceptions) {
    }

    /** The workbook's partner sheets resolved against the app's partners. */
    private record Rates(Map<String, Map<Product, BigDecimal>> byKey, Map<String, PartnerSheetReader.PartnerRates> sheet,
                         Map<String, Long> partnerIdByKey, int percentChanges, List<String> problems) {
    }

    @Transactional(readOnly = true)
    public Preview preview(byte[] content, String fileName, LocalDate from) {
        if (from == null) throw new IllegalArgumentException("Choose the date the changes count from");
        String token = files.save(STORE_AREA, content, ".xlsx");
        Result read = MasterShareReader.read(content);
        if (read.error() != null) {
            return new Preview(token, fileName, read.error(), from, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, List.of(), List.of(),
                    0, 0, 0, 0, 0, List.of(), List.of(), null);
        }
        List<ClientService.ClientKey> keys = clients.keys();
        MasterClientPlan cp = clientPlan(read, clients.matchKeys(), from);
        Rates rates = rates(content, from);
        SharePlan plan = plan(read, rates);
        Map<Long, EmployeeView> people = employees.list().stream().collect(java.util.stream.Collectors.toMap(EmployeeView::id, e -> e));
        Map<Long, ClientService.ClientKey> keyById = keys.stream().collect(java.util.stream.Collectors.toMap(ClientService.ClientKey::id, k -> k));
        Map<Long, String> names = clients.names(cp.rmChanges.stream().limit(UNMATCHED_SAMPLE).map(MasterClientPlan.RmChange::clientId).toList());
        List<RmChangeView> changes = cp.rmChanges.stream().limit(UNMATCHED_SAMPLE)
                .map(c -> new RmChangeView(c.clientId(), names.get(c.clientId()), keyById.get(c.clientId()).panHash() == null ? null : "PAN on file",
                        c.fromRmId() == null ? null : people.get(c.fromRmId()).name(), people.get(c.toRmId()).name()))
                .toList();
        List<String> problems = new java.util.ArrayList<>(cp.problems);
        problems.addAll(rates.problems());
        problems.addAll(plan.problems);
        Map<Long, PartnerView> partnerById = partners.list().stream().collect(java.util.stream.Collectors.toMap(PartnerView::id, v -> v));
        PartnerRatesPreview ratesPreview = new PartnerRatesPreview(rates.sheet().size(), rates.percentChanges(), plan.followsPartner,
                plan.exceptions, statusChanges(plan, PartnerStatus.PENDING, partnerById), statusChanges(plan, PartnerStatus.ACTIVE, partnerById));
        return new Preview(token, fileName, null, from, plan.rows, plan.rowsWithPartner, plan.matchedClients, plan.changeCount(Action.SET),
                plan.changeCount(Action.END), plan.unchanged, plan.zeroPct, plan.bothRoles, plan.matchedByPanOnly, plan.conflicts, List.copyOf(plan.newPartners.values()),
                plan.unmatched.size(), plan.unmatched.stream().limit(UNMATCHED_SAMPLE).toList(), problems,
                cp.newClients.size(), (int) cp.newClients.stream().filter(n -> n.pan() == null).count(), cp.rmChanges.size(), cp.rmUnchanged,
                cp.withoutRm, changes, cp.unknownRms.entrySet().stream().map(e -> new UnknownRm(e.getKey(), cp.unknownRmNotes.get(e.getKey()), e.getValue())).toList(),
                ratesPreview);
    }

    private MasterClientPlan clientPlan(Result read, List<ClientService.ClientKey> keys, LocalDate from) {
        List<EmployeeView> people = employees.list();
        Map<String, List<EmployeeView>> byName = people.stream().collect(java.util.stream.Collectors.groupingBy(e -> Text.key(e.name())));
        Map<Long, EmployeeView> byId = people.stream().collect(java.util.stream.Collectors.toMap(EmployeeView::id, e -> e));
        // Branches the master names for each RM name: a book in none of them is that RM's general book (R-21).
        Map<String, java.util.Set<String>> masterBranches = new HashMap<>();
        read.rows().forEach(r -> masterBranches.computeIfAbsent(Text.key(r.rm()), k -> new java.util.HashSet<>()).add(Text.key(r.branch())));
        return MasterClientPlan.build(read.rows(), keys, panCipher::hash, clients.currentRmEntries(keys.stream().map(ClientService.ClientKey::id).toList()),
                from, (spelling, branch) -> {
                    var alias = aliases.resolve(ClientService.INVESTWELL, EntityType.RM, spelling);
                    if (alias.isPresent()) return new RmMatch(alias.get(), null);
                    List<EmployeeView> same = byName.getOrDefault(Text.key(spelling), List.of());
                    if (same.size() == 1) return new RmMatch(same.get(0).id(), null);
                    if (same.isEmpty()) return new RmMatch(null, "no RM with this name");
                    List<EmployeeView> inBranch = same.stream().filter(e -> e.branchName() != null && Text.key(e.branchName()).equals(Text.key(branch))).toList();
                    if (inBranch.size() == 1) return new RmMatch(inBranch.get(0).id(), null);
                    // No book in that branch: the one book in a branch the master never names for this RM takes it.
                    java.util.Set<String> named = masterBranches.getOrDefault(Text.key(spelling), java.util.Set.of());
                    List<EmployeeView> general = same.stream().filter(e -> e.branchName() == null || !named.contains(Text.key(e.branchName()))).toList();
                    if (inBranch.isEmpty() && general.size() == 1) return new RmMatch(general.get(0).id(), null);
                    return new RmMatch(null, "several RMs have this name; branch '" + branch + "' does not pick one");
                },
                id -> {
                    EmployeeView e = byId.get(id);
                    return e != null && (e.joinedOn() == null || !from.isBefore(e.joinedOn())) && (e.exitedOn() == null || !from.isAfter(e.exitedOn()));
                });
    }

    /** An RM spelling Master MIS uses that is not the RM's name: remembered, so it resolves on every later upload. */
    @Transactional
    public void rememberRmSpelling(String spelling, long rmId) {
        employees.get(rmId);
        aliases.remember(ClientService.INVESTWELL, EntityType.RM, spelling, rmId);
    }

    /** Master MIS names a partner differently from the app (e.g. "Partner X (Branch Y)"): remember which partner it means. */
    @Transactional
    public void rememberPartnerSpelling(String spelling, long partnerId) {
        partners.get(partnerId);
        aliases.remember(ClientService.INVESTWELL, EntityType.PARTNER, spelling, partnerId);
    }

    /** Used by {@link ShareLoadJob}, inside its transaction. */
    LoadResult apply(String token, String fileName, LocalDate from) {
        if (from == null) throw new IllegalArgumentException("A start date is required");
        JobProgress progress = JobProgress.current();
        progress.steps(4);
        progress.step("Reading the Master MIS", 0);
        byte[] content = files.read(STORE_AREA, token);
        Result read = MasterShareReader.read(content);
        if (read.error() != null) throw new IllegalArgumentException(read.error());
        String reason = "Master MIS " + fileName;
        MasterClientPlan cp = clientPlan(read, clients.matchKeys(), from);
        progress.step("Adding clients and setting their RM", cp.newClients.size() + cp.rmChanges.size());
        for (MasterClientPlan.NewClient n : cp.newClients) {
            long id = clients.createUncoded(n.name(), n.pan());
            clients.setRmFrom(id, n.rmId(), from, reason);
            progress.advance();
        }
        for (MasterClientPlan.RmChange c : cp.rmChanges) {
            clients.setRmFrom(c.clientId(), c.toRmId(), from, reason);
            progress.advance();
        }
        Rates rates = rates(content, from);
        SharePlan plan = plan(read, rates);

        // Partners: create the missing ones (from client rows and partner sheets), then their status and % per product.
        progress.step("Setting partners and their % per product", plan.newPartners.size() + rates.byKey().size());
        Map<String, Long> created = new HashMap<>();
        for (var e : plan.newPartners.entrySet()) {
            NewPartner np = e.getValue();
            created.put(e.getKey(), partners.create(new PartnerInput(np.type(), np.name(), null, null, null, null, null)).id());
            progress.advance();
        }
        for (var e : rates.sheet().entrySet()) {
            if (rates.partnerIdByKey().containsKey(e.getKey()) || created.containsKey(e.getKey())) continue;
            var r = e.getValue();
            created.put(e.getKey(), partners.create(new PartnerInput(r.type(), r.name(), null, null, null, null, null)).id());
        }
        Map<String, Long> idOf = new HashMap<>(rates.partnerIdByKey());
        idOf.putAll(created);
        for (String key : plan.pendingKeys) {
            Long id = idOf.get(key);
            if (id != null && partners.get(id).status() != PartnerStatus.PENDING) {
                partners.update(id, new PartnerInput(null, null, null, null, null, null, PartnerStatus.PENDING));
            }
        }
        for (String key : plan.approvedKeys) {
            Long id = idOf.get(key);
            if (id != null && !plan.pendingKeys.contains(key) && partners.get(id).status() == PartnerStatus.PENDING) {
                partners.update(id, new PartnerInput(null, null, null, null, null, null, PartnerStatus.ACTIVE));
            }
        }
        int productPercents = 0;
        for (var e : rates.byKey().entrySet()) {
            long id = idOf.get(e.getKey());
            for (var pr : e.getValue().entrySet()) {
                if (shares.setProductPercent(id, pr.getKey(), pr.getValue().multiply(HUNDRED), from, reason)) productPercents++;
            }
            progress.advance();
        }
        int set = 0, ended = 0;
        progress.step("Linking clients to their partners", plan.changes.size());
        for (Change c : plan.changes) {
            if (c.action() == Action.END) {
                shares.end(c.clientId(), c.role(), from.minusDays(1), reason);
                ended++;
            } else {
                Long partnerId = c.partnerId() != null ? c.partnerId() : created.get(c.desired().type() + "|" + c.desired().partnerKey());
                shares.apply(c.clientId(), partnerId, c.fraction() == null ? null : c.fraction().multiply(HUNDRED), from, reason);
                set++;
            }
            progress.advance();
        }
        return new LoadResult(fileName, cp.newClients.size(), cp.rmChanges.size(), set, ended, plan.unchanged, created.size(), plan.zeroPct,
                plan.unmatched.size(), productPercents, plan.exceptions);
    }

    /** The token must be a file this service stored. */
    void checkToken(String token) {
        files.checkToken(STORE_AREA, token);
    }

    private SharePlan plan(Result read, Rates rates) {
        List<ClientService.ClientKey> keys = clients.keys();
        List<Existing> existing = partners.list().stream()
                .map(p -> new Existing(p.id(), p.type(), p.name(), p.status() == PartnerStatus.PENDING)).toList();
        Map<Long, Map<PartnerType, SharePlan.Current>> current = new HashMap<>();
        shares.currentLinks(keys.stream().map(ClientService.ClientKey::id).toList()).forEach((clientId, list) -> {
            Map<PartnerType, SharePlan.Current> byRole = new java.util.EnumMap<>(PartnerType.class);
            for (ShareView v : list) {
                byRole.put(v.partnerRole(), new SharePlan.Current(v.partnerId(), fraction(v.clientPercent())));
            }
            current.put(clientId, byRole);
        });
        Map<Long, Map<Product, BigDecimal>> appRates = new HashMap<>();
        shares.currentProductPercents().forEach((id, byProduct) -> {
            Map<Product, BigDecimal> f = new java.util.EnumMap<>(Product.class);
            byProduct.forEach((product, pct) -> f.put(product, fraction(pct)));
            appRates.put(id, f);
        });
        return SharePlan.build(read.rows(), clients.matchKeys(), panCipher::hash, existing, current,
                name -> aliases.resolve(ClientService.INVESTWELL, EntityType.PARTNER, name).orElse(null), rates.byKey(), appRates);
    }

    /** Partner sheets: each partner (type|name key) with its %s, the app partner it is (if any), and how many %s change. */
    private Rates rates(byte[] content, LocalDate from) {
        PartnerSheetReader.Result sheets = PartnerSheetReader.read(content);
        List<String> problems = new java.util.ArrayList<>(sheets.problems());
        Map<String, PartnerSheetReader.PartnerRates> byKey = new java.util.LinkedHashMap<>();
        for (var r : sheets.partners()) {
            String key = r.type() + "|" + Text.key(r.name());
            if (byKey.putIfAbsent(key, r) != null) problems.add(r.sheet() + " row " + r.row() + ": '" + r.name() + "' is listed twice; the first row counts");
        }
        Map<String, Long> ids = new HashMap<>();
        Map<String, PartnerView> byTypeName = new HashMap<>();
        Map<Long, PartnerView> byId = new HashMap<>();
        for (PartnerView p : partners.list()) {
            byTypeName.putIfAbsent(p.type() + "|" + Text.key(p.name()), p);
            byId.put(p.id(), p);
        }
        Map<Long, Map<Product, BigDecimal>> now = shares.currentProductPercents();
        int changes = 0;
        Map<String, Map<Product, BigDecimal>> rates = new java.util.LinkedHashMap<>();
        for (var e : byKey.entrySet()) {
            var r = e.getValue();
            Long aliased = aliases.resolve(ClientService.INVESTWELL, EntityType.PARTNER, r.name()).orElse(null);
            PartnerView p = aliased == null ? null : byId.get(aliased);
            if (p == null || p.type() != r.type()) p = byTypeName.get(e.getKey());
            if (p != null) ids.put(e.getKey(), p.id());
            Map<Product, BigDecimal> current = p == null ? Map.of() : now.getOrDefault(p.id(), Map.of());
            for (var pr : r.fractions().entrySet()) {
                BigDecimal pct = current.get(pr.getKey());
                if (pct == null || fraction(pct).compareTo(pr.getValue()) != 0) changes++;
            }
            rates.put(e.getKey(), r.fractions());
        }
        return new Rates(rates, byKey, ids, changes, problems);
    }

    /** Names of partners whose status the load moves to {@code to}. */
    private static List<String> statusChanges(SharePlan plan, PartnerStatus to, Map<Long, PartnerView> partnerById) {
        Map<String, PartnerView> byKey = new HashMap<>();
        partnerById.values().forEach(p -> byKey.putIfAbsent(p.type() + "|" + Text.key(p.name()), p));
        List<String> names = new java.util.ArrayList<>();
        if (to == PartnerStatus.PENDING) {
            for (String key : plan.pendingKeys) {
                PartnerView p = byKey.get(key);
                if (p == null || p.status() != PartnerStatus.PENDING) names.add(p == null ? key.substring(key.indexOf('|') + 1) : p.name());
            }
        } else {
            for (String key : plan.approvedKeys) {
                PartnerView p = byKey.get(key);
                if (p != null && !plan.pendingKeys.contains(key) && p.status() == PartnerStatus.PENDING) names.add(p.name());
            }
        }
        java.util.Collections.sort(names);
        return names;
    }

    private static BigDecimal fraction(BigDecimal percent) {
        return percent == null ? null : percent.divide(HUNDRED, 6, java.math.RoundingMode.HALF_UP);
    }
}
