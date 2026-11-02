package money.epsilon.brokerage.insurance.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy;
import money.epsilon.brokerage.insurance.service.InsuranceService.PolicyView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Insurance policies in the column order of the insurance team's "Policy level data" sheet (R-94), so the export can be
 * uploaded again. Percentages are fractions with a % format, as in the sheet.
 */
final class InsuranceWorkbook {

    static final String[] HEADERS = {"Business Month", "Policy No", "POSP Name", "Insurance Company", "CAT", "Client", "PAN", "RM", "Branch",
            "Insurance Sub Type", "Product Name", "PPT", "Policy Term (PT)", "Sum Assured", "OD Premium", "TP Premium", "Total Premium",
            "Net Premium", "Business Type", "% Expect Comm.", "Actual Comm.%", "Diff in Comm.%", "Expect Comm.", "Actual Comm.",
            "Diff in Comm.", "Agreed % sharing with POSP", "% POSP Ideal Commission", "POSP Ideal Commission", "Remarks", "Region", "Client code"};

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM''yy", Locale.ENGLISH);

    private InsuranceWorkbook() {
    }

    static byte[] build(List<PolicyView> list) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet(InsuranceService.SHEET);
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

            Row h = s.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
            }
            int r = 1;
            for (PolicyView p : list) {
                if (p.status() == InsurancePolicy.Status.CANCELLED) continue;
                Row row = s.createRow(r++);
                int c = 0;
                text(row, c++, p.month().atDay(1).format(MONTH));
                text(row, c++, p.policyNo());
                text(row, c++, p.pospName() == null ? "NA" : p.pospName());
                text(row, c++, p.insurer());
                text(row, c++, title(p.category().name()));
                text(row, c++, p.clientName());
                text(row, c++, p.pan());
                text(row, c++, p.rmName());
                text(row, c++, p.branchName());
                text(row, c++, p.subType());
                text(row, c++, p.productName());
                text(row, c++, p.ppt());
                number(row, c++, p.policyTerm() == null ? null : BigDecimal.valueOf(p.policyTerm()), null);
                number(row, c++, p.sumAssured(), money);
                number(row, c++, p.odPremium(), money);
                number(row, c++, p.tpPremium(), money);
                number(row, c++, p.totalPremium(), money);
                number(row, c++, p.netPremium(), money);
                text(row, c++, p.businessType() == InsurancePolicy.BusinessType.FYCI ? "FYCI" : title(p.businessType().name()));
                number(row, c++, fraction(p.expectedPercent()), pct);
                number(row, c++, fraction(p.actualPercent()), pct);
                number(row, c++, fraction(p.diffPercent()), pct);
                number(row, c++, p.expectedComm(), money);
                number(row, c++, p.actualComm(), money);
                number(row, c++, p.diffComm(), money);
                number(row, c++, fraction(p.pospShare()), pct);
                number(row, c++, fraction(p.pospPercent()), pct);
                number(row, c++, p.pospComm(), money);
                text(row, c++, p.remarks());
                text(row, c++, p.region());
                text(row, c, p.clientCode());
            }
            for (int i = 0; i < HEADERS.length; i++) s.setColumnWidth(i, 16 * 256);
            s.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String title(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static BigDecimal fraction(BigDecimal percent) {
        return percent == null ? null : percent.movePointLeft(2);
    }

    private static void text(Row row, int col, String value) {
        if (value != null) row.createCell(col).setCellValue(value);
    }

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        if (value == null) return;
        Cell c = row.createCell(col);
        c.setCellValue(value.doubleValue());
        if (style != null) c.setCellStyle(style);
    }
}
