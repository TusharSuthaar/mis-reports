package money.epsilon.brokerage.aif.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.aif.domain.AifEntry;
import money.epsilon.brokerage.aif.service.AifService.AifView;
import money.epsilon.brokerage.aif.service.AifService.InstalmentView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** AIF commitments in the AIF register's column order (as kept in Excel before the platform). */
final class AifWorkbook {

    static final String[] HEADERS = {"SR.NO", "Month", "REGION", "RM NAME", "REF/SUB", "Revenue Sharing", "CLIENT NAME", "PAN NO", "MOBILE NO",
            "EMAIL ID", "ADDRESS", "PARTNER", "AIF", "COMMITMENT", "1st Inst", "Date of Payment", "Date of Agreement Signing", "2nd Inst",
            "Date of Payment", "Further instalments", "MATURITY / TENURE", "SOA Received", "Application Form", "QTY", "PRICE",
            "CONSIDERATION INVESTMENT AMOUNT (RS)", "CLIENT TYPE", "CERTIFICATE STATUS", "INVESTMENT MAIL", "ENTRY STATUS", "PAYOUT TERMS",
            "MARK UP %", "Gross Revenue", "TDS (Where ever applicable)", "GST (Where ever applicable)", "Net Revenue (in RS) - TDS",
            "Invoice Amount", "Invoice Raised by Accts", "PAYMENT STATUS", "REV SHARING STATUS", "Remarks"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    private AifWorkbook() {
    }

    static byte[] build(List<AifView> entries) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("AIF");
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
            Row h = s.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
            }
            int r = 1;
            for (AifView e : entries) {
                if (e.status() == AifEntry.Status.CANCELLED) continue;
                Row row = s.createRow(r);
                List<InstalmentView> inst = e.instalments();
                InstalmentView first = inst.size() > 0 ? inst.get(0) : null, second = inst.size() > 1 ? inst.get(1) : null;
                int c = 0;
                num(row, c++, r, null);
                text(row, c++, MONTH.format(e.month()));
                text(row, c++, e.region());
                text(row, c++, e.rmName());
                text(row, c++, e.partnerName());
                text(row, c++, e.sharingPercent() == null ? null : e.sharingPercent().stripTrailingZeros().toPlainString() + "%");
                text(row, c++, e.clientName());
                text(row, c++, e.pan());
                text(row, c++, e.mobile());
                text(row, c++, e.email());
                text(row, c++, e.address());
                text(row, c++, e.platformName());
                text(row, c++, e.fundName());
                num(row, c++, e.commitment(), money);
                num(row, c++, first == null ? null : first.amount(), money);
                text(row, c++, first == null ? null : day(first.paidOn()));
                text(row, c++, day(e.agreementDate()));
                num(row, c++, second == null ? null : second.amount(), money);
                text(row, c++, second == null ? null : day(second.paidOn()));
                text(row, c++, inst.size() > 2 ? String.join("; ", inst.subList(2, inst.size()).stream()
                        .map(i -> i.amount().stripTrailingZeros().toPlainString() + (i.paidOn() == null ? "" : " on " + day(i.paidOn()))).toList()) : null);
                text(row, c++, e.maturityDate() != null ? day(e.maturityDate()) : e.tenure());
                text(row, c++, e.soaReceived() ? "YES" : "NO");
                text(row, c++, e.applicationForm() ? "YES" : "NO");
                num(row, c++, e.quantity(), null);
                num(row, c++, e.price(), money);
                num(row, c++, e.called().signum() == 0 ? null : e.called(), money);
                text(row, c++, e.clientType());
                text(row, c++, e.certificate().name().replace('_', ' '));
                text(row, c++, e.investmentMail() ? "YES" : "NO");
                text(row, c++, e.onInvestwell() ? "Updated on Investwell" : null);
                text(row, c++, e.payoutTerms());
                num(row, c++, e.markupPercent(), null);
                num(row, c++, e.gross(), money);
                num(row, c++, e.tdsApplied() ? e.tds() : null, money);
                num(row, c++, e.gst(), money);
                num(row, c++, e.net(), money);
                num(row, c++, e.invoice(), money);
                text(row, c++, e.invoiceRaised() ? "YES" + (e.invoiceDate() == null ? "" : " (" + day(e.invoiceDate()) + ")") : "NO");
                text(row, c++, switch (e.paymentStatus()) {
                    case PENDING -> "Pending";
                    case RECEIVED -> "Received" + (e.paymentReceivedOn() == null ? "" : " " + day(e.paymentReceivedOn()));
                    case RECEIVED_GST_PENDING -> "Received (GST amount is pending)";
                });
                text(row, c++, e.partnerId() == null ? null : e.partnerPaid() ? "Paid" : "Pending");
                text(row, c, e.remarks());
                r++;
            }
            for (int i = 0; i < HEADERS.length; i++) s.setColumnWidth(i, (i == 10 || i == HEADERS.length - 1 ? 36 : 15) * 256);
            s.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
