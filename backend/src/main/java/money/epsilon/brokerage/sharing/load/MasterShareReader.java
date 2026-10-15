package money.epsilon.brokerage.sharing.load;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import money.epsilon.brokerage.platform.excel.ExcelFile;
import money.epsilon.brokerage.platform.excel.ExcelRow;
import money.epsilon.brokerage.platform.text.Text;

/**
 * Reads the client master (Master_MIS.xlsx, sheet "Master"): per client its name, PAN and up to two partners, each as
 * BROKER | TYPE | % (the % a fraction of net, e.g. 0.8). The second partner, when there is one, is in
 * BROKER 2 | TYPE 2 | % 2. Columns are found by name; where a name repeats (BROKER), the first one counts.
 */
final class MasterShareReader {

    static final String SHEET = "Master";
    static final String NAME = "CLIENT NAME";
    static final String PAN = "PAN NO";
    /** Optional: the client's RM as Master MIS names it. */
    static final String RM = "RM";
    /** Optional: tells apart RMs with the same name (one person with a book in two branches). */
    static final String BRANCH = "BRANCH";
    static final List<String> REQUIRED = List.of(NAME, PAN, "BROKER", "TYPE", "%");
    /** Partner column groups, in the order they are read. The second group is optional. */
    static final List<List<String>> PARTNER_GROUPS = List.of(List.of("BROKER", "TYPE", "%"), List.of("BROKER 2", "TYPE 2", "% 2"));
    private static final int HEADER_SEARCH_ROWS = 20;

    private MasterShareReader() {
    }

    /** One partner on a row. {@code pct} is a fraction, null when blank / "NA". */
    record PartnerCell(String partner, String type, BigDecimal pct, String pctProblem) {
    }

    /** {@code partners} holds only the groups that name a partner; {@code rm} is "" when the column is absent or blank. */
    record MasterRow(int rowNumber, String clientName, String pan, List<PartnerCell> partners, String rm, String branch) {

        MasterRow(int rowNumber, String clientName, String pan, List<PartnerCell> partners) {
            this(rowNumber, clientName, pan, partners, "", "");
        }
    }

    record Result(List<MasterRow> rows, String error) {
    }

    static Result read(byte[] content) {
        ExcelFile.Table table;
        try (ExcelFile file = ExcelFile.open(content)) {
            var found = file.table(SHEET, REQUIRED, HEADER_SEARCH_ROWS);
            if (found.isEmpty() || !found.get().missing().isEmpty()) found = file.table(null, REQUIRED, HEADER_SEARCH_ROWS);
            table = found.orElse(null);
        } catch (IllegalArgumentException e) {
            return new Result(List.of(), ExcelFile.UNREADABLE);
        }
        if (table == null || !table.missing().isEmpty()) {
            return new Result(List.of(), "Header row not found. Expected columns: " + String.join(", ", REQUIRED));
        }
        List<List<String>> groups = PARTNER_GROUPS.stream().filter(g -> g.stream().allMatch(table::has)).toList();

        List<MasterRow> rows = new ArrayList<>();
        for (ExcelRow row : table.rows()) {
            String name = clean(row.text(NAME));
            String pan = clean(row.text(PAN));
            if (name.isEmpty() && pan.isEmpty()) continue;
            List<PartnerCell> partners = new ArrayList<>();
            for (List<String> g : groups) {
                String partner = clean(row.text(g.get(0)));
                if (partner.isEmpty()) continue;
                partners.add(cell(partner, clean(row.text(g.get(1))), row.value(g.get(2))));
            }
            rows.add(new MasterRow(row.rowNumber(), name, pan, partners, clean(row.text(RM)), clean(row.text(BRANCH))));
        }
        return new Result(rows, null);
    }

    /** A % cell: a number is a fraction (0.8); text may be "0.8", "NA" or blank. */
    private static PartnerCell cell(String partner, String type, Object pctCell) {
        String problem = null;
        BigDecimal pct = null;
        if (pctCell instanceof BigDecimal d) {
            pct = d;
        } else if (pctCell != null) {
            String s = pctCell.toString().trim().replace("%", "");
            if (!s.isEmpty() && !s.equalsIgnoreCase("NA")) {
                try {
                    pct = new BigDecimal(s);
                } catch (NumberFormatException e) {
                    problem = "'" + s + "' is not a %";
                }
            }
        }
        if (pct != null && (pct.signum() < 0 || pct.compareTo(BigDecimal.ONE) > 0)) {
            problem = "% must be a fraction between 0 and 1 (e.g. 0.8), not " + pct.toPlainString();
            pct = null;
        }
        return new PartnerCell(partner, type, pct, problem);
    }

    private static String clean(String s) {
        String t = Text.clean(s);
        return t == null ? "" : t;
    }
}
