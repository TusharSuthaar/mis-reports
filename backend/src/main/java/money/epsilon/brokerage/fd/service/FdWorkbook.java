package money.epsilon.brokerage.fd.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.fd.domain.FdEntry;
import money.epsilon.brokerage.fd.service.FdService.FdView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** The month's FDs in the FD register's column order (as kept in Excel before the platform). */
final class FdWorkbook {

    static final String[] HEADERS = {"SR.NO", "Month", "Region", "Rd Name", "Rm Name", "Ref/sub-broker", "Client Name", "Pan No",
            "Client Type", "Mobile No", "Email Id", "Address", "FD Name", "FD No.", "Interest Frequency", "PARTNER", "Deposit Date",
            "Deposit Period", "Maturity Date", "Int Rate (%)", "Investment Amt (Rs)", "Maturity Value", "CERTIFICATE", "Margin (%)",
            "Gross Revenue", "TDS", "GST", "NET REVENUE", "INVOICE AMT", "Invoice Raised by Accts", "Payment Status", "Remarks"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);

    private FdWorkbook() {
    }

    static byte[] build(List<FdView> entries) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("FD");
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
            CellStyle pct = wb.createCellStyle();
            pct.setDataFormat(wb.createDataFormat().getFormat("0.00"));
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);

            Row h = s.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
            }
            int r = 1;
            for (FdView e : entries) {
                if (e.status() == FdEntry.Status.CANCELLED) continue;
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
                text(row, c++, e.clientType(), null);
                text(row, c++, e.mobile(), null);
                text(row, c++, e.email(), null);
                text(row, c++, e.address(), wrap);
                text(row, c++, e.fdName(), null);
                text(row, c++, e.fdNo(), null);
                text(row, c++, e.interestFrequency() == null ? null : words(e.interestFrequency().name()), null);
                text(row, c++, e.platform(), null);
                text(row, c++, day(e.depositDate()), null);
                text(row, c++, e.periodMonths() + " Months", null);
                text(row, c++, day(e.maturityDate()), null);
                num(row, c++, e.ratePercent(), pct);
                num(row, c++, e.amount(), money);
                num(row, c++, e.maturityValue(), money);
                text(row, c++, words(e.certificate().name()), null);
                num(row, c++, e.marginPercent(), pct);
                num(row, c++, e.gross(), money);
                num(row, c++, e.tdsApplied() ? e.tds() : null, money);
                num(row, c++, e.gst(), money);
                num(row, c++, e.net(), money);
                num(row, c++, e.invoice(), money);
                text(row, c++, e.invoiceRaised() ? "YES" : "NO", null);
                text(row, c++, payment(e.paymentStatus()), null);
                text(row, c, e.remarks(), wrap);
                r++;
            }
            for (int i = 0; i < HEADERS.length; i++) s.setColumnWidth(i, i == 11 || i == HEADERS.length - 1 ? 40 * 256 : 16 * 256);
            s.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String payment(FdEntry.PaymentStatus p) {
        return switch (p) {
            case PENDING -> "Pending";
            case RECEIVED -> "Received";
            case RECEIVED_GST_PENDING -> "Received (GST amount is pending)";
        };
    }

    /** HALF_YEARLY → "Half yearly". */
    private static String words(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String day(LocalDate d) {
        return d == null ? null : DAY.format(d);
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
