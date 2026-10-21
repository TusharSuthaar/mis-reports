package money.epsilon.brokerage.pms.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.pms.domain.PmsInvestment;
import money.epsilon.brokerage.pms.service.PmsService.InvestmentView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** PMS investments in the PMS register's column order (revenue columns come later, R-85). */
final class PmsWorkbook {

    static final String[] HEADERS = {"SR.NO", "Month", "REGION", "RM NAME", "REF/SUB", "CLIENT NAME", "PAN NO", "MOBILE NO", "EMAIL ID",
            "ADDRESS", "PARTNER", "PMS", "INV AMT", "INVESTMENT DATE", "INVESTWELL ENTRY", "MATURITY DATE", "QTY", "PRICE",
            "CONSIDERATION INVESTMENT AMOUNT (RS)", "CLIENT TYPE", "CERTIFICATE STATUS", "INVESTMENT MAIL", "ENTRY STATUS", "MARK UP %"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    private PmsWorkbook() {
    }

    static byte[] build(List<InvestmentView> entries) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("PMS");
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
            pct.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);

            Row h = s.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
            }
            int r = 1;
            for (InvestmentView e : entries) {
                if (e.status() == PmsInvestment.Status.CANCELLED) continue;
                Row row = s.createRow(r);
                int c = 0;
                num(row, c++, r, null);
                text(row, c++, MONTH.format(e.month()), null);
                text(row, c++, e.region(), null);
                text(row, c++, e.rmName(), null);
                text(row, c++, e.partnerName(), null);
                text(row, c++, e.clientName(), null);
                text(row, c++, e.pan(), null);
                text(row, c++, e.mobile(), null);
                text(row, c++, e.email(), null);
                text(row, c++, e.address(), wrap);
                text(row, c++, e.amc(), null);
                text(row, c++, e.strategy(), null);
                num(row, c++, e.amount(), money);
                text(row, c++, DAY.format(e.investmentDate()), null);
                text(row, c++, e.onPortal() ? "UPDATED ON APP" : null, null);
                text(row, c++, maturity(e), wrap);
                num(row, c++, e.qty(), null);
                num(row, c++, e.price(), money);
                num(row, c++, e.consideration(), money);
                text(row, c++, e.clientType(), null);
                text(row, c++, words(e.certificate().name()), null);
                text(row, c++, e.investmentMail() ? "Sent" : null, null);
                text(row, c++, e.holdingStatus() == PmsInvestment.Holding.REDEEMED ? "Redeemed" : "Active", null);
                num(row, c, e.trailPercent() == null ? null : e.trailPercent().movePointLeft(2), pct);
                r++;
            }
            for (int i = 0; i < HEADERS.length; i++) s.setColumnWidth(i, i == 9 || i == 11 || i == 15 ? 32 * 256 : 15 * 256);
            s.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** As the register's MATURITY DATE column reads: redeemed, or how it pays, plus the remarks. */
    private static String maturity(InvestmentView e) {
        String what = e.holdingStatus() == PmsInvestment.Holding.REDEEMED ? "REDEEMED ON " + MONTH.format(e.redeemedOn()).toUpperCase(Locale.ENGLISH)
                : switch (e.payoutFrequency()) {
                    case MONTHLY -> "Payment will be done Monthly";
                    case QUARTERLY -> "Payment will be done Quarterly";
                    case NONE -> "No Payout";
                };
        return e.remarks() == null ? what : what + " - " + e.remarks();
    }

    private static String words(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
