package money.epsilon.brokerage.masterimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WorkbookReaderTest {

    @Test
    void findsTheHeaderByNameBelowTitleRowsAndInAnyColumnOrder() {
        byte[] file = new TestWorkbook()
                .sheet("rm branch", "From", "Notes", "BRANCH", "emp code").titleRows(2)
                .row(LocalDate.of(2026, 4, 1), "x", "Kolkata", 1001)
                .bytes();

        var sheets = WorkbookReader.read(file);
        var data = sheets.get(SheetSpec.RM_BRANCH);
        assertThat(data.missingColumns()).isEmpty();
        assertThat(data.rows()).hasSize(1);
        var row = data.rows().get(0);
        assertThat(row.rowNumber()).isEqualTo(4);
        assertThat(row.get("Emp Code")).isEqualTo("1001");
        assertThat(row.get("Branch")).isEqualTo("Kolkata");
        assertThat(row.date("From")).contains(LocalDate.of(2026, 4, 1));
    }

    @Test
    void acceptsCommonTextDateFormats() {
        assertThat(money.epsilon.brokerage.platform.excel.ExcelRow.parseDate("2026-04-01", "From")).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(money.epsilon.brokerage.platform.excel.ExcelRow.parseDate("01-04-2026", "From")).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(money.epsilon.brokerage.platform.excel.ExcelRow.parseDate("1/4/2026", "From")).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(money.epsilon.brokerage.platform.excel.ExcelRow.parseDate("01-Apr-2026", "From")).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(money.epsilon.brokerage.platform.excel.ExcelRow.parseDate("01-APR-26", "From")).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThatThrownBy(() -> money.epsilon.brokerage.platform.excel.ExcelRow.parseDate("April first", "From"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("From");
    }

    @Test
    void skipsBlankRowsAndIgnoresUnknownSheets() {
        byte[] file = new TestWorkbook()
                .sheet("Regions", "Region").row("EAST").row((Object) null).row("WEST")
                .sheet("Something else", "A").row("1")
                .bytes();
        var sheets = WorkbookReader.read(file);
        assertThat(sheets).containsOnlyKeys(SheetSpec.REGIONS);
        assertThat(sheets.get(SheetSpec.REGIONS).rows()).extracting(r -> r.get("Region")).containsExactly("EAST", "WEST");
    }

    @Test
    void reportsMissingRequiredColumns() {
        byte[] file = new TestWorkbook().sheet("Employees", "Emp Code", "Type").row("E1", "RM").bytes();
        assertThat(WorkbookReader.read(file).get(SheetSpec.EMPLOYEES).missingColumns()).containsExactly("Emp Code", "Name");
    }

    @Test
    void rejectsSomethingThatIsNotAWorkbook() {
        assertThatThrownBy(() -> WorkbookReader.read("not excel".getBytes())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void templateHasEverySheetWithItsHeaders() {
        var sheets = WorkbookReader.read(MasterTemplate.build());
        assertThat(sheets.keySet()).containsExactlyElementsOf(SheetSpec.ALL);
        sheets.values().forEach(s -> {
            assertThat(s.missingColumns()).isEmpty();
            assertThat(s.rows()).isEmpty();
        });
    }
}
