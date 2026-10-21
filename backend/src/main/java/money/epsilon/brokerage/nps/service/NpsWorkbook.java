package money.epsilon.brokerage.nps.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.nps.domain.NpsEntry;
import money.epsilon.brokerage.nps.service.NpsService.NpsView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** NPS investments in the NPS register's column order (R-86). GST is shown as CGST + SGST, half each. */
final class NpsWorkbook {

    static final String[] HEADERS = {"SR.NO", "MONTH", "Region", "Rd Name", "Rm Name", "Ref/sub-broker", "Client Name", "Pan No", "Mobile No",
            "Email Id", "Address", "PARTNER", "INV Date", "Investment Amt (Rs)", "Welcome Mail", "Investment Mail", "Client Type",
            "Certificate Status", "Revenue Remarks", "Entry Status", "Invoice Amt Without GST", "CGST", "SGST", "Invoice amt with GST",
            "Amt received", "Remarks"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    private NpsWorkbook() {
    }

    static byte[] build(List<NpsView> entries) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("NPS");
            Font bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##,##0.00"));
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);

            Row h = s.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
            }
            int r = 1;
            for (NpsView e : entries) {
                if (e.status() == NpsEntry.Status.CANCELLED) continue;
                Row row = s.createRow(r);
                int c = 0;
                num(row, c++, r, null);
                text(row, c++, MONTH.format(e.month()), null);
                text(row, c++, e.region(), null);
                text(row, c++, e.rd(), null);
                text(row, c++, e.rmName(), null);
                text(row, c++, e.partnerName(), null);
                text(row, c++, e.clientName(), null);
                text(row, c++, e.pan(), null);
                text(row, c++, e.mobile(), null);
                text(row, c++, e.email(), null);
                text(row, c++, e.address(), wrap);
                text(row, c++, e.pop(), null);
                text(row, c++, DAY.format(e.investmentDate()), null);
                num(row, c++, e.amount(), money);
                text(row, c++, e.welcomeMail() ? "Y" : null, null);
                text(row, c++, e.investmentMail() ? "Y" : null, null);
                text(row, c++, e.clientType(), null);
                text(row, c++, e.certificate() == NpsEntry.Certificate.RECEIVED ? "Received" : e.certificate() == NpsEntry.Certificate.PENDING ? "Pending" : null, null);
                text(row, c++, e.revenueRemarks(), wrap);
                text(row, c++, e.onPortal() ? "UPDATED ON APP" : null, null);
                boolean revenue = e.revenue().signum() > 0;
                BigDecimal half = e.gst().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
                num(row, c++, revenue ? e.revenue() : null, money);
                num(row, c++, revenue ? half : null, money);
                num(row, c++, revenue ? e.gst().subtract(half) : null, money);
                num(row, c++, revenue ? e.invoice() : null, money);
                text(row, c++, e.receivedOn() == null ? null : DAY.format(e.receivedOn()), null);
                text(row, c, e.remarks(), wrap);
                r++;
            }
            for (int i = 0; i < HEADERS.length; i++) s.setColumnWidth(i, i == 10 || i == 18 || i == HEADERS.length - 1 ? 32 * 256 : 15 * 256);
            s.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        if (value == null) return;
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private static void num(Row row, int col, Object value, CellStyle style) {
        if (value == null) return;
        Cell c = row.createCell(col);
        c.setCellValue(value instanceof BigDecimal b ? b.doubleValue() : ((Number) value).doubleValue());
        if (style != null) c.setCellStyle(style);
    }
}
