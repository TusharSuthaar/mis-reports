package money.epsilon.brokerage.sharing.load;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.platform.excel.ExcelFile;
import money.epsilon.brokerage.platform.excel.ExcelRow;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.sharing.domain.Product;

/**
 * Reads the partner sheets of the Master MIS workbook: one sheet of sub-brokers (first column "Sub-Broker") and one of
 * referral partners (first column "Referral Partner"), each with a % per product column (MF, Pre-IPO, FD, Bonds, Loans,
 * Equity, PMS, AIF, Advisory, GIFT, Insurance) as a fraction (0.8 = 80%). Sheets are found by their headers, not their
 * names. Both are optional: a workbook without them is read as before (client rows only).
 */
final class PartnerSheetReader {

    private static final int HEADER_SEARCH_ROWS = 10;
    private static final Map<PartnerType, String> NAME_COLUMN = Map.of(
            PartnerType.SUB_BROKER, "Sub-Broker",
            PartnerType.REFERRAL, "Referral Partner");

    /** {@code percents}: product → fraction, only for the product columns present and filled. */
    record PartnerRates(String sheet, int row, PartnerType type, String name, Map<Product, BigDecimal> fractions) {
    }

    record Result(List<PartnerRates> partners, List<String> problems) {
    }

    private PartnerSheetReader() {
    }

    static Result read(byte[] content) {
        List<PartnerRates> partners = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try (ExcelFile file = ExcelFile.open(content)) {
            for (String sheet : file.sheetNames()) {
                for (var e : NAME_COLUMN.entrySet()) {
                    var table = file.table(sheet, List.of(e.getValue(), Product.MF.heading()), HEADER_SEARCH_ROWS);
                    if (table.isEmpty() || !table.get().missing().isEmpty()) continue;
                    List<Product> columns = java.util.Arrays.stream(Product.values()).filter(p -> table.get().has(p.heading())).toList();
                    for (ExcelRow row : table.get().rows()) {
                        String name = Text.clean(row.text(e.getValue()));
                        if (name == null) continue;
                        Map<Product, BigDecimal> fractions = new EnumMap<>(Product.class);
                        for (Product p : columns) {
                            BigDecimal f = fraction(row.value(p.heading()), sheet, row.rowNumber(), p, problems);
                            if (f != null) fractions.put(p, f);
                        }
                        if (fractions.isEmpty()) {
                            // No % at all: a note under the table (e.g. "*sharing is as per Client"), not a partner.
                            problems.add(sheet + " row " + row.rowNumber() + ": no % in any product column, read as a note: \""
                                    + (name.length() > 80 ? name.substring(0, 80) + "…" : name) + "\"");
                            continue;
                        }
                        partners.add(new PartnerRates(sheet, row.rowNumber(), e.getKey(), name, fractions));
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            return new Result(List.of(), List.of(ExcelFile.UNREADABLE));
        }
        return new Result(partners, problems);
    }

    /** A % cell as a fraction; blank / "NA" = not given. */
    private static BigDecimal fraction(Object cell, String sheet, int row, Product product, List<String> problems) {
        if (cell == null) return null;
        BigDecimal value;
        if (cell instanceof BigDecimal d) {
            value = d;
        } else {
            String s = cell.toString().trim().replace("%", "");
            if (s.isEmpty() || s.equalsIgnoreCase("NA")) return null;
            try {
                value = new BigDecimal(s);
            } catch (NumberFormatException e) {
                problems.add(sheet + " row " + row + ": " + product.heading() + " '" + s + "' is not a %");
                return null;
            }
        }
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            problems.add(sheet + " row " + row + ": " + product.heading() + " must be a fraction between 0 and 1 (e.g. 0.8), not "
                    + value.toPlainString());
            return null;
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
