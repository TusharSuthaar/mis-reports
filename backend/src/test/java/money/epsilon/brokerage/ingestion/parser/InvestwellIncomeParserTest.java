package money.epsilon.brokerage.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import money.epsilon.brokerage.ingestion.IncomeWorkbook;
import money.epsilon.brokerage.masterimport.TestWorkbook;
import org.junit.jupiter.api.Test;

class InvestwellIncomeParserTest {

    private final InvestwellIncomeParser parser = new InvestwellIncomeParser();

    @Test
    void readsRowsBelowTitleLinesKeepsFourDecimalsAndStopsAtTotal() {
        byte[] file = new IncomeWorkbook()
                .client("ANITA RAO", 80.6417, "PQRST6789K", "", "RM EPSILON", "", 320066313, 12.3014, 68.3403)
                .client("NO INCOME", "", "", "320066313", "STAFF", "SAMPLE LLP", 349145452, "", "")
                .total(80.6417, 12.3014, 68.3403)
                .bytes();

        ParsedIncomeFile parsed = parser.parse(file);
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).hasSize(2);
        StagedIncomeRow first = parsed.rows().get(0);
        assertThat(first.rowNumber()).isEqualTo(5);
        assertThat(first.clientCode()).isEqualTo("320066313");
        assertThat(first.clientCode2()).isEqualTo("c2-320066313");
        assertThat(first.gross()).isEqualByComparingTo("80.6417");
        assertThat(first.net()).isEqualTo(new BigDecimal("68.3403"));
        assertThat(first.rawRm()).isEqualTo("RM EPSILON");

        StagedIncomeRow blank = parsed.rows().get(1);
        assertThat(blank.gross()).isZero();
        assertThat(blank.net()).isZero();
        assertThat(blank.familyHeadCode()).isEqualTo("320066313");
        assertThat(blank.rawPartner()).isEqualTo("SAMPLE LLP");
        assertThat(parsed.fileTotals().net()).isEqualByComparingTo(parsed.rowTotals().net());
    }

    @Test
    void rowsThatDoNotAddUpToTheTotalFailTheFile() {
        byte[] file = new IncomeWorkbook()
                .client("A", 118, "", "", "RM", "", 1, 18, 100)
                .total(118, 18, 100.01)
                .bytes();
        ParsedIncomeFile parsed = parser.parse(file);
        assertThat(parsed.ok()).isFalse();
        assertThat(parsed.errors().get(0).message()).contains("do not add up");
    }

    @Test
    void rowErrorsAndWarnings() {
        byte[] file = new IncomeWorkbook()
                .client("NO CODE", 118, "", "", "RM", "", "", 18, 100)
                .client("BAD NET", 118, "", "", "RM", "", 2, 18, 99)
                .client("ODD GST", 118, "", "", "RM", "", 3, 20, 98)
                .client("DUP", 0, "", "", "RM", "", 3, 0, 0)
                .total(354, 56, 297)
                .bytes();
        ParsedIncomeFile parsed = parser.parse(file);
        assertThat(parsed.errors()).extracting(ParsedIncomeFile.Problem::message)
                .anyMatch(m -> m.contains("CODE is missing"))
                .anyMatch(m -> m.contains("≠ net"))
                .anyMatch(m -> m.contains("more than once"));
        assertThat(parsed.warnings()).extracting(ParsedIncomeFile.Problem::row).containsExactly(7);
    }

    @Test
    void missingTotalRowOrHeaderIsReported() {
        assertThat(parser.parse(new IncomeWorkbook().client("A", 0, "", "", "RM", "", 1, 0, 0).bytes()).errors())
                .extracting(ParsedIncomeFile.Problem::message).anyMatch(m -> m.contains("No 'Total' row"));
        assertThat(parser.parse(new TestWorkbook().sheet("x", "CLIENT", "AMOUNT").row("A", 1).bytes()).errors())
                .extracting(ParsedIncomeFile.Problem::message).anyMatch(m -> m.contains("Header row not found"));
        assertThat(parser.parse("not excel".getBytes()).ok()).isFalse();
    }
}
