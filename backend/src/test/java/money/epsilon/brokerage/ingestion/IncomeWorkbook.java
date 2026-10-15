package money.epsilon.brokerage.ingestion;

import java.util.ArrayList;
import java.util.List;
import money.epsilon.brokerage.masterimport.TestWorkbook;

/** Builds an Investwell-style Income Report for tests: title rows, the real header, client rows, a Total row. */
public final class IncomeWorkbook {

    public static final String[] HEADER = {"CLIENT", "INCOME AMOUNT", "PAN", "EQUITY CODE1", "SERVICE RM", "SERVICE RM2",
            "SERVICE RM3", "FAMILY HEAD IWELL CODE", "FAMILY HEAD IWELL CODE 2", "RELATIONSHIP MANAGER", "SUB BROKER",
            "FAMILY HEAD", "CLIENT IWELL CODE", "CLIENT IWELL CODE 2", "RM INCENTIVE", "GST", "NET BROKERAGE AMOUNT",
            "PAYOUT AMOUNT", "RETENTION"};

    private final List<Object[]> rows = new ArrayList<>();
    private Object[] total;

    /** gross/gst/net may be numbers or "" (blank). */
    public IncomeWorkbook client(String name, Object gross, String pan, String familyHeadCode, String rm, String subBroker,
                                 Object code, Object gst, Object net) {
        rows.add(new Object[]{name, gross, pan, "", "", "", "", familyHeadCode, "", rm, subBroker, "", code, "c2-" + code, 0, gst, net, "", net});
        return this;
    }

    public IncomeWorkbook total(Object gross, Object gst, Object net) {
        total = new Object[]{"Total", gross, "", "", "", "", "", "", "", "", "", "", "", "", 0, gst, net, "", net};
        return this;
    }

    public byte[] bytes() {
        TestWorkbook wb = new TestWorkbook().sheet("1. Income Report", HEADER).titleRows(3);
        rows.forEach(wb::row);
        if (total != null) wb.row(total);
        return wb.bytes();
    }
}
