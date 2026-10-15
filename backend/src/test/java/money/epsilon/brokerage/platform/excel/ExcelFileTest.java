package money.epsilon.brokerage.platform.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.masterimport.TestWorkbook;
import org.junit.jupiter.api.Test;

class ExcelFileTest {

    @Test
    void findsTheHeaderBelowTitleRowsAndReadsCellsByName() {
        byte[] f = new TestWorkbook().sheet("Data", "Code", "Client  name", "Amount", "Date", "Amount").titleRows(2)
                .row("A1", "ONE", 10.5, LocalDate.of(2026, 8, 31), 99)
                .row(null, null, null, null, null)
                .row("A2", "TWO", "1,234.50", "31-Aug-2026", null)
                .bytes();
        try (ExcelFile file = ExcelFile.open(f)) {
            ExcelFile.Table t = file.table("data", List.of("CODE", "Client Name", "amount"), 5).orElseThrow();
            assertThat(t.missing()).isEmpty();
            assertThat(t.title()).isEqualTo("Title line 0");
            assertThat(t.rows()).hasSize(2); // the blank row is left out
            ExcelRow one = t.rows().get(0);
            assertThat(one.text("client name")).isEqualTo("ONE");
            assertThat(one.amount("Amount")).isEqualByComparingTo("10.5"); // the first "Amount" column counts
            assertThat(one.date("Date")).isEqualTo(LocalDate.of(2026, 8, 31));
            ExcelRow two = t.rows().get(1);
            assertThat(two.amount("Amount")).isEqualByComparingTo(new BigDecimal("1234.50"));
            assertThat(two.date("Date")).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(two.rowNumber()).isEqualTo(6);
            assertThat(two.text("No such column")).isEmpty();
        }
    }

    @Test
    void namesMatchIgnoringPunctuationAndMissingColumnsAreListed() {
        byte[] f = new TestWorkbook().sheet("RM-Branch", "Emp-Code", "Name").row("E1", "X").bytes();
        try (ExcelFile file = ExcelFile.open(f)) {
            assertThat(file.table("RM Branch", List.of("Emp Code"), 3)).isPresent();
            assertThat(file.table("RM Branch", List.of("Emp Code", "Branch"), 3).orElseThrow().missing()).containsExactly("Branch");
            assertThat(file.table("Nope", List.of("Name"), 3)).isEmpty();
            assertThat(file.sheetNames()).containsExactly("RM-Branch");
        }
    }

    @Test
    void badCellsAndFilesSayWhereAndWhat() {
        byte[] f = new TestWorkbook().sheet("S", "Amount", "When").row("ten", "someday").bytes();
        try (ExcelFile file = ExcelFile.open(f)) {
            ExcelRow r = file.table(null, List.of("Amount"), 3).orElseThrow().rows().get(0);
            assertThatThrownBy(() -> r.amount("Amount")).hasMessageContaining("Row 2").hasMessageContaining("is not an amount");
            assertThatThrownBy(() -> r.date("When")).hasMessageContaining("Row 2").hasMessageContaining("is not a date");
        }
        assertThatThrownBy(() -> ExcelFile.open(new byte[]{1, 2, 3})).hasMessage(ExcelFile.UNREADABLE);
    }
}
