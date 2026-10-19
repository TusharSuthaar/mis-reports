package money.epsilon.brokerage.bond.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.bond.domain.BondEntry;
import money.epsilon.brokerage.bond.service.BondService.BondView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** The month's bond deals in the bond register's column order (as kept in Excel before the platform). */
final class BondWorkbook {

    static final String[] HEADERS = {"SR.NO", "MONTH", "REGION", "RM NAME", "REF/SUB", "CLIENT NAME", "PAN NO", "MOBILE NO", "EMAIL ID",
            "ADDRESS", "PARTNER", "BOND NAME", "COUPON RATE (%)", "YTM (%)", "INTEREST PAYOUT FREQUENCY", "INTEREST PAYMENT SCHEDULE",
            "ISIN NO", "INV DATE", "DATE OF ALLOTMENT", "MATURITY DATE/SELL DATE", "QTY", "PURCHASE RATE PER UNIT", "FACE VALUE",
            "INVESTMENT AMOUNT", "ACCRUED INTEREST", "CONSIDERATION INVESTMENT AMOUNT", "CLIENT TYPE", "MARK UP %", "GROSS REVENUE",
            "TDS (WHERE EVER APPLICABLE)", "GST (WHERE EVER APPLICABLE)", "NET REVENUE (IN RS) - TDS", "INVOICE AMT",
            "INVOICE RAISED BY ACCTS", "INVOICE RAISED ON", "AMT RECEIVED ON", "PAYMENT STATUS", "REMARKS"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);

    private BondWorkbook() {
    }

    static byte[] build(List<BondView> entries) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("BONDS");
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
            pct.setDataFormat(wb.createDataFormat().getFormat("0.00##"));
            Row h = s.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
            }
            int r = 1;
            for (BondView e : entries) {
                if (e.status() == BondEntry.Status.CANCELLED) continue;
                Row row = s.createRow(r);
                int c = 0;
                num(row, c++, r, null);
                text(row, c++, MONTH.format(e.month()));
                text(row, c++, e.region());
                text(row, c++, e.rmName());
                text(row, c++, e.partnerName());
                text(row, c++, e.clientName());
                text(row, c++, e.pan());
                text(row, c++, e.mobile());
                text(row, c++, e.email());
                text(row, c++, e.address());
                text(row, c++, e.platformName());
                text(row, c++, e.bondName());
                num(row, c++, e.couponPercent(), pct);
                num(row, c++, e.ytmPercent(), pct);
                text(row, c++, e.payoutFrequency() == null ? null : words(e.payoutFrequency().name()));
                text(row, c++, e.paymentSchedule());
                text(row, c++, e.isin());
                text(row, c++, day(e.investmentDate()));
                text(row, c++, day(e.allotmentDate()));
                text(row, c++, day(e.maturityDate()));
                num(row, c++, e.quantity(), null);
                num(row, c++, e.ratePerUnit(), money);
                num(row, c++, e.faceValue(), money);
                num(row, c++, e.investmentAmount(), money);
                num(row, c++, e.accruedInterest(), money);
                num(row, c++, e.consideration(), money);
                text(row, c++, e.clientType());
                num(row, c++, e.markupPercent(), pct);
                num(row, c++, e.gross(), money);
                num(row, c++, e.tdsApplied() ? e.tds() : null, money);
                num(row, c++, e.gst(), money);
                num(row, c++, e.net(), money);
                num(row, c++, e.invoice(), money);
                text(row, c++, e.invoiceRaised() ? "YES" : "NO");
                text(row, c++, day(e.invoiceDate()));
                text(row, c++, day(e.paymentReceivedOn()));
                text(row, c++, switch (e.paymentStatus()) {
                    case PENDING -> "Pending";
                    case RECEIVED -> "Received";
                    case RECEIVED_GST_PENDING -> "Received (GST amount is pending)";
                });
                text(row, c, e.remarks());
                r++;
            }
            for (int i = 0; i < HEADERS.length; i++) s.setColumnWidth(i, (i == 9 || i == 11 || i == HEADERS.length - 1 ? 36 : 16) * 256);
            s.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String words(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String day(LocalDate d) {
        return d == null ? null : DAY.format(d);
    }

    private static void text(Row row, int col, String value) {
        if (value != null) row.createCell(col).setCellValue(value);
    }

    private static void num(Row row, int col, Object value, CellStyle style) {
        if (value == null) return;
        Cell c = row.createCell(col);
        c.setCellValue(value instanceof BigDecimal b ? b.doubleValue() : ((Number) value).doubleValue());
        if (style != null) c.setCellStyle(style);
    }
}
