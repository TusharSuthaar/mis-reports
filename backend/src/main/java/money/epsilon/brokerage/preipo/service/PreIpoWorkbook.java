package money.epsilon.brokerage.preipo.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.preipo.domain.PreIpoEntry;
import money.epsilon.brokerage.preipo.service.PreIpoService.PreIpoView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Pre-IPO deals in the Pre-IPO register's column order: one sheet of buys, one of buybacks (as kept in Excel before). */
final class PreIpoWorkbook {

    static final String[] BUY = {"SR.NO", "Month", "Branch", "Rm name", "Referral / Sub-broker", "Client name", "PAN NO", "Client Type",
            "Mobile no", "Email id", "Pre-ipo name", "Partner", "ISIN", "Dp name", "DP ID", "Client ID", "Invt Date", "Quantity",
            "Price offered to client", "Investment Amount (Rs)", "Base Price", "Mark up", "Markup in %", "Gross Brk", "Without GST", "TDS",
            "GST", "Net Revenue", "Invoive Amnt", "Transaction Type", "Revenue sharing with Ref/Sub-broker", "EPSILON PORTAL", "Billing Month",
            "DealSheet Status", "REMARK"};
    static final String[] BUYBACK = {"Sr.no", "Month", "BRANCH", "Rm Name", "REF/SUB", "CLIENT NAME", "Pan No", "Client Type", "Mobile No",
            "Email Id", "PRE-IPO NAME", "PARTNER", "ISIN", "DP NAME", "DP ID", "Client ID", "TRANSACTION DATE", "Quantity", "Client Price",
            "Investment Amount (Rs)", " Price to us", "Mark up", "Markup in %", "Gross Brk", "Without GST", "TDS", "GST", "Net Revenue",
            "Invoive Amnt", "Transaction Type", "Revenue sharing with Ref/Sub-broker", "EPSILON PORTAL", "Billing Month", "Deal sheet", "REMARK"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);

    private PreIpoWorkbook() {
    }

    static byte[] build(List<PreIpoView> entries) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
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
            sheet(wb, "Pre IPO Purchase", BUY, entries, PreIpoEntry.Kind.BUY, header, money, pct);
            sheet(wb, "Pre Ipo Buyback", BUYBACK, entries, PreIpoEntry.Kind.BUYBACK, header, money, pct);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sheet(XSSFWorkbook wb, String name, String[] headers, List<PreIpoView> entries, PreIpoEntry.Kind kind,
                              CellStyle header, CellStyle money, CellStyle pct) {
        Sheet s = wb.createSheet(name);
        Row h = s.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(header);
        }
        int r = 1;
        for (PreIpoView e : entries) {
            if (e.status() == PreIpoEntry.Status.CANCELLED || e.kind() != kind) continue;
            Row row = s.createRow(r);
            int c = 0;
            num(row, c++, r, null);
            text(row, c++, MONTH.format(e.month()));
            text(row, c++, e.branchName());
            text(row, c++, e.rmName());
            text(row, c++, e.partnerName());
            text(row, c++, e.clientName());
            text(row, c++, e.pan());
            text(row, c++, e.clientType());
            text(row, c++, e.mobile());
            text(row, c++, e.email());
            text(row, c++, e.scripName());
            text(row, c++, e.vendorName());
            text(row, c++, e.isin());
            text(row, c++, e.dpName());
            text(row, c++, e.dpId());
            text(row, c++, e.dematClientId());
            text(row, c++, day(e.dealDate()));
            num(row, c++, e.quantity(), null);
            num(row, c++, e.clientPrice(), money);
            num(row, c++, e.amount(), money);
            num(row, c++, e.vendorPrice(), money);
            num(row, c++, e.markupPerShare(), money);
            num(row, c++, e.markupPercent(), pct);
            num(row, c++, e.brokerage(), money);
            num(row, c++, e.gross(), money);
            num(row, c++, e.tdsApplied() ? e.tds() : null, money);
            num(row, c++, e.gst(), money);
            num(row, c++, e.net(), money);
            num(row, c++, e.invoice(), money);
            text(row, c++, kind == PreIpoEntry.Kind.BUY ? "BUY" : "SELL");
            text(row, c++, e.sharingPercent() == null ? null : e.sharingPercent().stripTrailingZeros().toPlainString() + "%");
            text(row, c++, words(e.portalStatus().name()));
            text(row, c++, words(e.billingStatus().name()));
            text(row, c++, words(e.dealSheet().name()));
            text(row, c, e.remarks());
            r++;
        }
        for (int i = 0; i < headers.length; i++) s.setColumnWidth(i, (i == 5 || i == 10 ? 30 : 15) * 256);
        s.createFreezePane(0, 1);
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
