package money.epsilon.brokerage.ingestion.clientload;

import money.epsilon.brokerage.platform.text.Text;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.ingestion.NameMatcher;
import money.epsilon.brokerage.ingestion.NameMatcher.Match;
import money.epsilon.brokerage.ingestion.NameMatcher.Status;
import money.epsilon.brokerage.ingestion.parser.InvestwellIncomeParser;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Problem;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Totals;
import money.epsilon.brokerage.ingestion.parser.StagedIncomeRow;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import money.epsilon.brokerage.mapping.service.AliasService;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.platform.storage.FileStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * First load of clients from an Investwell income file: a preview that saves nothing, then a commit (as a job)
 * that creates the clients, their starting RM, the sub-brokers and the remembered spellings.
 */
@Service
public class ClientLoadService {

    static final String STORE_AREA = "client-loads";
    private static final int WARNING_SAMPLE = 20;

    private final InvestwellIncomeParser parser;
    private final FileStore files;
    private final ClientService clients;
    private final EmployeeService employees;
    private final PartnerService partners;
    private final AliasService aliases;

    ClientLoadService(InvestwellIncomeParser parser, FileStore files, ClientService clients, EmployeeService employees,
                      PartnerService partners, AliasService aliases) {
        this.parser = parser;
        this.files = files;
        this.clients = clients;
        this.employees = employees;
        this.partners = partners;
        this.aliases = aliases;
    }

    public record RmLine(String spelling, int clients, BigDecimal net, Status status, Long rmId, String rmLabel,
                         List<Choice> candidates) {
    }

    public record PartnerLine(String spelling, int clients, BigDecimal net, Status status, Long partnerId, String partnerName) {
    }

    public record Choice(Long id, String label) {
    }

    public record Preview(String token, String fileName, int rows, Totals totals, List<Problem> errors, int warningCount,
                          List<Problem> warnings, int newClients, int existingClients, int withoutPan,
                          List<RmLine> rms, List<PartnerLine> subBrokers, List<Choice> allRms) {
    }

    /** Stores the file and reports what a load would do. Saves nothing else. */
    @Transactional(readOnly = true)
    public Preview preview(byte[] content, String fileName) {
        String token = files.save(STORE_AREA, content, ".xlsx");
        ParsedIncomeFile parsed = parser.parse(content);
        List<StagedIncomeRow> rows = parsed.rows();
        List<EmployeeView> allRms = employees.list();
        NameMatcher matcher = new NameMatcher(parser.source(), aliases, allRms, partners.list());
        Map<Long, String> rmLabels = allRms.stream().collect(Collectors.toMap(EmployeeView::id, ClientLoadService::label));

        List<RmLine> rmLines = group(rows, StagedIncomeRow::rawRm).entrySet().stream().map(e -> {
            Match m = matcher.rm(e.getKey());
            return new RmLine(e.getKey(), e.getValue().size(), net(e.getValue()), m.status(), m.id(),
                    m.id() == null ? null : rmLabels.get(m.id()),
                    m.candidates().stream().map(c -> new Choice(c.id(), label(c))).toList());
        }).sorted(Comparator.comparing(RmLine::net).reversed()).toList();

        List<PartnerLine> partnerLines = group(rows, StagedIncomeRow::rawPartner).entrySet().stream()
                .filter(e -> !Text.key(e.getKey()).isEmpty())
                .map(e -> {
                    Match m = matcher.subBroker(e.getKey());
                    String name = m.id() == null ? null : partners.get(m.id()).name();
                    return new PartnerLine(e.getKey(), e.getValue().size(), net(e.getValue()), m.status(), m.id(), name);
                }).sorted(Comparator.comparing(PartnerLine::net).reversed()).toList();

        int existing = clients.idsByExternalCode(parser.source(), rows.stream().map(StagedIncomeRow::clientCode).toList()).size();
        int withoutPan = (int) rows.stream().filter(r -> r.pan().isBlank()).count();
        List<Choice> choices = allRms.stream().sorted(Comparator.comparing(e -> e.name().toLowerCase()))
                .map(e -> new Choice(e.id(), label(e))).toList();
        return new Preview(token, fileName, rows.size(), parsed.rowTotals(), parsed.errors(), parsed.warnings().size(),
                parsed.warnings().stream().limit(WARNING_SAMPLE).toList(), rows.size() - existing, existing, withoutPan,
                rmLines, partnerLines, choices);
    }

    static String label(EmployeeView e) {
        return e.branchName() == null ? e.name() : e.name() + " — " + e.branchName();
    }

    /** The token must be a file this service stored. */
    void checkToken(String token) {
        files.checkToken(STORE_AREA, token);
    }

    private static Map<String, List<StagedIncomeRow>> group(List<StagedIncomeRow> rows, Function<StagedIncomeRow, String> key) {
        Map<String, List<StagedIncomeRow>> groups = new LinkedHashMap<>();
        for (StagedIncomeRow r : rows) groups.computeIfAbsent(key.apply(r), k -> new ArrayList<>()).add(r);
        return groups;
    }

    private static BigDecimal net(List<StagedIncomeRow> rows) {
        return rows.stream().map(StagedIncomeRow::net).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** What the user decided on the preview screen. */
    public record Decisions(LocalDate from, Map<String, Long> rmChoices, Map<String, String> partnerNames) {
    }

    /** Summary of a completed load. */
    public record LoadResult(String fileName, int clientsCreated, int clientsAlreadyThere, int rmAssigned, int needsRm,
                             int partnersCreated, int spellingsRemembered, int familyHeadsLinked) {
    }

    /** Used by {@link ClientLoadJob}: reads the stored file again and applies it with the decisions. */
    LoadResult apply(String token, String fileName, Decisions d) {
        if (d.from() == null) throw new IllegalArgumentException("A start date is required");
        ParsedIncomeFile parsed = parser.parse(files.read(STORE_AREA, token));
        if (!parsed.ok()) {
            throw new IllegalArgumentException("The file has problems and cannot be loaded: " + parsed.errors().get(0).message());
        }
        String source = parser.source();
        List<EmployeeView> allRms = employees.list();
        NameMatcher matcher = new NameMatcher(source, aliases, allRms, partners.list());
        int remembered = 0;

        // RMs: the user's choice, else alias / exact name. User choices are remembered for next month.
        Map<String, Long> rmFor = new LinkedHashMap<>();
        for (String spelling : group(parsed.rows(), StagedIncomeRow::rawRm).keySet()) {
            Long chosen = d.rmChoices() == null ? null : d.rmChoices().get(spelling);
            if (chosen != null) {
                rmFor.put(spelling, chosen);
                if (matcher.rm(spelling).status() != Status.MATCHED && !Text.key(spelling).isEmpty()
                        && aliases.remember(source, EntityType.RM, spelling, chosen)) {
                    remembered++;
                }
            } else {
                Match m = matcher.rm(spelling);
                if (m.id() != null) rmFor.put(spelling, m.id());
            }
        }

        // Sub-brokers: become partners (merged when the user named an existing / other partner).
        int partnersCreated = 0;
        for (String spelling : group(parsed.rows(), StagedIncomeRow::rawPartner).keySet()) {
            if (Text.key(spelling).isEmpty()) continue;
            String wanted = d.partnerNames() == null ? null : d.partnerNames().get(spelling);
            Match m = matcher.subBroker(spelling);
            if ((wanted == null || wanted.isBlank()) && m.id() != null) continue;
            String name = wanted == null || wanted.isBlank() ? spelling.trim() : wanted.trim();
            var existing = matcher.partnerNamed(name);
            long partnerId;
            if (existing.isPresent()) {
                partnerId = existing.get().id();
            } else {
                partnerId = partners.create(NameMatcher.newSubBroker(name)).id();
                partnersCreated++;
                matcher = new NameMatcher(source, aliases, allRms, partners.list());
            }
            if (!Text.key(spelling).equals(Text.key(name))
                    && aliases.remember(source, EntityType.PARTNER, spelling, partnerId)) {
                remembered++;
            }
        }

        // Clients and their starting RM.
        Map<String, Long> existingIds = clients.idsByExternalCode(source, parsed.rows().stream().map(StagedIncomeRow::clientCode).toList());
        Map<Long, Long> currentRm = clients.currentRms(existingIds.values());
        int created = 0, assigned = 0, needsRm = 0;
        String reason = "Starting RM from " + fileName;
        for (StagedIncomeRow r : parsed.rows()) {
            Long clientId = existingIds.get(r.clientCode());
            if (clientId == null) {
                clientId = clients.create(source, r.clientCode(), r.clientCode2(), r.clientName(), r.pan(), r.familyHeadCode());
                created++;
            }
            if (currentRm.containsKey(clientId)) continue;
            Long rmId = rmFor.get(r.rawRm());
            if (rmId == null) {
                needsRm++;
            } else {
                clients.changeRm(clientId, rmId, d.from(), reason);
                assigned++;
            }
        }
        int heads = clients.resolveFamilyHeads(source);
        return new LoadResult(fileName, created, parsed.rows().size() - created, assigned, needsRm, partnersCreated, remembered, heads);
    }
}
