package money.epsilon.brokerage.masterimport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import money.epsilon.brokerage.platform.excel.ExcelFile;
import money.epsilon.brokerage.platform.excel.ExcelRow;

/** Reads the masters workbook into plain text rows keyed by the spec's column names (through {@link ExcelFile}). */
final class WorkbookReader {

    private static final int HEADER_SEARCH_ROWS = 10;

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
            return v == null ? Optional.empty() : Optional.of(ExcelRow.parseDate(v, header));
        }
    }

    record SheetData(SheetSpec spec, List<SheetRow> rows, List<String> missingColumns) {
    }

    /** @return the sheets that exist in the workbook, keyed by spec, in spec order */
    static Map<SheetSpec, SheetData> read(byte[] content) {
        try (ExcelFile file = ExcelFile.open(content)) {
            Map<SheetSpec, SheetData> result = new LinkedHashMap<>();
            for (SheetSpec spec : SheetSpec.ALL) {
                List<String> required = spec.columns().stream().filter(SheetSpec.Column::required).map(SheetSpec.Column::header).toList();
                file.table(spec.name(), required, HEADER_SEARCH_ROWS).ifPresent(table -> {
                    if (!table.missing().isEmpty()) {
                        result.put(spec, new SheetData(spec, List.of(), required));
                        return;
                    }
                    List<SheetRow> rows = new ArrayList<>();
                    for (ExcelRow row : table.rows()) {
                        Map<String, String> values = new LinkedHashMap<>();
                        boolean blank = true;
                        for (SheetSpec.Column c : spec.columns()) {
                            String v = row.text(c.header());
                            if (!v.isBlank()) blank = false;
                            values.put(c.header(), v.isBlank() ? null : v);
                        }
                        if (!blank) rows.add(new SheetRow(row.rowNumber(), values));
                    }
                    result.put(spec, new SheetData(spec, rows, List.of()));
                });
            }
            return result;
        }
    }
}
