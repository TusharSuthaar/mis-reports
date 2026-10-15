package money.epsilon.brokerage.ingestion.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Problem;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Totals;
import money.epsilon.brokerage.platform.excel.ExcelFile;
import money.epsilon.brokerage.platform.excel.ExcelRow;
import org.springframework.stereotype.Component;

/**
 * Investwell "Income Report" (.xlsx): title rows, then a header row found by column names, one row per client,
 * and a final row whose CLIENT is "Total" (the control totals). INCOME AMOUNT includes GST; NET = INCOME − GST.
 */
@Component
public class InvestwellIncomeParser implements IncomeFileParser {

    public static final String SOURCE = "INVESTWELL";

    static final String CLIENT = "CLIENT";
    static final String INCOME = "INCOME AMOUNT";
    static final String PAN = "PAN";
    static final String RM = "RELATIONSHIP MANAGER";
    static final String SUB_BROKER = "SUB BROKER";
    static final String FAMILY_HEAD_CODE = "FAMILY HEAD IWELL CODE";
    static final String CODE = "CLIENT IWELL CODE";
    static final String CODE2 = "CLIENT IWELL CODE 2";
    static final String GST = "GST";
    static final String NET = "NET BROKERAGE AMOUNT";
    static final List<String> REQUIRED = List.of(CLIENT, INCOME, PAN, RM, SUB_BROKER, CODE, GST, NET);

    private static final int HEADER_SEARCH_ROWS = 20;
    private static final int SCALE = 4;
    private static final BigDecimal NET_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal GST_RATE = new BigDecimal("18");
    private static final BigDecimal GST_BASE = new BigDecimal("118");

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public ParsedIncomeFile parse(byte[] content) {
        List<Problem> errors = new ArrayList<>();
        List<Problem> warnings = new ArrayList<>();
        List<StagedIncomeRow> rows = new ArrayList<>();
        Totals fileTotals = null;
        ExcelFile.Table table;
        try (ExcelFile file = ExcelFile.open(content)) {
            table = file.table(null, REQUIRED, HEADER_SEARCH_ROWS).orElse(null);
        } catch (IllegalArgumentException e) {
            errors.add(new Problem(0, ExcelFile.UNREADABLE));
            return new ParsedIncomeFile(List.of(), null, Totals.ZERO, errors, warnings);
        }
        if (table == null || !table.missing().isEmpty()) {
            errors.add(new Problem(0, "Header row not found. Expected columns: " + String.join(", ", REQUIRED)));
            return new ParsedIncomeFile(rows, null, Totals.ZERO, errors, warnings);
        }

        Set<String> seenCodes = new HashSet<>();
        for (ExcelRow row : table.rows()) {
            int excelRow = row.rowNumber();
            String client = row.text(CLIENT);
            if ("TOTAL".equalsIgnoreCase(client)) {
                fileTotals = new Totals(amount(row, INCOME, errors), amount(row, GST, errors), amount(row, NET, errors));
                break;
            }
            String code = row.text(CODE);
            if (client.isEmpty() && code.isEmpty()) continue;

            StagedIncomeRow staged = new StagedIncomeRow(excelRow, code, row.text(CODE2), client, row.text(PAN), row.text(FAMILY_HEAD_CODE),
                    row.text(RM), row.text(SUB_BROKER), amount(row, INCOME, errors), amount(row, GST, errors), amount(row, NET, errors));
            rows.add(staged);
            validate(staged, seenCodes, errors, warnings);
        }

        Totals rowTotals = rows.stream().reduce(Totals.ZERO, Totals::plus, (a, b) -> a);
        if (fileTotals == null) {
            errors.add(new Problem(0, "No 'Total' row found at the end of the file"));
        } else if (fileTotals.gross().compareTo(rowTotals.gross()) != 0 || fileTotals.gst().compareTo(rowTotals.gst()) != 0
                || fileTotals.net().compareTo(rowTotals.net()) != 0) {
            errors.add(new Problem(0, "Rows do not add up to the Total row: rows gross " + rowTotals.gross() + ", GST " + rowTotals.gst()
                    + ", net " + rowTotals.net() + " vs Total gross " + fileTotals.gross() + ", GST " + fileTotals.gst() + ", net " + fileTotals.net()));
        }
        return new ParsedIncomeFile(rows, fileTotals, rowTotals, errors, warnings);
    }

    private static void validate(StagedIncomeRow r, Set<String> seenCodes, List<Problem> errors, List<Problem> warnings) {
        if (r.clientCode().isEmpty()) {
            errors.add(new Problem(r.rowNumber(), "CLIENT IWELL CODE is missing"));
        } else if (!seenCodes.add(r.clientCode())) {
            errors.add(new Problem(r.rowNumber(), "CLIENT IWELL CODE " + r.clientCode() + " appears more than once"));
        }
        if (r.gross().subtract(r.gst()).subtract(r.net()).abs().compareTo(NET_TOLERANCE) > 0) {
            errors.add(new Problem(r.rowNumber(), "Income − GST ≠ net (" + r.gross() + " − " + r.gst() + " ≠ " + r.net() + ")"));
        }
        BigDecimal expectedGst = r.gross().multiply(GST_RATE).divide(GST_BASE, SCALE, RoundingMode.HALF_UP);
        if (expectedGst.subtract(r.gst()).abs().compareTo(NET_TOLERANCE) > 0) {
            warnings.add(new Problem(r.rowNumber(), "GST " + r.gst() + " is not 18/118 of income " + r.gross()));
        }
    }

    /** A number or numeric text, kept at 4 decimals (Investwell's precision); blank is 0; anything else is a row error. */
    private static BigDecimal amount(ExcelRow row, String header, List<Problem> errors) {
        try {
            BigDecimal v = row.amount(header);
            return (v == null ? BigDecimal.ZERO : v).setScale(SCALE, RoundingMode.HALF_UP);
        } catch (IllegalArgumentException e) {
            errors.add(new Problem(row.rowNumber(), "'" + row.text(header) + "' is not an amount"));
            return BigDecimal.ZERO.setScale(SCALE);
        }
    }
}
