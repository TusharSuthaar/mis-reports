package money.epsilon.brokerage.masterimport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Reads the masters workbook into plain text rows keyed by the spec's column names. */
final class WorkbookReader {

    private static final int HEADER_SEARCH_ROWS = 10;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            pattern("d-M-uuuu"), pattern("d/M/uuuu"), pattern("d.M.uuuu"),
            pattern("d-MMM-uuuu"), pattern("d MMM uuuu"), pattern("d-MMM-uu"), pattern("d/MMM/uuuu"));

    private WorkbookReader() {
    }

    record SheetRow(int rowNumber, Map<String, String> values) {

        /** Cleaned text of a column (null if blank or absent). */
        String get(String header) {
            String v = values.get(header);
            return v == null || v.isBlank() ? null : v.trim();
        }

        Optional<LocalDate> date(String header) {
            String v = get(header);
            return v == null ? Optional.empty() : Optional.of(parseDate(v, header));
        }
    }

    record SheetData(SheetSpec spec, List<SheetRow> rows, List<String> missingColumns) {
    }

    /** @return the sheets that exist in the workbook, keyed by spec, in spec order */
    static Map<SheetSpec, SheetData> read(byte[] content) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            Map<String, Sheet> sheetsByName = new HashMap<>();
            for (Sheet s : wb) sheetsByName.put(SheetSpec.normalise(s.getSheetName()), s);

            Map<SheetSpec, SheetData> result = new LinkedHashMap<>();
            for (SheetSpec spec : SheetSpec.ALL) {
                Sheet sheet = sheetsByName.get(SheetSpec.normalise(spec.name()));
                if (sheet != null) result.put(spec, readSheet(spec, sheet, formatter, evaluator));
            }
            return result;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("The file could not be read as an Excel workbook (.xlsx)", e);
        }
    }

    private static SheetData readSheet(SheetSpec spec, Sheet sheet, DataFormatter fmt, FormulaEvaluator eval) {
        Map<String, Integer> columnIndex = null;
        int headerRow = -1;
        for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + HEADER_SEARCH_ROWS); r++) {
            Map<String, Integer> found = headerColumns(sheet.getRow(r), fmt, eval);
            if (spec.columns().stream().filter(SheetSpec.Column::required)
                    .allMatch(c -> found.containsKey(SheetSpec.normalise(c.header())))) {
                columnIndex = found;
                headerRow = r;
                break;
            }
        }
        if (columnIndex == null) {
            List<String> required = spec.columns().stream().filter(SheetSpec.Column::required).map(SheetSpec.Column::header).toList();
            return new SheetData(spec, List.of(), required);
        }

        List<SheetRow> rows = new ArrayList<>();
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, String> values = new LinkedHashMap<>();
            boolean blank = true;
            for (SheetSpec.Column c : spec.columns()) {
                Integer idx = columnIndex.get(SheetSpec.normalise(c.header()));
                String v = idx == null ? null : cellText(row.getCell(idx), fmt, eval);
                if (v != null && !v.isBlank()) blank = false;
                values.put(c.header(), v);
            }
            if (!blank) rows.add(new SheetRow(r + 1, values));
        }
        return new SheetData(spec, rows, List.of());
    }

    private static Map<String, Integer> headerColumns(Row row, DataFormatter fmt, FormulaEvaluator eval) {
        Map<String, Integer> found = new HashMap<>();
        if (row == null) return found;
        for (Cell cell : row) {
            String text = cellText(cell, fmt, eval);
            if (text != null && !text.isBlank()) found.putIfAbsent(SheetSpec.normalise(text), cell.getColumnIndex());
        }
        return found;
    }

    /** Dates become ISO yyyy-MM-dd; everything else is the text Excel would display. */
    private static String cellText(Cell cell, DataFormatter fmt, FormulaEvaluator eval) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? eval.evaluateFormulaCell(cell) : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return fmt.formatCellValue(cell, eval).trim();
    }

    static LocalDate parseDate(String text, String header) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(text.trim(), f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new IllegalArgumentException(header + ": '" + text + "' is not a date (use 2026-04-01 or 01-04-2026)");
    }

    private static DateTimeFormatter pattern(String p) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH);
    }
}
