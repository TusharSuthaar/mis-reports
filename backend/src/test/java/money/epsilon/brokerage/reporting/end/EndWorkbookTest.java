package money.epsilon.brokerage.reporting.end;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import money.epsilon.brokerage.reporting.end.EndWorkbook.BranchTotal;
import money.epsilon.brokerage.reporting.end.EndWorkbook.EndRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class EndWorkbookTest {

    private static final YearMonth SEP = YearMonth.of(2026, 9);

    private static BigDecimal d(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static EndRow row(String branch, String rm, String sb, String sbPct, String ref, String refPct, String client,
                              String trail, String gst, String net, String sbAmt, String refAmt, String remarks) {
        return new EndRow("BIHAR & EUP", "RM BETA", branch, rm, sb, d(sbPct), ref, d(refPct), null, client, "ABCDE****F",
                d(trail), d(gst), d(net), d(sbAmt), d(refAmt), BigDecimal.ZERO, remarks);
    }

    /** Net 100 split 50 / 10 / 40; net 50 split referral 20; net 10 direct; an adjustment of -5. */
    private static List<EndRow> rows() {
        return List.of(
                row("PATNA", "RM ALPHA", "KEFI", "0.5", "PARTNER TWO", "0.2", "ONE", "118", "18", "100", "50", "10", null),
                row("PATNA", "RM ALPHA", null, null, "PARTNER TWO", "0.4", "TWO", "59", "9", "50", null, "20", null),
                row("PATNA", "RM BETA", null, null, null, null, "THREE", "11.8", "1.8", "10", null, null, null),
                row("KOLKATA", "RM BETA_KO", "KEFI", "0.8", null, null, "FOUR", "-5.9", "-0.9", "-5", "-4", null, "Adjustment entry for Aug'26"));
    }

    private static XSSFWorkbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    @Test
    void hasTheEndSheetsAndDataColumnsInOrder() throws Exception {
        try (XSSFWorkbook wb = open(EndWorkbook.build(new EndWorkbook.Input(SEP, false, rows(), null, List.of())))) {
            assertThat(List.of(wb.getSheetName(0), wb.getSheetName(1), wb.getSheetName(2)))
                    .containsExactly("Sub Broker Summary", "Summary", "Data_Sep'26");
            Row h = wb.getSheet("Data_Sep'26").getRow(0);
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < EndWorkbook.DATA_HEADERS.length; c++) headers.add(h.getCell(c).getStringCellValue());
            assertThat(headers).containsExactly(EndWorkbook.DATA_HEADERS);
            assertThat(headers.get(0)).isEqualTo("Region");
            assertThat(headers.get(29)).isEqualTo("REMARKS");
            assertThat(headers.get(22)).isEqualTo("GIFT CITY");
            assertThat(wb.getSheet("Data_Sep'26").isColumnHidden(EndWorkbook.SB_SHARING)).isTrue();
            assertThat(wb.getSheet("Summary").getRow(0).getCell(0).getStringCellValue()).isEqualTo("Row Labels");
            Cell trail = wb.getSheet("Data_Sep'26").getRow(1).getCell(EndWorkbook.MF_TRAIL);
            assertThat(trail.getCellStyle().getDataFormatString()).contains("#,##0_").doesNotContain("0.00");
            assertThat(wb.getSheet("Data_Sep'26").getRow(2).getCell(6).getCellStyle().getDataFormatString()).isEqualTo("0%");
            assertThat(EndWorkbook.fileName(SEP)).isEqualTo("END_File_Sep_2026.xlsx");
        }
    }

    @Test
    void dataRowsCarryTheSplitAndBrokerType() throws Exception {
        try (XSSFWorkbook wb = open(EndWorkbook.build(new EndWorkbook.Input(SEP, false, rows(), null, List.of())))) {
            Sheet data = wb.getSheet("Data_Sep'26");
            Row one = data.getRow(1);
            assertThat(one.getCell(6).getStringCellValue()).isEqualTo("50% + 20%");
            assertThat(one.getCell(EndWorkbook.REF_SHARING).getNumericCellValue()).isEqualTo(60.0);
            assertThat(one.getCell(EndWorkbook.NET_REVENUE).getNumericCellValue()).isEqualTo(40.0);
            assertThat(one.getCell(28).getStringCellValue()).isEqualTo("Sub broker + Referral Partner");
            Row two = data.getRow(2);
            assertThat(two.getCell(6).getNumericCellValue()).isEqualTo(0.4);
            assertThat(two.getCell(28).getStringCellValue()).isEqualTo("Referral Partner");
            assertThat(data.getRow(3).getCell(28).getStringCellValue()).isEmpty();
            assertThat(data.getRow(4).getCell(29).getStringCellValue()).isEqualTo("Adjustment entry for Aug'26");
            assertThat(data.getRow(5).getCell(0).getStringCellValue()).isEqualTo("TOTAL");
        }
    }

    @Test
    void partnersWithNothingThisMonthAreLeftOutOfTheEnd() throws Exception {
        List<EndRow> rows = List.of(
                row("PATNA", "RM ALPHA", "KEFI", "0.5", null, null, "ONE", "118", "18", "100", "50", null, null),
                row("PATNA", "RM ALPHA", "ZERO SB", "0", "ZERO REF", "0", "TWO", "59", "9", "50", "0", "0", null));
        try (XSSFWorkbook wb = open(EndWorkbook.build(new EndWorkbook.Input(SEP, false, rows, null, List.of())))) {
            var data = wb.getSheet("Data_Sep'26");
            var two = java.util.stream.StreamSupport.stream(data.spliterator(), false)
                    .filter(r -> r.getCell(8) != null && "TWO".equals(r.getCell(8).getStringCellValue())).findFirst().orElseThrow();
            assertThat(two.getCell(4) == null || two.getCell(4).getStringCellValue().isEmpty()).as("sub-broker blanked").isTrue();
            assertThat(two.getCell(5) == null || two.getCell(5).getStringCellValue().isEmpty()).as("referral blanked").isTrue();
            assertThat(two.getCell(28) == null || two.getCell(28).getStringCellValue().isEmpty()).as("no broker type").isTrue();
            var summary = wb.getSheet("Sub Broker Summary");
            List<String> listed = new ArrayList<>();
            summary.forEach(r -> r.forEach(c -> { if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) listed.add(c.getStringCellValue()); }));
            assertThat(listed).contains("KEFI").doesNotContain("ZERO SB", "ZERO REF");
        }
    }

    @Test
    void regionSectionFollowsTheOrgOrderAndShowsBranchRowsAboveARegion() throws Exception {
        List<EndWorkbook.RegionLayout> layout = List.of(new EndWorkbook.RegionLayout("BIHAR & EUP", true), new EndWorkbook.RegionLayout("EMPTY LLP", false));
        try (XSSFWorkbook wb = open(EndWorkbook.build(new EndWorkbook.Input(SEP, false, rows(), null, List.of(), layout)))) {
            Sheet summary = wb.getSheet("Summary");
            List<String> labels = new java.util.ArrayList<>();
            for (int r = 2; r <= 6; r++) labels.add(summary.getRow(r).getCell(12).getStringCellValue());
            assertThat(labels).containsExactly("KOLKATA", "PATNA", "BIHAR & EUP", "EMPTY LLP", "TOTAL");
            double kolkata = summary.getRow(2).getCell(16).getNumericCellValue();
            double patna = summary.getRow(3).getCell(16).getNumericCellValue();
            double region = summary.getRow(4).getCell(16).getNumericCellValue();
            assertThat(kolkata + patna).isEqualTo(region, within(1e-9));
            assertThat(region * 100_000).isEqualTo(155.0, within(1e-6));
            // TOTAL counts the region once, not its branch rows again.
            assertThat(summary.getRow(6).getCell(16).getNumericCellValue()).isEqualTo(region, within(1e-9));
        }
    }

    @Test
    void formulaResultsAreStoredSoPreviewsShowNumbers() throws Exception {
        try (XSSFWorkbook wb = open(EndWorkbook.build(new EndWorkbook.Input(SEP, false, rows(), null, List.of())))) {
            Row total = wb.getSheet("Data_Sep'26").getRow(5);
            assertThat(total.getCell(EndWorkbook.TOTAL_REV).getNumericCellValue()).isEqualTo(155.0, within(1e-9));
            Row grand = find(wb.getSheet("Summary"), "Grand Total");
            assertThat(grand.getCell(1).getNumericCellValue()).isEqualTo(155.0, within(1e-9));
        }
    }

    @Test
    void everyFormulaWorksAndTheTotalsAgree() throws Exception {
        List<BranchTotal> previous = List.of(new BranchTotal("BIHAR & EUP", "PATNA", new BigDecimal("80"), new BigDecimal("50")));
        try (XSSFWorkbook wb = open(EndWorkbook.build(new EndWorkbook.Input(SEP, true, rows(), YearMonth.of(2026, 8), previous)))) {
            FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
            for (Sheet s : wb) {
                for (Row r : s) {
                    for (Cell c : r) {
                        if (c.getCellType() == CellType.FORMULA) {
                            CellValue v = eval.evaluate(c);
                            assertThat(v.getCellType()).as(s.getSheetName() + "!" + c.getAddress() + " " + c.getCellFormula()).isNotEqualTo(CellType.ERROR);
                        }
                    }
                }
            }
            Sheet data = wb.getSheet("Data_Sep'26");
            Row total = data.getRow(5);
            assertThat(eval.evaluate(total.getCell(EndWorkbook.TOTAL_REV)).getNumberValue()).isEqualTo(155.0, within(1e-9));
            assertThat(eval.evaluate(total.getCell(EndWorkbook.REF_SHARING)).getNumberValue()).isEqualTo(76.0, within(1e-9));

            Sheet summary = wb.getSheet("Summary");
            assertThat(findInColumn(summary, 12, "DRAFT — Sep'26 is not locked yet")).isNotNull();
            Row grand = find(summary, "Grand Total");
            assertThat(eval.evaluate(grand.getCell(1)).getNumberValue()).isEqualTo(155.0, within(1e-9));
            Row check = summary.getRow(grand.getRowNum() + 1);
            assertThat(eval.evaluate(check.getCell(1)).getNumberValue()).isEqualTo(0.0, within(1e-9));
            Row patna = find(summary, "PATNA");
            assertThat(eval.evaluate(patna.getCell(1)).getNumberValue()).isEqualTo(160.0, within(1e-9));

            Row sharing = findInColumn(summary, 8, "Sub Broker & Referral Partner Sharing");
            double categorySharing = eval.evaluate(sharing.getCell(9)).getNumberValue();
            assertThat(categorySharing).isEqualTo(76.0, within(1e-9));
            Sheet partners = wb.getSheet("Sub Broker Summary");
            assertThat(partners.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Sub broker");
            assertThat(partners.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Referral Partner");
            double sb = eval.evaluate(findInColumn(partners, 0, "Grand Total").getCell(1)).getNumberValue();
            double ref = eval.evaluate(findInColumn(partners, 3, "Grand Total").getCell(4)).getNumberValue();
            assertThat(sb).isEqualTo(46.0, within(1e-9));
            assertThat(sb + ref).isEqualTo(categorySharing, within(1e-9));

            Row gross = findInColumn(summary, 8, "Gross Revenue");
            assertThat(eval.evaluate(gross.getCell(9)).getNumberValue()).isEqualTo(155.0, within(1e-9));
            Row net = findInColumn(summary, 8, "Net Revenue");
            assertThat(eval.evaluate(net.getCell(9)).getNumberValue()).isEqualTo(79.0, within(1e-9));

            Row mom = findInColumn(summary, 12, "BIHAR & EUP");
            assertThat(eval.evaluate(mom.getCell(14)).getNumberValue()).isEqualTo(80.0 / 1e5, within(1e-12));
            assertThat(eval.evaluate(mom.getCell(16)).getNumberValue()).isEqualTo(155.0 / 1e5, within(1e-12));
            assertThat(eval.evaluate(mom.getCell(18)).getNumberValue()).isEqualTo(155.0 / 80 - 1, within(1e-9));
        }
    }

    private static Row find(Sheet s, String label) {
        return findInColumn(s, 0, label);
    }

    private static Row findInColumn(Sheet s, int col, String label) {
        for (Row r : s) {
            Cell c = r.getCell(col);
            if (c != null && c.getCellType() == CellType.STRING && c.getStringCellValue().equals(label)) return r;
        }
        throw new AssertionError(label + " not found in " + s.getSheetName());
    }
}
