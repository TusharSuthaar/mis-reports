package money.epsilon.brokerage.sharing.load;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.sharing.load.MasterClientPlan.RmMatch;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import money.epsilon.brokerage.mapping.service.AliasService;
import money.epsilon.brokerage.partner.domain.PartnerType;
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
 * partners and sets / ends each client's partner share from that date. Re-running with an updated master applies only
 * the differences.
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
                          List<RmChangeView> rmChanges, List<UnknownRm> unknownRms) {
    }

    public record RmChangeView(long clientId, String client, String pan, String fromRm, String toRm) {
    }

    public record UnknownRm(String spelling, String note, int clients) {
    }

    public record LoadResult(String fileName, int clientsCreated, int rmChanged, int sharesSet, int sharesEnded, int unchanged,
                             int partnersCreated, int zeroPct, int unmatched) {
    }

    @Transactional(readOnly = true)
    public Preview preview(byte[] content, String fileName, LocalDate from) {
        if (from == null) throw new IllegalArgumentException("Choose the date the changes count from");
        String token = files.save(STORE_AREA, content, ".xlsx");
        Result read = MasterShareReader.read(content);
        if (read.error() != null) {
            return new Preview(token, fileName, read.error(), from, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, List.of(), List.of(),
                    0, 0, 0, 0, 0, List.of(), List.of());
        }
        List<ClientService.ClientKey> keys = clients.keys();
        MasterClientPlan cp = clientPlan(read, keys, from);
        SharePlan plan = plan(read);
        Map<Long, EmployeeView> people = employees.list().stream().collect(java.util.stream.Collectors.toMap(EmployeeView::id, e -> e));
        Map<Long, ClientService.ClientKey> keyById = keys.stream().collect(java.util.stream.Collectors.toMap(ClientService.ClientKey::id, k -> k));
        Map<Long, String> names = clients.names(cp.rmChanges.stream().limit(UNMATCHED_SAMPLE).map(MasterClientPlan.RmChange::clientId).toList());
        List<RmChangeView> changes = cp.rmChanges.stream().limit(UNMATCHED_SAMPLE)
                .map(c -> new RmChangeView(c.clientId(), names.get(c.clientId()), keyById.get(c.clientId()).panHash() == null ? null : "PAN on file",
                        c.fromRmId() == null ? null : people.get(c.fromRmId()).name(), people.get(c.toRmId()).name()))
                .toList();
        List<String> problems = new java.util.ArrayList<>(cp.problems);
        problems.addAll(plan.problems);
        return new Preview(token, fileName, null, from, plan.rows, plan.rowsWithPartner, plan.matchedClients, plan.changeCount(Action.SET),
                plan.changeCount(Action.END), plan.unchanged, plan.zeroPct, plan.bothRoles, plan.matchedByPanOnly, plan.conflicts, List.copyOf(plan.newPartners.values()),
                plan.unmatched.size(), plan.unmatched.stream().limit(UNMATCHED_SAMPLE).toList(), problems,
                cp.newClients.size(), (int) cp.newClients.stream().filter(n -> n.pan() == null).count(), cp.rmChanges.size(), cp.rmUnchanged,
                cp.withoutRm, changes, cp.unknownRms.entrySet().stream().map(e -> new UnknownRm(e.getKey(), cp.unknownRmNotes.get(e.getKey()), e.getValue())).toList());
    }

    private MasterClientPlan clientPlan(Result read, List<ClientService.ClientKey> keys, LocalDate from) {
        List<EmployeeView> people = employees.list();
        Map<String, List<EmployeeView>> byName = people.stream().collect(java.util.stream.Collectors.groupingBy(e -> Text.key(e.name())));
        Map<Long, EmployeeView> byId = people.stream().collect(java.util.stream.Collectors.toMap(EmployeeView::id, e -> e));
        return MasterClientPlan.build(read.rows(), keys, panCipher::hash, clients.currentRmEntries(keys.stream().map(ClientService.ClientKey::id).toList()),
                from, (spelling, branch) -> {
                    var alias = aliases.resolve(ClientService.INVESTWELL, EntityType.RM, spelling);
                    if (alias.isPresent()) return new RmMatch(alias.get(), null);
                    List<EmployeeView> same = byName.getOrDefault(Text.key(spelling), List.of());
                    if (same.size() == 1) return new RmMatch(same.get(0).id(), null);
                    if (same.isEmpty()) return new RmMatch(null, "no RM with this name");
                    List<EmployeeView> inBranch = same.stream().filter(e -> e.branchName() != null && Text.key(e.branchName()).equals(Text.key(branch))).toList();
                    if (inBranch.size() == 1) return new RmMatch(inBranch.get(0).id(), null);
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

    /** Used by {@link ShareLoadJob}, inside its transaction. */
    LoadResult apply(String token, String fileName, LocalDate from) {
        if (from == null) throw new IllegalArgumentException("A start date is required");
        Result read = MasterShareReader.read(files.read(STORE_AREA, token));
        if (read.error() != null) throw new IllegalArgumentException(read.error());
        String reason = "Master MIS " + fileName;
        MasterClientPlan cp = clientPlan(read, clients.keys(), from);
        for (MasterClientPlan.NewClient n : cp.newClients) {
            long id = clients.createUncoded(n.name(), n.pan());
            clients.setRmFrom(id, n.rmId(), from, reason);
        }
        for (MasterClientPlan.RmChange c : cp.rmChanges) clients.setRmFrom(c.clientId(), c.toRmId(), from, reason);
        SharePlan plan = plan(read);

        Map<String, Long> created = new HashMap<>();
        for (var e : plan.newPartners.entrySet()) {
            NewPartner np = e.getValue();
            created.put(e.getKey(), partners.create(new PartnerInput(np.type(), np.name(), null, null, null, null, null)).id());
        }
        int set = 0, ended = 0;
        for (Change c : plan.changes) {
            if (c.action() == Action.END) {
                shares.end(c.clientId(), c.role(), from.minusDays(1), reason);
                ended++;
            } else {
                Long partnerId = c.partnerId() != null ? c.partnerId() : created.get(c.desired().type() + "|" + c.desired().partnerKey());
                shares.set(c.clientId(), partnerId, c.desired().fraction().multiply(HUNDRED), from, reason);
                set++;
            }
        }
        return new LoadResult(fileName, cp.newClients.size(), cp.rmChanges.size(), set, ended, plan.unchanged, created.size(), plan.zeroPct,
                plan.unmatched.size());
    }

    /** The token must be a file this service stored. */
    void checkToken(String token) {
        files.checkToken(STORE_AREA, token);
    }

    private SharePlan plan(Result read) {
        List<ClientService.ClientKey> keys = clients.keys();
        List<Existing> existing = partners.list().stream().map(p -> new Existing(p.id(), p.type(), p.name())).toList();
        Map<Long, Map<PartnerType, SharePlan.Current>> current = new HashMap<>();
        shares.current(keys.stream().map(ClientService.ClientKey::id).toList()).forEach((clientId, list) -> {
            Map<PartnerType, SharePlan.Current> byRole = new java.util.EnumMap<>(PartnerType.class);
            for (ShareView v : list) {
                byRole.put(v.partnerRole(), new SharePlan.Current(v.partnerId(), v.percent().divide(HUNDRED, 6, java.math.RoundingMode.HALF_UP)));
            }
            current.put(clientId, byRole);
        });
        return SharePlan.build(read.rows(), keys, panCipher::hash, existing, current,
                name -> aliases.resolve(ClientService.INVESTWELL, EntityType.PARTNER, name).orElse(null));
    }
}
