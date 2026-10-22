package money.epsilon.brokerage.equity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import money.epsilon.brokerage.masterimport.TestWorkbook;

/** Builds a broker "Brokerage Due" report like SMC's for tests: a pivot sheet first, then the trades under title rows. */
public final class EquityFiles {

    static final String[] HEADER = {"Franchise", "Franchise Name", "Party", "PAN", "Name", "Date", "Exchange", "Gross Recd", "Level 1", "Level 2",
            "Level 3", "Level 4", "Franchise", "Area", "Other"};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final List<Object[]> rows = new ArrayList<>();

    /** One trade: Epsilon's franchise share of the gross brokerage. */
    public EquityFiles trade(String party, String pan, String name, LocalDate date, String exchange, double gross, double share) {
        rows.add(new Object[]{"SNO0001", "EPSILON MONEY MART PRIVATE LTD .", party, pan, name, DAY.format(date), exchange, gross, 0, 0, 0, 0, share, "MUMFRT", "MART"});
        return this;
    }

    public byte[] bytes() {
        TestWorkbook wb = new TestWorkbook().sheet("Sheet2", "Sum of Franchise2").row("PAN", null, "Total")
                .sheet("Sheet1", HEADER).titleRows(4)
                .row(null, null, null, null, null, null, null, "Gross Brokerage", "Share", "Share", "Share", "Share", "Share");
        rows.forEach(wb::row);
        wb.row("Total", null, null, null, null, null, null, rows.stream().mapToDouble(r -> (double) r[7]).sum());
        return wb.bytes();
    }
}
