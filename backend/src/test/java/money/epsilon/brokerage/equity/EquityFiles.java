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

    /** The same report in the layout without PANs (SMC, Sep 2026): franchise name first, the client's name after Party. */
    static final String[] HEADER_NO_PAN = {"Area", "Other", "Franchise", "Name", "Party", "Name", "Date", "Gross Brokerage", "Level 1", "Level 2",
            "Level 3", "Level 4", "Franchise"};
    private final List<Object[]> noPanRows = new ArrayList<>();

    public EquityFiles tradeWithoutPan(String party, String name, LocalDate date, double gross, double share) {
        noPanRows.add(new Object[]{"MUMFRT", "MART", "SNO0001", "EPSILON MONEY MART", party, name, DAY.format(date), gross, 0, 0, 0, 0, share});
        return this;
    }

    private final List<Object[]> summary = new ArrayList<>();

    /** A row of the summary (pivot) sheet: party code, name, total, and the PAN in a column without a header. */
    public EquityFiles summaryPan(String party, String name, String pan) {
        summary.add(new Object[]{party, name, 0, pan});
        return this;
    }

    public byte[] bytesWithoutPan() {
        TestWorkbook wb = new TestWorkbook().sheet("Sheet2", "Sum of Franchise2").row("Party", "Name2", "Total");
        summary.forEach(wb::row);
        wb.sheet("Sheet1", HEADER_NO_PAN).titleRows(3)
                .row(null, null, null, null, null, null, null, "+ Min.Chrg.", "Share", "Share", "Share", "Share", "Share");
        noPanRows.forEach(wb::row);
        wb.row("Total", null, null, null, null, null, null, noPanRows.stream().mapToDouble(r -> (double) r[7]).sum());
        return wb.bytes();
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
