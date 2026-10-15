package money.epsilon.brokerage.masterimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** The empty masters workbook users fill in: an instructions sheet, then one sheet per {@link SheetSpec}. */
final class MasterTemplate {

    private MasterTemplate() {
    }

    static byte[] build() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font white = wb.createFont();
            white.setBold(true);
            white.setColor(IndexedColors.WHITE.getIndex());
            CellStyle header = wb.createCellStyle();
            header.setFont(white);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(bold);

            Sheet info = wb.createSheet("Instructions");
            String[] lines = {
                    "Epsilon MIS: masters import",
                    "Fill the sheets you need; leave out (or delete) the rest. Column order does not matter, names do.",
                    "Required columns are marked * in the list below.",
                    "Dates: 2026-04-01 or 01-04-2026. All dates are inclusive.",
                    "The whole file is applied or nothing is: any error means no change at all. Use 'Check file' first.",
                    "Re-importing the same file changes nothing. History is never rewritten: a new branch / RD / region",
                    "must start after the current one started.",
                    ""};
            int r = 0;
            for (String line : lines) info.createRow(r++).createCell(0).setCellValue(line);
            info.getRow(0).getCell(0).setCellStyle(boldStyle);
            for (SheetSpec spec : SheetSpec.ALL) {
                Row row = info.createRow(r++);
                row.createCell(0).setCellValue(spec.name());
                row.getCell(0).setCellStyle(boldStyle);
                row.createCell(1).setCellValue(spec.help());
                info.createRow(r++).createCell(1).setCellValue("Columns: " + String.join(", ",
                        spec.columns().stream().map(c -> c.header() + (c.required() ? " *" : "")).toList()));
            }
            info.setColumnWidth(0, 22 * 256);
            info.setColumnWidth(1, 110 * 256);

            for (SheetSpec spec : SheetSpec.ALL) {
                Sheet sheet = wb.createSheet(spec.name());
                Row head = sheet.createRow(0);
                for (int c = 0; c < spec.columns().size(); c++) {
                    head.createCell(c).setCellValue(spec.columns().get(c).header());
                    head.getCell(c).setCellStyle(header);
                    sheet.setColumnWidth(c, 20 * 256);
                }
                sheet.createFreezePane(0, 1);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
