package money.epsilon.brokerage.platform.excel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import money.epsilon.brokerage.platform.text.Text;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * The one way the app reads an uploaded Excel file (guardrail G-16). A sheet is read as a {@link Table}: its header row is
 * found by column names (title rows above it are fine), columns are looked up by name, and each cell comes back as a
 * {@link BigDecimal} (numbers, exactly as Excel stores them), a {@link LocalDate} (date cells) or text.
 *
 * <p>Names are forgiving: sheets and headers match ignoring case and extra spaces, and failing that ignoring punctuation
 * too ("RM-Branch" = "RM Branch", "Pan Number" = "PAN NUMBER"). When a header appears twice, the first one counts.
 */
public final class ExcelFile implements AutoCloseable {

    public static final String UNREADABLE = "The file could not be read as an Excel workbook (.xlsx)";

    private final Workbook workbook;
    private final FormulaEvaluator eval;
    private final DataFormatter fmt = new DataFormatter(Locale.ENGLISH);

    private ExcelFile(Workbook workbook) {
        this.workbook = workbook;
        this.eval = workbook.getCreationHelper().createFormulaEvaluator();
    }

    /** @throws IllegalArgumentException with {@link #UNREADABLE} when the bytes are not a workbook */
    public static ExcelFile open(byte[] content) {
        try {
            return new ExcelFile(WorkbookFactory.create(new ByteArrayInputStream(content)));
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException(UNREADABLE, e);
        }
    }

    public List<String> sheetNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) names.add(workbook.getSheetName(i));
        return names;
    }

    /**
     * Reads a sheet as a table.
     *
     * @param sheetName  null = the first sheet
     * @param required   headers that must all be on the header row
     * @param searchRows how many rows from the top may hold titles before the header
     * @return empty when there is no such sheet; otherwise a table whose {@link Table#missing()} lists the required headers
     *         not found (then it has no rows)
     */
    public Optional<Table> table(String sheetName, Collection<String> required, int searchRows) {
        Sheet sheet = sheet(sheetName);
        if (sheet == null) return Optional.empty();
        String title = null;
        for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + searchRows); r++) {
            Columns columns = columns(sheet.getRow(r));
            if (required.stream().allMatch(h -> columns.indexOf(h) != null)) return Optional.of(read(sheet, r, columns, title));
            if (title == null && sheet.getRow(r) != null) {
                Object first = value(sheet.getRow(r).getCell(0));
                if (first instanceof String s && !s.isBlank()) title = s;
            }
        }
        Columns top = columns(sheet.getRow(sheet.getFirstRowNum()));
        return Optional.of(new Table(sheet.getSheetName(), title, top, List.of(), required.stream().filter(h -> top.indexOf(h) == null).toList(), -1));
    }

    @Override
    public void close() {
        try {
            workbook.close();
        } catch (IOException ignored) {
            // read-only use: nothing to flush
        }
    }

    private Sheet sheet(String name) {
        if (name == null) return workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
        for (Sheet s : workbook) if (Text.key(s.getSheetName()).equals(Text.key(name))) return s;
        for (Sheet s : workbook) if (!Text.compact(name).isEmpty() && Text.compact(s.getSheetName()).equals(Text.compact(name))) return s;
        return null;
    }

    private Table read(Sheet sheet, int headerRow, Columns columns, String title) {
        List<ExcelRow> rows = new ArrayList<>();
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<Integer, Object> values = new HashMap<>();
            boolean any = false;
            for (int c : columns.labels.keySet()) {
                Object v = value(row.getCell(c));
                if (v != null) {
                    values.put(c, v);
                    if (!(v instanceof String s) || !s.isBlank()) any = true;
                }
            }
            if (any) rows.add(new ExcelRow(r + 1, columns, values));
        }
        return new Table(sheet.getSheetName(), title, columns, rows, List.of(), headerRow);
    }

    /**
     * The texts of one row of a sheet, column index → text (blank cells left out): for headers that span two rows, e.g. a
     * vendor's name above each of several "Base Price" columns. {@code rowIndex} is 0-based, as {@link Table#headerRow()}.
     */
    public Map<Integer, String> rowTexts(String sheetName, int rowIndex) {
        Map<Integer, String> result = new java.util.TreeMap<>();
        Sheet sheet = sheet(sheetName);
        if (sheet == null || rowIndex < 0 || sheet.getRow(rowIndex) == null) return result;
        for (Cell cell : sheet.getRow(rowIndex)) {
            Object v = value(cell);
            if (v != null && !v.toString().isBlank()) result.put(cell.getColumnIndex(), v instanceof BigDecimal d ? d.toPlainString() : v.toString().trim());
        }
        return result;
    }

    private Columns columns(Row row) {
        Columns c = new Columns();
        if (row == null) return c;
        for (Cell cell : row) {
            Object v = value(cell);
            if (v == null) continue;
            String h = v instanceof BigDecimal d ? d.toPlainString() : v.toString();
            if (h.isBlank()) continue;
            c.labels.put(cell.getColumnIndex(), h.trim());
            c.byKey.putIfAbsent(Text.key(h), cell.getColumnIndex());
            if (!Text.compact(h).isEmpty()) c.byCompact.putIfAbsent(Text.compact(h), cell.getColumnIndex());
        }
        return c;
    }

    private Object value(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType();
        boolean cached = false;
        if (type == CellType.FORMULA) {
            try {
                type = eval.evaluateFormulaCell(cell);
            } catch (RuntimeException e) {
                // A formula that cannot be worked out here (a link to another workbook, a function such as XLOOKUP): use the
                // value Excel saved with the file, which is what the user sees.
                type = cell.getCachedFormulaResultType();
                cached = true;
            }
        }
        if (type == CellType.BLANK || type == CellType._NONE || (cached && type == CellType.ERROR)) return null;
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate();
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String text = (cached ? (type == CellType.BOOLEAN ? String.valueOf(cell.getBooleanCellValue()) : cell.getStringCellValue())
                : fmt.formatCellValue(cell, eval)).replace(' ', ' ').trim();
        return text.isEmpty() ? null : text;
    }

    /** Header → column index. */
    public static final class Columns {
        /** Column index → the header as written, every column in order (a repeated header keeps each of its columns). */
        final java.util.TreeMap<Integer, String> labels = new java.util.TreeMap<>();
        final Map<String, Integer> byKey = new HashMap<>();
        final Map<String, Integer> byCompact = new HashMap<>();

        /** Every column whose header is {@code header} (a header may repeat), left to right. */
        public List<Integer> indexesOf(String header) {
            String key = Text.key(header);
            return labels.entrySet().stream().filter(e -> Text.key(e.getValue()).equals(key)).map(Map.Entry::getKey).toList();
        }

        Integer indexOf(String header) {
            Integer i = byKey.get(Text.key(header));
            if (i != null) return i;
            String compact = Text.compact(header);
            return compact.isEmpty() ? null : byCompact.get(compact);
        }
    }

    /** A sheet read below its header row. Blank rows are left out. {@code headerRow}: 0-based, −1 when not found. */
    public record Table(String sheetName, String title, Columns columns, List<ExcelRow> rows, List<String> missing, int headerRow) {

        public boolean has(String header) {
            return columns.indexOf(header) != null;
        }
    }
}
