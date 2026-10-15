package money.epsilon.brokerage.ingestion.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import money.epsilon.brokerage.ingestion.upload.UploadService.NotInMaster;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** The clients of an upload that are not in Master MIS, to add them there (or give them an RM on screen). */
final class NotInMasterWorkbook {

    static final String[] HEADERS = {"Finesse code(s)", "Client", "PAN", "Why", "Net brokerage"};

    private NotInMasterWorkbook() {
    }

    static byte[] build(List<NotInMaster> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Not in Master MIS");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            Row h = s.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                h.createCell(c).setCellValue(HEADERS[c]);
                h.getCell(c).setCellStyle(head);
            }
            int r = 1;
            for (NotInMaster n : rows) {
                Row row = s.createRow(r++);
                row.createCell(0).setCellValue(n.codes());
                row.createCell(1).setCellValue(n.name());
                row.createCell(2).setCellValue(n.pan() == null ? "" : n.pan());
                row.createCell(3).setCellValue(n.reason());
                row.createCell(4).setCellValue(n.net().doubleValue());
                row.getCell(4).setCellStyle(money);
            }
            int[] widths = {18, 34, 14, 44, 16};
            for (int c = 0; c < widths.length; c++) s.setColumnWidth(c, widths[c] * 256);
            s.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
