package money.epsilon.brokerage.sharing.load;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService.ClientKey;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.sharing.load.MasterShareReader.MasterRow;
import money.epsilon.brokerage.sharing.load.MasterShareReader.PartnerCell;

/**
 * Works out, without saving anything, what the master says each existing client's partners should be (one per role:
 * sub-broker, referral partner) and how that differs from today. Clients are matched by PAN + name (case and spaces
 * ignored); a client without PAN by name alone, only when that name is unique on both sides. A partner without a % gets 0.
 * A matched client whose row names no partner in a role has no partner in that role.
 */
final class SharePlan {

    enum Action { SET, END }

    record Desired(String partnerKey, PartnerType type, String partnerName, BigDecimal fraction) {
    }

    /** A change for one client in one role. For SET, {@code partnerId} is null when the partner will be created. */
    record Change(long clientId, Action action, PartnerType role, Desired desired, Long partnerId) {
    }

    record Unmatched(int row, String clientName, String maskedPan, String reason) {
    }

    record NewPartner(String name, PartnerType type, int clients) {
    }

    record Current(long partnerId, BigDecimal fraction) {
    }

    record Existing(long id, PartnerType type, String name) {
    }

    final List<Change> changes = new ArrayList<>();
    final List<Unmatched> unmatched = new ArrayList<>();
    final List<String> problems = new ArrayList<>();
    final Map<String, NewPartner> newPartners = new LinkedHashMap<>();
    int rows;
    int rowsWithPartner;
    int matchedClients;
    int unchanged;
    int zeroPct;
    int bothRoles;
    int matchedByPanOnly; // PAN matched, name spelt differently
    int conflicts;

    /**
     * @param hash       PAN → hash (as stored on clients)
     * @param partners   existing partners
     * @param current    client id → its current MF share per role
     * @param remembered partner spelling → partner id, from spellings confirmed before (null if none)
     */
    static SharePlan build(List<MasterRow> rows, List<ClientKey> clients, Function<String, String> hash, List<Existing> partners,
                           Map<Long, Map<PartnerType, Current>> current, Function<String, Long> remembered) {
        SharePlan plan = new SharePlan();
        plan.rows = rows.size();

        Map<String, List<ClientKey>> byPan = new HashMap<>();
        Map<String, List<ClientKey>> noPanByName = new HashMap<>();
        for (ClientKey c : clients) {
            if (c.panHash() == null) {
                noPanByName.computeIfAbsent(c.nameKey(), k -> new ArrayList<>()).add(c);
            } else {
                byPan.computeIfAbsent(c.panHash(), k -> new ArrayList<>()).add(c);
            }
        }
        Map<String, Long> noPanRowsPerName = rows.stream().filter(r -> r.pan().isEmpty())
                .collect(Collectors.groupingBy(r -> Text.key(r.clientName()), Collectors.counting()));
        Map<String, PartnerType> typeByPartner = inferTypes(rows);

        Map<Long, Map<PartnerType, Desired>> desired = new LinkedHashMap<>();
        Map<Long, Integer> desiredRow = new HashMap<>();
        Set<Long> conflicted = new HashSet<>();
        for (MasterRow r : rows) {
            boolean hasPartner = !r.partners().isEmpty();
            if (hasPartner) plan.rowsWithPartner++;

            List<ClientKey> found = match(r, hash, byPan, noPanByName, noPanRowsPerName, plan);
            if (found == null) continue;

            Map<PartnerType, Desired> wanted = new EnumMap<>(PartnerType.class);
            boolean rowOk = true;
            for (PartnerCell p : r.partners()) {
                if (p.pctProblem() != null) plan.problems.add("Row " + r.rowNumber() + ": " + p.pctProblem());
                PartnerType type = partnerType(p.type());
                if (type == null) type = typeByPartner.get(Text.key(p.partner()));
                if (type == null) {
                    plan.problems.add("Row " + r.rowNumber() + ": partner '" + p.partner() + "' has no TYPE (Sub broker / Referral Partner); row left unchanged");
                    rowOk = false;
                    break;
                }
                if (wanted.containsKey(type)) {
                    plan.problems.add("Row " + r.rowNumber() + ": two partners of the same type; row left unchanged");
                    rowOk = false;
                    break;
                }
                BigDecimal fraction = (p.pct() == null ? BigDecimal.ZERO : p.pct()).setScale(6, RoundingMode.HALF_UP);
                wanted.put(type, new Desired(Text.key(p.partner()), type, p.partner(), fraction));
            }
            if (!rowOk) continue;
            for (ClientKey c : found) {
                if (desiredRow.containsKey(c.id())) {
                    if (!sameDesire(desired.get(c.id()), wanted)) conflicted.add(c.id());
                    continue;
                }
                desired.put(c.id(), wanted);
                desiredRow.put(c.id(), r.rowNumber());
            }
        }
        for (Long id : conflicted) {
            plan.problems.add("Rows disagree on the partners / % for the client on row " + desiredRow.get(id) + "; left unchanged");
            desired.remove(id);
        }
        plan.conflicts = conflicted.size();
        plan.matchedClients = desired.size();

        Map<String, Existing> existing = partners.stream()
                .collect(Collectors.toMap(p -> p.type() + "|" + Text.key(p.name()), Function.identity(), (a, b) -> a));
        Map<Long, Existing> existingById = partners.stream().collect(Collectors.toMap(Existing::id, Function.identity()));
        Map<String, Integer> newPartnerClients = new LinkedHashMap<>();
        for (var e : desired.entrySet()) {
            long clientId = e.getKey();
            Map<PartnerType, Desired> wanted = e.getValue();
            Map<PartnerType, Current> now = current.getOrDefault(clientId, Map.of());
            if (wanted.size() > 1) plan.bothRoles++;
            int before = plan.changes.size();
            for (PartnerType role : PartnerType.values()) {
                Desired d = wanted.get(role);
                Current c = now.get(role);
                if (d == null) {
                    if (c != null) plan.changes.add(new Change(clientId, Action.END, role, null, null));
                    continue;
                }
                if (d.fraction().signum() == 0) plan.zeroPct++;
                // A remembered spelling wins over the exact name: a merged duplicate keeps its name but points elsewhere.
                Long aliased = remembered.apply(d.partnerName());
                Existing p = aliased == null ? null : existingById.get(aliased);
                if (p == null || p.type() != d.type()) p = existing.get(d.type() + "|" + d.partnerKey());
                if (p == null) {
                    String key = d.type() + "|" + d.partnerKey();
                    newPartnerClients.merge(key, 1, Integer::sum);
                    plan.newPartners.putIfAbsent(key, new NewPartner(d.partnerName(), d.type(), 0));
                    plan.changes.add(new Change(clientId, Action.SET, role, d, null));
                } else if (c == null || c.partnerId() != p.id() || c.fraction().compareTo(d.fraction()) != 0) {
                    plan.changes.add(new Change(clientId, Action.SET, role, d, p.id()));
                }
            }
            if (plan.changes.size() == before) plan.unchanged++;
        }
        newPartnerClients.forEach((k, n) -> plan.newPartners.computeIfPresent(k, (key, np) -> new NewPartner(np.name(), np.type(), n)));
        plan.unmatched.sort(Comparator.comparingInt(Unmatched::row));
        return plan;
    }

    /** The clients a row stands for, or null (recorded as unmatched when the row names a partner). */
    /** A row with a PAN matches the client(s) with that PAN, whatever the name is spelt; without a PAN, a unique name. */
    private static List<ClientKey> match(MasterRow r, Function<String, String> hash, Map<String, List<ClientKey>> byPan,
                                         Map<String, List<ClientKey>> noPanByName, Map<String, Long> noPanRowsPerName,
                                         SharePlan plan) {
        boolean report = !r.partners().isEmpty();
        String pan = normalisePan(r.pan());
        String name = Text.key(r.clientName());
        if (pan != null) {
            String h = hash.apply(pan);
            List<ClientKey> found = byPan.getOrDefault(h, List.of());
            if (found.isEmpty()) {
                if (report) plan.unmatched.add(new Unmatched(r.rowNumber(), r.clientName(), PanCipher.mask(pan), "No client with this PAN yet"));
                return null;
            }
            if (found.stream().noneMatch(c -> c.nameKey().equals(name))) plan.matchedByPanOnly++;
            return found;
        }
        List<ClientKey> same = noPanByName.getOrDefault(name, List.of());
        if (same.size() != 1 || noPanRowsPerName.getOrDefault(name, 0L) != 1) {
            if (report) plan.unmatched.add(new Unmatched(r.rowNumber(), r.clientName(), null,
                    same.isEmpty() ? "No PAN, and no client by this name without PAN" : "No PAN, and the name is not unique"));
            return null;
        }
        return same;
    }

    static PartnerType partnerType(String text) {
        String k = Text.key(text);
        if (k.contains("SUB")) return PartnerType.SUB_BROKER;
        if (k.contains("REFERRAL")) return PartnerType.REFERRAL;
        if (k.contains("POSP")) return PartnerType.POSP;
        return null;
    }

    /** For partners named with TYPE left blank: the type that partner has on its other rows, if only one. */
    private static Map<String, PartnerType> inferTypes(List<MasterRow> rows) {
        Map<String, Set<PartnerType>> seen = new HashMap<>();
        for (MasterRow r : rows) {
            for (PartnerCell p : r.partners()) {
                PartnerType t = partnerType(p.type());
                if (t != null) seen.computeIfAbsent(Text.key(p.partner()), k -> new HashSet<>()).add(t);
            }
        }
        Map<String, PartnerType> result = new HashMap<>();
        seen.forEach((k, types) -> {
            if (types.size() == 1) result.put(k, types.iterator().next());
        });
        return result;
    }

    private static boolean sameDesire(Map<PartnerType, Desired> a, Map<PartnerType, Desired> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (PartnerType t : a.keySet()) {
            Desired x = a.get(t), y = b.get(t);
            if (!x.partnerKey().equals(y.partnerKey()) || x.fraction().compareTo(y.fraction()) != 0) return false;
        }
        return true;
    }

    private static String normalisePan(String pan) {
        try {
            return PanCipher.normalise(pan);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    int changeCount(Action action) {
        return (int) changes.stream().filter(c -> c.action() == action).count();
    }
}
