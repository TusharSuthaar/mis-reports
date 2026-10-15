package money.epsilon.brokerage.masterimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Builds small .xlsx files for tests. LocalDate values become real Excel date cells. */
public final class TestWorkbook {

    private final Map<String, List<Object[]>> sheets = new LinkedHashMap<>();
    private final Map<String, Integer> blankRowsBeforeHeader = new LinkedHashMap<>();
    private String current;

    /** Starts (or replaces) a sheet; following rows go to it. */
    public TestWorkbook sheet(String name, String... headers) {
        current = name;
        sheets.put(name, new ArrayList<>());
        sheets.get(name).add(headers);
        return this;
    }

    /** Puts {@code n} title rows above the header of the last sheet added. */
    public TestWorkbook titleRows(int n) {
        blankRowsBeforeHeader.put(current, n);
        return this;
    }

    public TestWorkbook row(Object... values) {
        sheets.get(current).add(values);
        return this;
    }

    public byte[] bytes() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd-mmm-yyyy"));
            for (var entry : sheets.entrySet()) {
                Sheet sheet = wb.createSheet(entry.getKey());
                int r = 0;
                for (int i = 0; i < blankRowsBeforeHeader.getOrDefault(entry.getKey(), 0); i++) {
                    sheet.createRow(r++).createCell(0).setCellValue("Title line " + i);
                }
                for (Object[] values : entry.getValue()) {
                    Row row = sheet.createRow(r++);
                    for (int c = 0; c < values.length; c++) {
                        Object v = values[c];
                        if (v == null) continue;
                        Cell cell = row.createCell(c);
                        if (v instanceof LocalDate d) {
                            cell.setCellValue(d);
                            cell.setCellStyle(dateStyle);
                        } else if (v instanceof Number n) {
                            cell.setCellValue(n.doubleValue());
                        } else {
                            cell.setCellValue(v.toString());
                        }
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
