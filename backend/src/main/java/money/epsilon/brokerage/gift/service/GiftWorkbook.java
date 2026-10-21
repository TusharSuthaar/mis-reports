package money.epsilon.brokerage.gift.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.gift.domain.GiftEntry;
import money.epsilon.brokerage.gift.service.GiftService.GiftView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** GIFT City entries in the GIFT CITY register's column order, then the brokerage columns. */
final class GiftWorkbook {

    static final String[] HEADERS = {"SR .NO", "Month", "Region", "Referral Partner", "RM Name", "CLIENT NAME", "Pan No", "Mobile No",
            "Email Id", "Address", "PARTNER", "AMT", "AMT in RS.", "ENTRY STATUS", "Trxn Status", "Brokerage", "TDS", "GST", "Net Revenue",
            "Invoice Amt", "Payment Status", "Amt Received Date", "Remarks"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    private GiftWorkbook() {
    }

    static byte[] build(List<GiftView> entries) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("GIFT CITY");
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
            for (GiftView e : entries) {
                if (e.status() == GiftEntry.Status.CANCELLED) continue;
                Row row = s.createRow(r);
                int c = 0;
                num(row, c++, r, null);
                text(row, c++, MONTH.format(e.month()), null);
                text(row, c++, e.region(), null);
                text(row, c++, e.partnerName(), null);
                text(row, c++, e.rmName(), null);
                text(row, c++, e.clientName(), null);
                text(row, c++, e.pan(), null);
                text(row, c++, e.mobile(), null);
                text(row, c++, e.email(), null);
                text(row, c++, e.address(), wrap);
                text(row, c++, e.fund(), null);
                text(row, c++, e.amountUsd() == null ? null : "USD " + e.amountUsd().stripTrailingZeros().toPlainString(), null);
                num(row, c++, e.amountInr(), money);
                text(row, c++, e.onPortal() ? "Updated on portal" : null, null);
                text(row, c++, e.trxnStatus() == GiftEntry.TrxnStatus.DONE ? "Done" : "Pending", null);
                num(row, c++, e.brokerage(), money);
                num(row, c++, e.tdsApplied() ? e.tds() : null, money);
                num(row, c++, e.gst(), money);
                num(row, c++, e.net(), money);
                num(row, c++, e.invoice(), money);
                text(row, c++, e.paymentStatus() == GiftEntry.PaymentStatus.RECEIVED ? "Received" : "Pending", null);
                text(row, c++, e.receivedOn() == null ? null : DAY.format(e.receivedOn()), null);
                text(row, c, e.remarks(), wrap);
                r++;
            }
            for (int i = 0; i < HEADERS.length; i++) s.setColumnWidth(i, i == 9 || i == 10 || i == HEADERS.length - 1 ? 36 * 256 : 15 * 256);
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
