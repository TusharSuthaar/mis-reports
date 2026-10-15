package money.epsilon.brokerage.ingestion;

import java.util.ArrayList;
import java.util.List;
import money.epsilon.brokerage.masterimport.TestWorkbook;

/** Builds Finesse-style exports for tests (same sheets and headers as the real ones, made-up values). */
public final class FinesseFiles {

    public static final String[] BROKERAGE_HEADER = {"Client Code", "Client Name", "Family Head Name", "Family Head Code", "RM", "SRM",
            "Location/Branch", "Brokerage Start period", "Brokerage End period", "Brokerage Amount", "Percentage", "Referred By"};
    public static final String[] MASTER_HEADER = {"Client Code", "Salutation", "Client Name", "Family Name", "Family Head Code",
            "Relationship with Family", "Family Head Name", "Family Head", "RM", "SRM", "Pan Number", "Email", "Guardian PAN"};

    private FinesseFiles() {
    }

    /** A valid, distinct PAN for each {@code n}: 5 letters from n, 4 digits from n, a final letter. */
    public static String pan(long n) {
        StringBuilder letters = new StringBuilder();
        long x = Math.abs(n);
        for (int i = 0; i < 5; i++) {
            letters.append((char) ('A' + x % 26));
            x /= 26;
        }
        return letters + String.format("%04d", Math.abs(n) % 10000) + (char) ('A' + Math.abs(n / 7) % 26);
    }

    /** MF Brokerage export, sheet "Client Wise Summary". */
    public static final class Brokerage {
        private final List<Object[]> rows = new ArrayList<>();
        private Object total;

        /** One brokerage row; {@code end} like "31-Aug-2026". */
        public Brokerage row(String code, String name, String start, String end, Object amount) {
            rows.add(new Object[]{code, name, name, code, "Suspense RM", null, "Suspense", start, end, amount, 0, null});
            return this;
        }

        public Brokerage total(Object amount) {
            total = amount;
            return this;
        }

        public byte[] bytes() {
            TestWorkbook wb = new TestWorkbook().sheet("MFBrokerageSummary", "AMC Wise", "Amount").row("Some AMC", 1)
                    .sheet("Client Wise Summary", BROKERAGE_HEADER).titleRows(1);
            rows.forEach(wb::row);
            if (total != null) wb.row("Total", null, null, null, null, null, null, null, null, total);
            return wb.bytes();
        }
    }

    /** Client master export, sheet "ClientMasterReport". */
    public static final class ClientMaster {
        private final List<Object[]> rows = new ArrayList<>();

        public ClientMaster client(String code, String name, String pan, String guardianPan) {
            rows.add(new Object[]{code, null, name, name, code, "Self", name, name, "Suspense RM", null, pan, null, guardianPan});
            return this;
        }

        public byte[] bytes() {
            TestWorkbook wb = new TestWorkbook().sheet("ClientMasterReport", MASTER_HEADER);
            rows.forEach(wb::row);
            return wb.bytes();
        }
    }
}
