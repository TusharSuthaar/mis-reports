package money.epsilon.brokerage.ingestion.parser;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Result of reading an income file. {@code errors} block the file; {@code warnings} are for information only.
 *
 * @param fileTotals the file's own "Total" row (null if it had none)
 * @param rowTotals  the sum of the client rows
 * @param netByMonth net per brokerage month, when the file says which month each row is for (Finesse); else empty
 */
public record ParsedIncomeFile(List<StagedIncomeRow> rows, Totals fileTotals, Totals rowTotals,
                               List<Problem> errors, List<Problem> warnings, Map<YearMonth, BigDecimal> netByMonth) {

    public ParsedIncomeFile(List<StagedIncomeRow> rows, Totals fileTotals, Totals rowTotals, List<Problem> errors, List<Problem> warnings) {
        this(rows, fileTotals, rowTotals, errors, warnings, Map.of());
    }

    public record Totals(BigDecimal gross, BigDecimal gst, BigDecimal net) {

        public static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        public Totals plus(StagedIncomeRow r) {
            return new Totals(gross.add(r.gross()), gst.add(r.gst()), net.add(r.net()));
        }
    }

    /** {@code row} is the Excel row number (0 = the whole file). */
    public record Problem(int row, String message) {
    }

    public boolean ok() {
        return errors.isEmpty();
    }
}
