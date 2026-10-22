package money.epsilon.brokerage.p2p.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.p2p.domain.P2pEntry;
import money.epsilon.brokerage.p2p.service.P2pService.P2pView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** P2P investments in the P2P register's column order, then the brokerage columns (R-87). */
final class P2pWorkbook {

    static final String[] HEADERS = {"SR.NO", "MONTH", "REGION", "RM NAME", "REF/SUB", "CLIENT NAME", "PAN NO", "MOBILE NO", "EMAIL ID",
            "ADDRESS", "PARTNER", "P2P SCHEME", "ROI", "AMOUNT INVESTED", "FUNDS INITIATED DATE", "START DATE", "QUARTERLY END DATE",
            "ENTRY STATUS", "PAYMENT STATUS", "REV SHARING STATUS", "Brokerage", "TDS", "GST", "Net Revenue", "Invoice Amt", "Amt Received Date",
            "Remarks"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    private P2pWorkbook() {
    }

    static byte[] build(List<P2pView> entries) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("P2P");
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
            for (P2pView e : entries) {
                if (e.status() == P2pEntry.Status.CANCELLED) continue;
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
                text(row, c++, e.platform(), null);
                text(row, c++, e.scheme(), null);
                num(row, c++, e.roiPercent() == null ? null : e.roiPercent().movePointLeft(2), pct);
                num(row, c++, e.amount(), money);
                text(row, c++, day(e.fundsInitiatedOn()), null);
                text(row, c++, day(e.startDate()), null);
                text(row, c++, day(e.quarterEndDate()), null);
                text(row, c++, e.onPortal() ? "UPDATED ON APP" : null, null);
                text(row, c++, e.paymentNote(), wrap);
                text(row, c++, e.revSharingStatus(), wrap);
                boolean revenue = e.gross().signum() > 0;
                num(row, c++, revenue ? e.gross() : null, money);
                num(row, c++, revenue && e.tdsApplied() ? e.tds() : null, money);
                num(row, c++, revenue ? e.gst() : null, money);
                num(row, c++, revenue ? e.net() : null, money);
                num(row, c++, revenue ? e.invoice() : null, money);
                text(row, c++, day(e.receivedOn()), null);
                text(row, c, e.remarks(), wrap);
                r++;
            }
            for (int i = 0; i < HEADERS.length; i++) s.setColumnWidth(i, i == 9 || i == 18 || i == HEADERS.length - 1 ? 30 * 256 : 15 * 256);
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
