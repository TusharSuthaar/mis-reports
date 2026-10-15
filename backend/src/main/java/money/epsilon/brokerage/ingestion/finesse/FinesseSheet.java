package money.epsilon.brokerage.ingestion.finesse;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * One sheet of a Finesse export, read as rows of cells by header name. Finesse puts a title row above the header
 * ("MF Brokerage for the period …"); the header row is found by its column names. Header names are compared in
 * upper case with spaces collapsed.
 */
final class FinesseSheet {

    private static final int HEADER_SEARCH_ROWS = 15;

    /** A data row: {@code rowNumber} is the Excel row number. */
    record Line(int rowNumber, Map<String, Object> values) {

        String text(String header) {
            Object v = values.get(header);
            if (v == null) return "";
            if (v instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
            return v.toString().trim();
        }

        /** Numeric cells, or numeric text (commas allowed); blank = null; anything else throws. */
        BigDecimal amount(String header) {
            Object v = values.get(header);
            if (v == null) return null;
            if (v instanceof BigDecimal d) return d;
            String s = v.toString().trim().replace(",", "");
            if (s.isEmpty()) return null;
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Row " + rowNumber + ": '" + s + "' in " + header + " is not an amount");
            }
        }
    }

    record Read(String title, List<Line> lines, List<String> missing, List<String> sheetNames, boolean sheetFound) {
    }

    private FinesseSheet() {
    }

    /**
     * @param sheetName the sheet to read (case-insensitive); null = the first sheet
     * @param required  header names that must all be on the header row
     */
    static Read read(byte[] content, String sheetName, List<String> required) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter fmt = new DataFormatter(Locale.ENGLISH);
            List<String> names = new ArrayList<>();
            Sheet sheet = null;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                names.add(wb.getSheetName(i));
                if (sheetName == null ? i == 0 : wb.getSheetName(i).trim().equalsIgnoreCase(sheetName)) sheet = wb.getSheetAt(i);
            }
            if (sheet == null) return new Read(null, List.of(), required, names, false);

            Map<String, Integer> col = null;
            int headerRow = -1;
            String title = null;
            for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS); r++) {
                Map<String, Integer> found = headers(sheet.getRow(r), fmt, eval);
                if (found.keySet().containsAll(required)) {
                    col = found;
                    headerRow = r;
                    break;
                }
                if (title == null && sheet.getRow(r) != null && sheet.getRow(r).getCell(0) != null) {
                    String t = fmt.formatCellValue(sheet.getRow(r).getCell(0), eval).trim();
                    if (!t.isEmpty()) title = t;
                }
            }
            if (col == null) {
                Map<String, Integer> best = headers(sheet.getRow(sheet.getFirstRowNum()), fmt, eval);
                return new Read(title, List.of(), required.stream().filter(h -> !best.containsKey(h)).toList(), names, true);
            }
            List<Line> lines = new ArrayList<>();
            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, Object> values = new HashMap<>();
                boolean any = false;
                for (var e : col.entrySet()) {
                    Object v = value(row.getCell(e.getValue()), fmt, eval);
                    if (v != null && !(v instanceof String s && s.isBlank())) any = true;
                    values.put(e.getKey(), v);
                }
                if (any) lines.add(new Line(r + 1, values));
            }
            return new Read(title, lines, List.of(), names, true);
        } catch (java.io.IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae && iae.getMessage() != null && iae.getMessage().startsWith("Row ")) throw iae;
            throw new IllegalArgumentException("The file could not be read as an Excel workbook (.xlsx)");
        }
    }

    static String header(String s) {
        return s == null ? "" : s.replace(' ', ' ').trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static Map<String, Integer> headers(Row row, DataFormatter fmt, FormulaEvaluator eval) {
        Map<String, Integer> found = new HashMap<>();
        if (row == null) return found;
        for (Cell cell : row) {
            String h = header(fmt.formatCellValue(cell, eval));
            if (!h.isEmpty()) found.putIfAbsent(h, cell.getColumnIndex());
        }
        return found;
    }

    /** Numbers as exact decimals (BigDecimal of the double as Excel stores it), everything else as text. */
    private static Object value(Cell cell, DataFormatter fmt, FormulaEvaluator eval) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && !org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        if (type == CellType.BLANK) return null;
        return fmt.formatCellValue(cell, eval).replace(' ', ' ').trim();
    }
}
