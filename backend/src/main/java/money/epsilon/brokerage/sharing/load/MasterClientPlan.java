package money.epsilon.brokerage.sharing.load;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import money.epsilon.brokerage.client.service.ClientService.ClientKey;
import money.epsilon.brokerage.client.service.ClientService.CurrentRm;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.sharing.load.MasterShareReader.MasterRow;

/**
 * Works out, without saving anything, which clients Master MIS adds and whose RM it changes. A row is keyed by its PAN;
 * a row without a PAN by its client name (matching an existing client that has no PAN either). Several rows of one key
 * must agree on the RM. New clients get the RM from the load date; existing clients whose current RM differs get the
 * new one from that date.
 */
final class MasterClientPlan {

    /** A client Master MIS has and the app does not. {@code pan} is normalised, or null for a name-only row. */
    record NewClient(int row, String name, String pan, long rmId) {
    }

    /** {@code fromRmId} null = the client has no RM yet. */
    record RmChange(long clientId, Long fromRmId, long toRmId) {
    }

    /** Resolves an RM spelling: the employee id, or null when unknown / ambiguous ({@code note} says which). */
    record RmMatch(Long id, String note) {
    }

    final List<NewClient> newClients = new ArrayList<>();
    final List<RmChange> rmChanges = new ArrayList<>();
    /** RM spelling (as cleaned) → rows; {@code unknownRmNotes} says why each is not resolved. */
    final Map<String, Integer> unknownRms = new TreeMap<>();
    final Map<String, String> unknownRmNotes = new TreeMap<>();
    final List<String> problems = new ArrayList<>();
    int keys;
    int rmUnchanged;
    int withoutRm;

    private MasterClientPlan() {
    }

    /**
     * @param current   each client's open RM entry
     * @param from      the date the changes count from
     * @param rmOf      resolves an RM spelling with the row's branch (alias, else exact employee name; the branch picks
     *                  between RMs of the same name)
     * @param employed  whether an RM is employed on {@code from}
     */
    static MasterClientPlan build(List<MasterRow> rows, List<ClientKey> clients, Function<String, String> hash, Map<Long, CurrentRm> current,
                                  LocalDate from, java.util.function.BiFunction<String, String, RmMatch> rmOf,
                                  java.util.function.Predicate<Long> employed) {
        MasterClientPlan plan = new MasterClientPlan();
        Map<String, List<Long>> byPan = new LinkedHashMap<>();
        Map<String, List<Long>> noPanByName = new LinkedHashMap<>();
        for (ClientKey c : clients) {
            if (c.panHash() != null) byPan.computeIfAbsent(c.panHash(), k -> new ArrayList<>()).add(c.id());
            else noPanByName.computeIfAbsent(nameKey(c.nameKey()), k -> new ArrayList<>()).add(c.id());
        }

        Map<String, List<MasterRow>> byKey = new LinkedHashMap<>();
        Map<String, String> panOfKey = new LinkedHashMap<>();
        for (MasterRow r : rows) {
            String pan = null;
            if (!r.pan().isBlank()) {
                try {
                    pan = PanCipher.normalise(r.pan());
                } catch (IllegalArgumentException e) {
                    plan.problems.add("Row " + r.rowNumber() + ": PAN '" + PanCipher.mask(r.pan().trim().toUpperCase()) + "' is not valid; matched by name instead");
                }
            }
            String key = pan != null ? "P:" + hash.apply(pan) : "N:" + nameKey(Text.key(r.clientName()));
            if (key.equals("N:")) continue;
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            if (pan != null) panOfKey.put(key, pan);
        }
        plan.keys = byKey.size();

        for (var e : byKey.entrySet()) {
            List<MasterRow> keyRows = e.getValue();
            MasterRow first = keyRows.get(0);
            List<String> spellings = keyRows.stream().map(MasterRow::rm).filter(s -> !s.isBlank()).map(Text::key).distinct().toList();
            if (spellings.isEmpty()) {
                plan.withoutRm++;
                continue;
            }
            if (spellings.size() > 1) {
                plan.problems.add("Row " + first.rowNumber() + ": " + first.clientName() + " is on " + keyRows.size() + " rows with different RMs ("
                        + String.join(" / ", spellings) + "); RM left unchanged");
                continue;
            }
            RmMatch rm = rmOf.apply(spellings.get(0), first.branch());
            if (rm.id() == null) {
                plan.unknownRms.merge(spellings.get(0), 1, Integer::sum);
                plan.unknownRmNotes.putIfAbsent(spellings.get(0), rm.note());
                continue;
            }
            if (!employed.test(rm.id())) {
                plan.problems.add("Row " + first.rowNumber() + ": RM " + spellings.get(0) + " is not employed on " + from + "; RM left unchanged");
                continue;
            }
            String key = e.getKey();
            List<Long> existing = key.startsWith("P:") ? byPan.getOrDefault(key.substring(2), List.of()) : noPanByName.getOrDefault(key.substring(2), List.of());
            if (existing.isEmpty()) {
                plan.newClients.add(new NewClient(first.rowNumber(), first.clientName(), panOfKey.get(key), rm.id()));
                continue;
            }
            if (key.startsWith("N:") && existing.size() > 1) {
                plan.problems.add("Row " + first.rowNumber() + ": " + existing.size() + " clients without a PAN are named " + first.clientName() + "; RM left unchanged");
                continue;
            }
            for (Long clientId : existing) {
                CurrentRm now = current.get(clientId);
                if (now != null && now.rmId() == rm.id()) {
                    plan.rmUnchanged++;
                } else if (now != null && now.since().isAfter(from)) {
                    plan.problems.add("Row " + first.rowNumber() + ": " + first.clientName() + "'s RM changed on " + now.since() + ", after " + from
                            + "; RM left unchanged");
                } else {
                    plan.rmChanges.add(new RmChange(clientId, now == null ? null : now.rmId(), rm.id()));
                }
            }
        }
        return plan;
    }

    /** Name match key: upper case, single spaces, dots ignored ("A. K. SHAH" = "A K SHAH"). */
    static String nameKey(String key) {
        return Text.key(key == null ? "" : key.replace('.', ' '));
    }
}
