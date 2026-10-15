package money.epsilon.brokerage.sharing.load;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import money.epsilon.brokerage.platform.text.Text;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Reads the client master (Master_MIS.xlsx, sheet "Master"): per client its name, PAN and up to two partners, each as
 * BROKER | TYPE | % (the % a fraction of net, e.g. 0.8). The second partner, when there is one, is in
 * BROKER 2 | TYPE 2 | % 2. Columns are found by name; where a name repeats (BROKER), the first one counts.
 */
final class MasterShareReader {

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
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter fmt = new DataFormatter(Locale.ENGLISH);
            Sheet sheet = wb.getSheet("Master") != null ? wb.getSheet("Master") : wb.getSheetAt(0);
            Map<String, Integer> col = null;
            int headerRow = -1;
            for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS); r++) {
                Map<String, Integer> found = headers(sheet.getRow(r), fmt, eval);
                if (found.keySet().containsAll(REQUIRED)) {
                    col = found;
                    headerRow = r;
                    break;
                }
            }
            if (col == null) return new Result(List.of(), "Header row not found. Expected columns: " + String.join(", ", REQUIRED));
            List<List<Integer>> groups = new ArrayList<>();
            for (List<String> g : PARTNER_GROUPS) {
                if (col.keySet().containsAll(g)) groups.add(g.stream().map(col::get).toList());
            }

            List<MasterRow> rows = new ArrayList<>();
            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = text(row, col.get(NAME), fmt, eval);
                String pan = text(row, col.get(PAN), fmt, eval);
                if (name.isEmpty() && pan.isEmpty()) continue;
                List<PartnerCell> partners = new ArrayList<>();
                for (List<Integer> g : groups) {
                    String partner = text(row, g.get(0), fmt, eval);
                    if (partner.isEmpty()) continue;
                    partners.add(cell(partner, text(row, g.get(1), fmt, eval), row.getCell(g.get(2))));
                }
                rows.add(new MasterRow(r + 1, name, pan, partners, col.containsKey(RM) ? text(row, col.get(RM), fmt, eval) : "",
                        col.containsKey(BRANCH) ? text(row, col.get(BRANCH), fmt, eval) : ""));
            }
            return new Result(rows, null);
        } catch (Exception e) {
            return new Result(List.of(), "The file could not be read as an Excel workbook (.xlsx)");
        }
    }

    private static PartnerCell cell(String partner, String type, Cell pctCell) {
        String problem = null;
        BigDecimal pct = null;
        CellType t = pctCell == null ? CellType.BLANK
                : pctCell.getCellType() == CellType.FORMULA ? pctCell.getCachedFormulaResultType() : pctCell.getCellType();
        if (t == CellType.NUMERIC) {
            pct = BigDecimal.valueOf(pctCell.getNumericCellValue());
        } else if (t == CellType.STRING) {
            String s = pctCell.getStringCellValue().trim().replace("%", "");
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

    private static Map<String, Integer> headers(Row row, DataFormatter fmt, FormulaEvaluator eval) {
        Map<String, Integer> found = new HashMap<>();
        if (row == null) return found;
        for (Cell cell : row) {
            String h = Text.key(fmt.formatCellValue(cell, eval));
            if (!h.isEmpty()) found.putIfAbsent(h, cell.getColumnIndex());
        }
        return found;
    }

    private static String text(Row row, int col, DataFormatter fmt, FormulaEvaluator eval) {
        Cell cell = row.getCell(col);
        String v = cell == null ? null : Text.clean(fmt.formatCellValue(cell, eval));
        return v == null ? "" : v;
    }
}
