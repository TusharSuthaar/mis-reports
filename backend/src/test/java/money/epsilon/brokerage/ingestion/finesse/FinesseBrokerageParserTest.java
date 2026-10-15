package money.epsilon.brokerage.ingestion.finesse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile;
import money.epsilon.brokerage.ingestion.parser.StagedIncomeRow;
import org.junit.jupiter.api.Test;

class FinesseBrokerageParserTest {

    final FinesseBrokerageParser parser = new FinesseBrokerageParser();

    @Test
    void addsUpEachCodesRowsAndWorksOutGstAt18Percent() {
        ParsedIncomeFile f = parser.parse(new FinesseFiles.Brokerage()
                .row("FC00001", "ONE", "01-Aug-2026", "17-Aug-2026", 60.123456)
                .row("FC00001", "ONE", "18-Aug-2026", "31-Aug-2026", 39.876544)
                .row("FC00002", "TWO", "01-Jul-2026", "31-Jul-2026", "1,000.50")
                .total(1100.5)
                .bytes());

        assertThat(f.errors()).isEmpty();
        assertThat(f.rows()).extracting(StagedIncomeRow::clientCode).containsExactly("FC00001", "FC00002");
        StagedIncomeRow one = f.rows().get(0);
        assertThat(one.net()).isEqualByComparingTo("100.0000");
        assertThat(one.gst()).isEqualByComparingTo("18.0000");
        assertThat(one.gross()).isEqualByComparingTo("118.0000");
        assertThat(one.clientName()).isEqualTo("ONE");
        assertThat(f.fileTotals().net()).isEqualByComparingTo("1100.5000");
        assertThat(f.netByMonth()).containsOnlyKeys(YearMonth.of(2026, 8), YearMonth.of(2026, 7));
        assertThat(f.netByMonth().get(YearMonth.of(2026, 7))).isEqualByComparingTo("1000.5");
    }

    @Test
    void aTotalOffByRoundingIsANoteButOffByMoreIsAnError() {
        ParsedIncomeFile rounding = parser.parse(new FinesseFiles.Brokerage().row("FC1", "A", "01-Aug-2026", "31-Aug-2026", 10).total(10.04).bytes());
        assertThat(rounding.errors()).isEmpty();
        assertThat(rounding.warnings()).singleElement().satisfies(w -> assertThat(w.message()).contains("rounding"));

        ParsedIncomeFile wrong = parser.parse(new FinesseFiles.Brokerage().row("FC1", "A", "01-Aug-2026", "31-Aug-2026", 10).total(12).bytes());
        assertThat(wrong.errors()).singleElement().satisfies(e -> assertThat(e.message()).contains("Total row says 12.00"));

        ParsedIncomeFile none = parser.parse(new FinesseFiles.Brokerage().row("FC1", "A", "01-Aug-2026", "31-Aug-2026", 10).bytes());
        assertThat(none.errors()).singleElement().satisfies(e -> assertThat(e.message()).contains("No 'Total' row"));
    }

    @Test
    void explainsAWrongFileAndBadCells() {
        ParsedIncomeFile notFinesse = parser.parse(new money.epsilon.brokerage.masterimport.TestWorkbook().sheet("Sheet1", "A").row("x").bytes());
        assertThat(notFinesse.errors().get(0).message()).contains("Client Wise Summary");

        ParsedIncomeFile bad = parser.parse(new FinesseFiles.Brokerage()
                .row("FC1", "A", "01-Aug-2026", "Aug 2026", 10)
                .row("FC2", "B", "01-Aug-2026", "31-Aug-2026", "ten")
                .total(10).bytes());
        assertThat(bad.errors()).extracting(ParsedIncomeFile.Problem::message)
                .anyMatch(m -> m.contains("not a date")).anyMatch(m -> m.contains("not an amount"));
        assertThat(parser.parse(new byte[]{1, 2, 3}).errors().get(0).message()).contains("could not be read");
    }
}
