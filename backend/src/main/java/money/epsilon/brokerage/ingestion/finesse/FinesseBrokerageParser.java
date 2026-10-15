package money.epsilon.brokerage.ingestion.finesse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import money.epsilon.brokerage.ingestion.parser.IncomeFileParser;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Problem;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Totals;
import money.epsilon.brokerage.ingestion.parser.StagedIncomeRow;
import money.epsilon.brokerage.platform.excel.ExcelFile;
import money.epsilon.brokerage.platform.excel.ExcelRow;
import org.springframework.stereotype.Component;

/**
 * Finesse "MF Brokerage" export, sheet "Client Wise Summary": a title row, the header, one row per client per
 * brokerage period (a client can have several), and a final "Total" row. Brokerage Amount is net of GST; GST is 18% of
 * it. Rows are added up per client code. RM, SRM, Location/Branch, Percentage and Referred By are not used: Master MIS
 * decides the RM and partners.
 */
@Component
public class FinesseBrokerageParser implements IncomeFileParser {

    public static final String SOURCE = "FINESSE";

    static final String SHEET = "Client Wise Summary";
    static final String CODE = "CLIENT CODE";
    static final String NAME = "CLIENT NAME";
    static final String FAMILY_HEAD_CODE = "FAMILY HEAD CODE";
    static final String AMOUNT = "BROKERAGE AMOUNT";
    static final String END = "BROKERAGE END PERIOD";
    static final List<String> REQUIRED = List.of(CODE, NAME, AMOUNT, END);

    static final BigDecimal TOTAL_TOLERANCE = BigDecimal.ONE;
    private static final int HEADER_SEARCH_ROWS = 15;
    private static final int SCALE = 4;
    private static final BigDecimal GST_RATE = new BigDecimal("0.18");

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public ParsedIncomeFile parse(byte[] content) {
        List<Problem> errors = new ArrayList<>();
        List<Problem> warnings = new ArrayList<>();
        ExcelFile.Table read;
        try (ExcelFile file = ExcelFile.open(content)) {
            var table = file.table(SHEET, REQUIRED, HEADER_SEARCH_ROWS);
            if (table.isEmpty()) {
                return failed("Sheet '" + SHEET + "' not found. This does not look like the Finesse MF brokerage file (sheets: "
                        + String.join(", ", file.sheetNames()) + ")");
            }
            read = table.get();
        } catch (IllegalArgumentException e) {
            return failed(e.getMessage());
        }
        if (!read.missing().isEmpty()) return failed("Header row not found on '" + SHEET + "'. Missing columns: " + String.join(", ", read.missing()));

        Map<String, Client> byCode = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> byMonth = new TreeMap<>();
        BigDecimal fileTotal = null;
        BigDecimal rawSum = BigDecimal.ZERO;
        for (ExcelRow l : read.rows()) {
            String code = l.text(CODE).toUpperCase(Locale.ROOT);
            BigDecimal amount;
            try {
                amount = l.amount(AMOUNT);
            } catch (IllegalArgumentException e) {
                errors.add(new Problem(l.rowNumber(), e.getMessage()));
                continue;
            }
            if (code.equals("TOTAL")) {
                fileTotal = amount;
                break;
            }
            if (code.isEmpty()) {
                if (amount != null && amount.signum() != 0) errors.add(new Problem(l.rowNumber(), "Brokerage of " + amount + " has no client code"));
                continue;
            }
            if (amount == null) amount = BigDecimal.ZERO;
            YearMonth month;
            try {
                LocalDate end = l.date(END);
                if (end == null) throw new IllegalArgumentException("blank");
                month = YearMonth.from(end);
            } catch (IllegalArgumentException e) {
                errors.add(new Problem(l.rowNumber(), "Brokerage end period '" + l.text(END) + "' is not a date like 31-Aug-2026"));
                continue;
            }
            rawSum = rawSum.add(amount);
            byMonth.merge(month, amount, BigDecimal::add);
            final BigDecimal a = amount;
            byCode.compute(code, (k, c) -> c == null ? new Client(l.rowNumber(), l.text(NAME), l.text(FAMILY_HEAD_CODE), a) : c.plus(a));
        }

        List<StagedIncomeRow> rows = new ArrayList<>();
        for (var e : byCode.entrySet()) {
            Client c = e.getValue();
            BigDecimal net = c.net().setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal gst = net.multiply(GST_RATE).setScale(SCALE, RoundingMode.HALF_UP);
            rows.add(new StagedIncomeRow(c.row(), e.getKey(), "", c.name().isBlank() ? e.getKey() : c.name(), "", c.familyHeadCode(), "", "",
                    net.add(gst), gst, net));
            if (net.signum() < 0) warnings.add(new Problem(c.row(), "Client " + e.getKey() + " has negative brokerage " + net));
        }
        Totals rowTotals = rows.stream().reduce(Totals.ZERO, Totals::plus, (x, y) -> x);
        if (rows.isEmpty()) errors.add(new Problem(0, "No client rows found on '" + SHEET + "'"));
        if (fileTotal == null) {
            errors.add(new Problem(0, "No 'Total' row found at the end of '" + SHEET + "'"));
        } else {
            BigDecimal gap = fileTotal.subtract(rawSum).abs();
            if (gap.compareTo(TOTAL_TOLERANCE) > 0) {
                errors.add(new Problem(0, "Rows add up to " + rawSum.setScale(2, RoundingMode.HALF_UP) + " but the Total row says "
                        + fileTotal.setScale(2, RoundingMode.HALF_UP)));
            } else if (gap.signum() != 0) {
                warnings.add(new Problem(0, "The Total row (" + fileTotal.setScale(4, RoundingMode.HALF_UP) + ") differs from the rows by "
                        + gap.setScale(4, RoundingMode.HALF_UP) + " (rounding); the rows are used"));
            }
        }
        Map<YearMonth, BigDecimal> netByMonth = new TreeMap<>();
        byMonth.forEach((m, v) -> netByMonth.put(m, v.setScale(SCALE, RoundingMode.HALF_UP)));
        // The saved lines must equal the totals checked at save time: Finesse's Total row is checked above, the rows are used.
        return new ParsedIncomeFile(rows, errors.isEmpty() ? rowTotals : null, rowTotals, errors, warnings, netByMonth);
    }

    private static ParsedIncomeFile failed(String message) {
        return new ParsedIncomeFile(List.of(), null, Totals.ZERO, List.of(new Problem(0, message)), List.of());
    }

    private record Client(int row, String name, String familyHeadCode, BigDecimal net) {
        Client plus(BigDecimal amount) {
            return new Client(row, name, familyHeadCode, net.add(amount));
        }
    }
}
