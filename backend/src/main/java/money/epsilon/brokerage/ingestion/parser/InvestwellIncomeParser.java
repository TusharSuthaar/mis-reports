package money.epsilon.brokerage.ingestion.parser;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Problem;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Totals;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter fmt = new DataFormatter(Locale.ENGLISH);
            Sheet sheet = wb.getSheetAt(0);
            Map<String, Integer> col = null;
            int headerRow = -1;
            for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS); r++) {
                Map<String, Integer> found = headers(sheet.getRow(r), fmt, eval);
                if (found.keySet().containsAll(REQUIRED)) {
                    col = found;
                    headerRow = r;
                    break;
                }
            }
            if (col == null) {
                errors.add(new Problem(0, "Header row not found. Expected columns: " + String.join(", ", REQUIRED)));
                return new ParsedIncomeFile(rows, null, Totals.ZERO, errors, warnings);
            }

            Set<String> seenCodes = new HashSet<>();
            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                int excelRow = r + 1;
                String client = text(row, col.get(CLIENT), fmt, eval);
                if ("TOTAL".equalsIgnoreCase(client)) {
                    fileTotals = new Totals(amount(row, col.get(INCOME), excelRow, errors), amount(row, col.get(GST), excelRow, errors),
                            amount(row, col.get(NET), excelRow, errors));
                    break;
                }
                String code = text(row, col.get(CODE), fmt, eval);
                if (client.isEmpty() && code.isEmpty()) continue;

                StagedIncomeRow staged = new StagedIncomeRow(excelRow, code, text(row, col.get(CODE2), fmt, eval), client,
                        text(row, col.get(PAN), fmt, eval), col.containsKey(FAMILY_HEAD_CODE) ? text(row, col.get(FAMILY_HEAD_CODE), fmt, eval) : "",
                        text(row, col.get(RM), fmt, eval), text(row, col.get(SUB_BROKER), fmt, eval),
                        amount(row, col.get(INCOME), excelRow, errors), amount(row, col.get(GST), excelRow, errors),
                        amount(row, col.get(NET), excelRow, errors));
                rows.add(staged);
                validate(staged, seenCodes, errors, warnings);
            }
        } catch (Exception e) {
            errors.add(new Problem(0, "The file could not be read as an Excel workbook (.xlsx)"));
            return new ParsedIncomeFile(List.of(), null, Totals.ZERO, errors, warnings);
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

    private static Map<String, Integer> headers(Row row, DataFormatter fmt, FormulaEvaluator eval) {
        Map<String, Integer> found = new HashMap<>();
        if (row == null) return found;
        for (Cell cell : row) {
            String h = fmt.formatCellValue(cell, eval).trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
            if (!h.isEmpty()) found.putIfAbsent(h, cell.getColumnIndex());
        }
        return found;
    }

    private static String text(Row row, int col, DataFormatter fmt, FormulaEvaluator eval) {
        Cell cell = row.getCell(col);
        return cell == null ? "" : fmt.formatCellValue(cell, eval).trim();
    }

    /** Numeric cells and numeric text; blanks are 0. Kept at 4 decimals (Investwell's precision). */
    private static BigDecimal amount(Row row, int col, int excelRow, List<Problem> errors) {
        Cell cell = row.getCell(col);
        if (cell == null) return BigDecimal.ZERO.setScale(SCALE);
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(SCALE, RoundingMode.HALF_UP);
        }
        String s = type == CellType.STRING ? cell.getStringCellValue().trim().replace(",", "") : "";
        if (s.isEmpty()) return BigDecimal.ZERO.setScale(SCALE);
        try {
            return new BigDecimal(s).setScale(SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            errors.add(new Problem(excelRow, "'" + s + "' is not an amount"));
            return BigDecimal.ZERO.setScale(SCALE);
        }
    }
}
