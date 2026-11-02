package money.epsilon.brokerage.insurance.service;

import static money.epsilon.brokerage.insurance.service.InsuranceService.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService.ClientKey;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.insurance.domain.InsuranceCategoryRules;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.BusinessType;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.Category;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy;
import money.epsilon.brokerage.insurance.repository.InsurancePolicyRepository;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import money.epsilon.brokerage.mapping.service.AliasService;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.excel.ExcelFile;
import money.epsilon.brokerage.platform.excel.ExcelRow;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The insurance team's monthly "Policy level data" sheet (R-94): rows mapped to clients by PAN (a row without a PAN by the one
 * client with exactly that name), saved as the month's uploaded policies; the rows that cannot be are listed. Split
 * out of {@link InsuranceService} (audit M-06): entry and lists stay there, the sheet lives here.
 */
@Service
public class InsuranceSheetUpload {

    private final InsurancePolicyRepository policies;
    private final InsuranceService insurance;
    private final ClientService clients;
    private final PanCipher panCipher;
    private final AuditService audit;
    private final ChangeWindow months;
    private final AliasService aliases;

    InsuranceSheetUpload(InsurancePolicyRepository policies, InsuranceService insurance, ClientService clients,
                         PanCipher panCipher, AuditService audit,
                         ChangeWindow months, AliasService aliases) {
        this.policies = policies;
        this.insurance = insurance;
        this.clients = clients;
        this.panCipher = panCipher;
        this.audit = audit;
        this.months = months;
        this.aliases = aliases;
    }


    /** A sheet row that was not saved, and why (shown by its row number in the file). */
    public record Unmapped(int row, String client, String pan, String reason) {
    }

    /** {@code byName}: saved policies whose client was found by name only, waiting for a confirmation (R-100). */
    public record UploadResult(YearMonth month, String fileName, int rows, int saved, int replaced, int newPosps, BigDecimal expectedTotal,
                               BigDecimal pospTotal, List<Unmapped> notSaved, int byName) {
    }

    /**
     * The month's "Policy level data" sheet (R-94). Each policy row is mapped to a client by PAN (current or former); rows
     * with no PAN, a PAN not in Clients, a client with no RM, or another business month are listed and not saved, to be
     * fixed and uploaded again or typed on the screen. The month's earlier uploaded policies are replaced; policies typed
     * on the screen stay, and a row with the same policy number and business type as one of them is skipped. Commissions
     * are kept as the sheet has them: as given where they differ from the calculation.
     */
    @Transactional
    public UploadResult upload(byte[] content, String fileName, LocalDate monthDay) {
        LocalDate month = month(monthDay);
        months.beforeEntryChange(month);
        LocalDate lastDay = month.plusMonths(1).minusDays(1);
        List<Unmapped> notSaved = new ArrayList<>();
        Map<String, Long> byPanHash = new HashMap<>();
        Map<String, List<Long>> byName = new HashMap<>();
        for (ClientKey k : clients.keys()) {
            if (k.panHash() != null) byPanHash.putIfAbsent(k.panHash(), k.id());
            byName.computeIfAbsent(Text.nameKey(k.nameKey()), x -> new ArrayList<>()).add(k.id());
        }
        clients.formerPans().forEach(byPanHash::putIfAbsent);
        Map<String, Long> posps = insurance.pospsByName();
        int pospsBefore = posps.size();

        List<InsurancePolicy> earlier = policies.findByMonthOrderByIdAsc(month).stream().filter(p -> p.getStatus() == InsurancePolicy.Status.ACTIVE).toList();
        Set<String> onScreen = earlier.stream().filter(p -> p.getSource() == InsurancePolicy.Source.SCREEN)
                .map(p -> Text.key(p.getPolicyNo()) + "|" + p.getBusinessType()).collect(Collectors.toSet());
        int replaced = 0;
        for (InsurancePolicy p : earlier) {
            if (p.getSource() == InsurancePolicy.Source.UPLOAD) {
                p.cancel();
                replaced++;
            }
        }
        policies.saveAll(earlier);

        record Row(ExcelRow r, long clientId, String clientName, String pan, boolean byName) {
        }
        List<Row> ok = new ArrayList<>();
        int rows = 0;
        try (ExcelFile file = ExcelFile.open(content)) {
            List<String> required = List.of(H_POLICY, H_INSURER, H_CAT, H_PAN, H_PRODUCT, H_TOTAL, H_TYPE);
            ExcelFile.Table t = file.table(SHEET, required, 10)
                    .or(() -> file.sheetNames().stream().map(n -> file.table(n, required, 10).orElse(null))
                            .filter(x -> x != null && x.missing().isEmpty()).findFirst())
                    .filter(x -> x.missing().isEmpty())
                    .orElseThrow(() -> new RuleViolationException("This is not the insurance policy sheet: no sheet has the columns " + String.join(", ", required)));
            for (ExcelRow r : t.rows()) {
                String policyNo = Text.clean(r.text(H_POLICY));
                String insurer = Text.clean(r.text(H_INSURER));
                if (policyNo == null || insurer == null) continue;   // totals and notes under the table
                rows++;
                String name = Text.clean(r.text(H_CLIENT));
                String pan = PanCipher.normaliseOrNull(r.text(H_PAN));
                YearMonth rowMonth = InsuranceSheetText.monthOf(r.value(H_MONTH));
                if (rowMonth != null && !rowMonth.equals(YearMonth.from(month))) {
                    notSaved.add(new Unmapped(r.rowNumber(), name, mask(pan), "Business month is " + rowMonth + ", not " + YearMonth.from(month)));
                    continue;
                }
                if (pan == null && Text.clean(r.text(H_PAN)) == null && name != null) {
                    // No PAN in the sheet (taken to be on a guardian's PAN for now): the client this name was confirmed as, else
                    // the one client with exactly this name, as a suggestion to confirm (R-100).
                    java.util.Optional<Long> confirmed = aliases.resolve(ALIAS_SOURCE, EntityType.CLIENT, Text.nameKey(name));
                    if (confirmed.isPresent()) {
                        ok.add(new Row(r, confirmed.get(), name, null, false));
                        continue;
                    }
                    List<Long> same = byName.getOrDefault(Text.nameKey(name), List.of());
                    if (same.size() == 1) {
                        ok.add(new Row(r, same.get(0), name, null, true));
                        continue;
                    }
                }
                if (pan == null) {
                    notSaved.add(new Unmapped(r.rowNumber(), name, null, Text.clean(r.text(H_PAN)) == null ? "No PAN: add it to the sheet, or type this policy on the screen and pick the client"
                            : "'" + Text.clean(r.text(H_PAN)) + "' is not a PAN"));
                    continue;
                }
                Long clientId = byPanHash.get(panCipher.hash(pan));
                if (clientId == null) {
                    notSaved.add(new Unmapped(r.rowNumber(), name, mask(pan), "PAN not in Clients: add the client on the Clients screen (with its RM), then upload again"));
                    continue;
                }
                ok.add(new Row(r, clientId, name, mask(pan), false));
            }
            Map<Long, Long> rmOf = clients.rmsOn(ok.stream().map(Row::clientId).distinct().toList(), lastDay);
            int saved = 0, byNameCount = 0;
            BigDecimal expectedTotal = BigDecimal.ZERO, pospTotal = BigDecimal.ZERO;
            for (Row x : ok) {
                ExcelRow r = x.r();
                Long rm = rmOf.get(x.clientId());
                if (rm == null) {
                    notSaved.add(new Unmapped(r.rowNumber(), x.clientName(), x.pan(), "The client has no RM in " + YearMonth.from(month) + ": give it one on the Clients screen"));
                    continue;
                }
                Category cat = InsuranceSheetText.category(r.text(H_CAT));
                BusinessType type = InsuranceSheetText.businessType(r.text(H_TYPE));
                if (cat == null || type == null) {
                    notSaved.add(new Unmapped(r.rowNumber(), x.clientName(), x.pan(), cat == null ? "CAT '" + r.text(H_CAT) + "' is not Life, Health, Motor, General or SME"
                            : "Business Type '" + r.text(H_TYPE) + "' is not New, Renewal, Port, Endorsement or FYCI"));
                    continue;
                }
                String policyNo = Text.clean(r.text(H_POLICY));
                if (onScreen.contains(Text.key(policyNo) + "|" + type)) {
                    notSaved.add(new Unmapped(r.rowNumber(), x.clientName(), x.pan(), "Already typed on the screen for this month (policy " + policyNo + ")"));
                    continue;
                }
                InsurancePolicy.Details d;
                try {
                    d = check(sheetDetails(r, month, rm, insurance.posp(null, r.text(H_POSP), posps), cat, type));
                } catch (IllegalArgumentException e) {
                    notSaved.add(new Unmapped(r.rowNumber(), x.clientName(), x.pan(), e.getMessage()));
                    continue;
                }
                InsurancePolicy p = new InsurancePolicy(x.clientId(), InsurancePolicy.Source.UPLOAD, r.rowNumber());
                p.fill(d);
                if (x.byName()) {
                    p.matchedByName();
                    byNameCount++;
                }
                policies.save(p);
                saved++;
                expectedTotal = expectedTotal.add(p.getExpectedComm());
                pospTotal = pospTotal.add(p.getPospComm());
            }
            policies.flush();
            notSaved.sort(java.util.Comparator.comparingInt(Unmapped::row));
            UploadResult result = new UploadResult(YearMonth.from(month), fileName, rows, saved, replaced, posps.size() - pospsBefore,
                    expectedTotal, pospTotal, notSaved, byNameCount);
            Map<String, Object> auditView = new LinkedHashMap<>();
            auditView.put("month", result.month());
            auditView.put("fileName", fileName);
            auditView.put("rows", rows);
            auditView.put("saved", saved);
            auditView.put("replaced", replaced);
            auditView.put("notSaved", notSaved.size());
            auditView.put("byName", byNameCount);
            auditView.put("expectedTotal", expectedTotal);
            auditView.put("pospTotal", pospTotal);
            audit.record(AUDIT, null, "UPLOAD", null, auditView);
            return result;
        }
    }

    /**
     * A sheet row as policy details. Percentages in the sheet are fractions (0.15 = 15%). The sheet's commission amounts are
     * taken as given only where they differ from the calculation by more than a rupee (they were typed, not worked out).
     */
    static InsurancePolicy.Details sheetDetails(ExcelRow r, LocalDate month, long rmId, Long pospId, Category cat, BusinessType type) {
        BigDecimal net = number(r, H_NET);
        BigDecimal total = number(r, List.of(H_TOTAL));
        BigDecimal expPct = InsuranceSheetText.percent(number(r, List.of(H_EXP_PCT)));
        BigDecimal actPct = InsuranceSheetText.percent(number(r, List.of(H_ACT_PCT)));
        BigDecimal agreed = pospId == null ? null : InsuranceSheetText.percent(number(r, List.of(H_AGREED)));
        BigDecimal exp = number(r, List.of(H_EXP));
        BigDecimal pospComm = pospId == null ? null : number(r, List.of(H_POSP_COMM));
        if (net == null) net = total;
        // CAT "SME": General with sub type SME; the sheet's own sub type (e.g. "Fire - Corporate") is kept in the remarks.
        String subType = Text.clean(r.text(H_SUB)), remarks = remarks(r);
        if (InsuranceSheetText.isSme(r.text(H_CAT))) {
            remarks = subType == null ? remarks : "SME: " + subType + (remarks == null ? "" : " · " + remarks);
            subType = InsuranceCategoryRules.SME;
        }
        InsurancePolicy.Details calc = new InsurancePolicy.Details(month, rmId, pospId, Text.clean(r.text(H_INSURER)), cat,
                subType, Text.clean(r.text(H_PRODUCT)), Text.clean(r.text(H_POLICY)), type, Text.clean(r.text(H_PPT)),
                integer(number(r, List.of(H_PT))), number(r, List.of(H_SA)), number(r, List.of(H_OD)), number(r, List.of(H_TP)), total, net,
                expPct, null, actPct, actPct == null ? number(r, List.of(H_ACT)) : null, agreed, null, remarks);
        InsurancePolicy.Figures f = InsurancePolicy.figures(calc);
        BigDecimal expGiven = exp != null && (expPct == null || differs(exp, f.expectedComm())) ? exp : null;
        BigDecimal pospGiven = pospComm != null && pospId != null && (agreed == null || differs(pospComm, f.pospComm())) ? pospComm : null;
        if (pospId != null && agreed == null && pospGiven == null) pospGiven = BigDecimal.ZERO;
        return new InsurancePolicy.Details(month, rmId, pospId, calc.insurer(), cat, calc.subType(), calc.productName(), calc.policyNo(), type,
                calc.ppt(), calc.policyTerm(), calc.sumAssured(), calc.odPremium(), calc.tpPremium(), total, net, expPct, expGiven, actPct,
                calc.actualComm(), agreed, pospGiven, calc.remarks());
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(BigDecimal.ONE) > 0;
    }

    private static String remarks(ExcelRow r) {
        for (String h : H_REMARKS) {
            String s = Text.clean(r.text(h));
            if (s != null) return s;
        }
        return null;
    }

    /** The first of {@code headers} with a number; text that is not a number (e.g. "-") counts as blank. */
    private static BigDecimal number(ExcelRow r, List<String> headers) {
        for (String h : headers) {
            Object v = r.value(h);
            if (v instanceof BigDecimal b) return b;
            if (v instanceof String s) {
                try {
                    return new BigDecimal(s.trim().replace(",", ""));
                } catch (NumberFormatException ignored) {
                    // not a number: treated as blank
                }
            }
        }
        return null;
    }

    private static Integer integer(BigDecimal v) {
        return v == null ? null : v.intValue();
    }

    private static String mask(String pan) {
        return pan == null ? null : PanCipher.mask(pan);
    }
}
