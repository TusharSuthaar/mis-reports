package money.epsilon.brokerage.loan.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.loan.domain.LoanEntry;
import money.epsilon.brokerage.loan.service.LoanService.LoanView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Loan payouts in the LOANS register's column order (as kept in Excel before the platform). */
final class LoanWorkbook {

    static final String[] HEADERS = {"SR .NO", "Month", "Region", "Referral Partner", "Rm Name", "CLIENT NAME", "Pan No", "Mobile No",
            "Email Id", "Address", "PARTNER", "LOAN TYPE", "Invt Date", "Loan Amt/Sactioned Amt", "Utilised Amt", "Rate of Interest",
            "Revenue sharing", "Without GST", "TDS", "GST", "Net Revenue", "Invoive Amnt", "Payment Status", "Amt Received Date", "Remarks"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    private LoanWorkbook() {
    }

    static byte[] build(List<LoanView> entries) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("LOANS");
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
            // As in the register: the open loans, then a "Closed" row and the closed ones (each numbered from 1).
            List<LoanView> active = entries.stream().filter(e -> e.status() != LoanEntry.Status.CANCELLED).toList();
            int r = rows(s, active.stream().filter(e -> !e.loanClosed()).toList(), 1, money, pct, wrap);
            List<LoanView> closed = active.stream().filter(LoanView::loanClosed).toList();
            if (!closed.isEmpty()) {
                CellStyle boldText = wb.createCellStyle();
                Font b = wb.createFont();
                b.setBold(true);
                boldText.setFont(b);
                text(s.createRow(r + 1), 0, "Closed", boldText);
                rows(s, closed, r + 2, money, pct, wrap);
            }
            for (int i = 0; i < HEADERS.length; i++) s.setColumnWidth(i, i == 9 || i >= HEADERS.length - 3 ? 36 * 256 : 15 * 256);
            s.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes {@code list} from row {@code first}, numbered 1, 2, …; @return the next free row. */
    private static int rows(Sheet s, List<LoanView> list, int first, CellStyle money, CellStyle pct, CellStyle wrap) {
        int r = first;
        int sr = 1;
        for (LoanView e : list) {
            Row row = s.createRow(r);
            int c = 0;
            num(row, c++, sr++, null);
            text(row, c++, MONTH.format(e.month()), null);
            text(row, c++, e.region(), null);
            text(row, c++, e.partnerName(), null);
            text(row, c++, e.rmName(), null);
            text(row, c++, e.clientName(), null);
            text(row, c++, e.pan(), null);
            text(row, c++, e.mobile(), null);
            text(row, c++, e.email(), null);
            text(row, c++, e.address(), wrap);
            text(row, c++, e.lender(), null);
            text(row, c++, e.loanType(), null);
            text(row, c++, day(e.disbursalDate()), null);
            num(row, c++, e.sanctionedAmount(), money);
            num(row, c++, e.utilisedAmount(), money);
            num(row, c++, e.ratePercent(), pct);
            num(row, c++, e.payoutPercent(), pct);
            num(row, c++, e.gross(), money);
            num(row, c++, e.tdsApplied() ? e.tds() : null, money);
            num(row, c++, e.gst(), money);
            num(row, c++, e.net(), money);
            num(row, c++, e.invoice(), money);
            text(row, c++, paymentStatus(e), wrap);
            text(row, c++, day(e.receivedOn()), null);
            text(row, c, remarks(e), wrap);
            r++;
        }
        return r;
    }

    /** As the register writes it: the note when there is one, else Loan Closed / CREDITED / Pending. */
    static String paymentStatus(LoanView e) {
        if (e.paymentNote() != null) return e.paymentNote();
        if (e.loanClosed()) return "Loan Closed";
        return e.paymentStatus() == LoanEntry.PaymentStatus.RECEIVED ? "CREDITED" : "Pending";
    }

    private static String remarks(LoanView e) {
        String trail = e.payoutKind() == LoanEntry.PayoutKind.TRAIL ? "Trail payout" + (e.payoutOn() == LoanEntry.PayoutOn.UTILISED ? " on utilised amt" : "") : null;
        if (trail == null) return e.remarks();
        return e.remarks() == null ? trail : trail + " - " + e.remarks();
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
