package money.epsilon.brokerage.reporting.end;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Writes the monthly END workbook in the layout of the hand-made END file: "Sub Broker Summary" (sharing per sub broker,
 * referral partner and POSP), "Summary" (Branch &amp; RM table, Category table, Region MOM in lakhs) and "Data_Mon'YY"
 * (one row per client line, values from the ledger split, TOTAL row of SUM formulas). Everything outside the Data rows
 * is a formula on the Data sheet, so the file checks itself. Amounts show without decimals; the cells keep full precision.
 * Two hidden helper columns after REMARKS split "Ref Sharing" into its sub-broker and referral parts.
 */
public final class EndWorkbook {

    /**
     * One DATA row. Percent fields are fractions (0.5 = 50%); null amounts mean nothing. An MF row has the MF figures; an
     * FD row (R-74) has {@code fd} (its commission excl. GST) and {@code aum} (the amount invested).
     */
    public record EndRow(String region, String rd, String branch, String rm, String subBroker, BigDecimal subBrokerPct,
                         String referral, BigDecimal referralPct, String posp, String client, String pan,
                         BigDecimal mfTrail, BigDecimal gst, BigDecimal mfNet,
                         BigDecimal subBrokerAmount, BigDecimal referralAmount, BigDecimal pospAmount, String remarks,
                         BigDecimal aum, BigDecimal fd, BigDecimal bonds, BigDecimal aif, BigDecimal loans, BigDecimal gift,
                         BigDecimal equity, BigDecimal preIpo, BigDecimal pms, BigDecimal nps, BigDecimal p2p, BigDecimal insurance) {
        /** Every product but insurance. */
        public EndRow(String region, String rd, String branch, String rm, String subBroker, BigDecimal subBrokerPct,
                      String referral, BigDecimal referralPct, String posp, String client, String pan,
                      BigDecimal mfTrail, BigDecimal gst, BigDecimal mfNet,
                      BigDecimal subBrokerAmount, BigDecimal referralAmount, BigDecimal pospAmount, String remarks,
                      BigDecimal aum, BigDecimal fd, BigDecimal bonds, BigDecimal aif, BigDecimal loans, BigDecimal gift,
                      BigDecimal equity, BigDecimal preIpo, BigDecimal pms, BigDecimal nps, BigDecimal p2p) {
            this(region, rd, branch, rm, subBroker, subBrokerPct, referral, referralPct, posp, client, pan, mfTrail, gst, mfNet,
                    subBrokerAmount, referralAmount, pospAmount, remarks, aum, fd, bonds, aif, loans, gift, equity, preIpo, pms, nps, p2p, null);
        }

        /** Every product up to Pre-IPO (PMS, NPS, P2P and insurance none). */
        public EndRow(String region, String rd, String branch, String rm, String subBroker, BigDecimal subBrokerPct,
                      String referral, BigDecimal referralPct, String posp, String client, String pan,
                      BigDecimal mfTrail, BigDecimal gst, BigDecimal mfNet,
                      BigDecimal subBrokerAmount, BigDecimal referralAmount, BigDecimal pospAmount, String remarks,
                      BigDecimal aum, BigDecimal fd, BigDecimal bonds, BigDecimal aif, BigDecimal loans, BigDecimal gift, BigDecimal equity,
                      BigDecimal preIpo) {
            this(region, rd, branch, rm, subBroker, subBrokerPct, referral, referralPct, posp, client, pan, mfTrail, gst, mfNet,
                    subBrokerAmount, referralAmount, pospAmount, remarks, aum, fd, bonds, aif, loans, gift, equity, preIpo, null, null, null);
        }

        /** Every product but Pre-IPO, PMS, NPS and P2P. */
        public EndRow(String region, String rd, String branch, String rm, String subBroker, BigDecimal subBrokerPct,
                      String referral, BigDecimal referralPct, String posp, String client, String pan,
                      BigDecimal mfTrail, BigDecimal gst, BigDecimal mfNet,
                      BigDecimal subBrokerAmount, BigDecimal referralAmount, BigDecimal pospAmount, String remarks,
                      BigDecimal aum, BigDecimal fd, BigDecimal bonds, BigDecimal aif, BigDecimal loans, BigDecimal gift, BigDecimal equity) {
            this(region, rd, branch, rm, subBroker, subBrokerPct, referral, referralPct, posp, client, pan, mfTrail, gst, mfNet,
                    subBrokerAmount, referralAmount, pospAmount, remarks, aum, fd, bonds, aif, loans, gift, equity, null);
        }

        public EndRow(String region, String rd, String branch, String rm, String subBroker, BigDecimal subBrokerPct,
                      String referral, BigDecimal referralPct, String posp, String client, String pan,
                      BigDecimal mfTrail, BigDecimal gst, BigDecimal mfNet,
                      BigDecimal subBrokerAmount, BigDecimal referralAmount, BigDecimal pospAmount, String remarks) {
            this(region, rd, branch, rm, subBroker, subBrokerPct, referral, referralPct, posp, client, pan, mfTrail, gst, mfNet,
                    subBrokerAmount, referralAmount, pospAmount, remarks, null, null, null, null, null, null, null, null);
        }

        /** MF net + the other products: the row's revenue excl. GST. */
        BigDecimal totalRev() {
            return nz(mfNet).add(nz(preIpo)).add(nz(fd)).add(nz(bonds)).add(nz(aif)).add(nz(loans)).add(nz(gift)).add(nz(equity))
                    .add(nz(pms)).add(nz(nps)).add(nz(p2p)).add(nz(insurance));
        }
    }

    /** A branch total from the previous month (Total Rev excl. GST and Net Revenue). */
    public record BranchTotal(String region, String branch, BigDecimal gross, BigDecimal net) {
    }

    /**
     * {@code regions}: the region section's order; a region with {@code branchRows} gets one row per branch above it. Regions
     * found in the data but not listed come after, by name.
     */
    public record Input(YearMonth month, boolean draft, List<EndRow> rows, YearMonth previousMonth, List<BranchTotal> previous,
                        List<RegionLayout> regions, boolean previousDraft) {
        /** {@code previousDraft}: the previous month is processed but not locked yet (its figures may still change). */
        public Input(YearMonth month, boolean draft, List<EndRow> rows, YearMonth previousMonth, List<BranchTotal> previous,
                     List<RegionLayout> regions) {
            this(month, draft, rows, previousMonth, previous, regions, false);
        }

        public Input(YearMonth month, boolean draft, List<EndRow> rows, YearMonth previousMonth, List<BranchTotal> previous) {
            this(month, draft, rows, previousMonth, previous, List.of());
        }
    }

    public record RegionLayout(String name, boolean branchRows) {
    }

    static final String[] DATA_HEADERS = {"Region", "RD", "Branch", "Relationship Manager", "Sub-Broker", "Referral Partners",
            "Sharing %", "Posp", "Client", "PAN", "AUM/Inv Amt", "MF Trail", "GST", "MF NET BROKERAGE", "Pre-IPO", "FD", "Bonds",
            "Loans", "Insurance", "Equity", "PMS", "AIF", "GIFT CITY", "NPS", "P2P", "Total Rev", "Ref Sharing", "POSP Sharing", "Total Sharing",
            "Net Revenue", "Broker Type", "REMARKS"};
    static final String[] HELPER_HEADERS = {"Sub-Broker Sharing", "Referral Sharing"};
    static final String SUB_BROKER_TYPE = "Sub broker", REFERRAL_TYPE = "Referral Partner", POSP_TYPE = "POSP";

    // DATA column positions (0-based)
    static final int FD_COL = 15, BONDS_COL = 16, LOANS_COL = 17, GIFT_COL = 22, EQUITY_COL = 19, PMS_COL = 20, NPS_COL = 23, P2P_COL = 24,
            INSURANCE_COL = 18,
            BROKER_TYPE = 30, REMARKS = 31;
    static final int REGION = 0, BRANCH = 2, RM = 3, SUB_BROKER = 4, REFERRAL = 5, POSP = 7, AUM = 10, MF_TRAIL = 11,
            MF_NET = 13, PRE_IPO = 14, AIF = 21, TOTAL_REV = 25, REF_SHARING = 26, POSP_SHARING = 27, TOTAL_SHARING = 28,
            NET_REVENUE = 29, SB_SHARING = 32, REF_ONLY_SHARING = 33;

    /** The END file's accounting format: no decimals, a dash for zero. */
    private static final String MONEY = "_ * #,##0_ ;_ * \\-#,##0_ ;_ * \"-\"??_ ;_ @_ ";
    private static final String LAKHS = "0.00";
    private static final String PERCENT = "0%";

    private final XSSFWorkbook wb = new XSSFWorkbook();
    private final CellStyle header, text, money, lakhs, percent, bold, boldMoney, branchLabel, branchMoney, rmLabel;
    private final Input in;
    private final String data;   // quoted DATA sheet name for formulas
    private int dataFirst, dataLast, dataTotal; // 1-based Excel rows

    private EndWorkbook(Input in) {
        this.in = new Input(in.month(), in.draft(), withoutZeroPartners(in.rows()), in.previousMonth(), in.previous(), in.regions(), in.previousDraft());
        Font arial = font(false, false);
        Font arialBold = font(true, false);
        header = style(font(true, true), null, IndexedColors.DARK_BLUE, true);
        text = style(arial, null, null, false);
        money = style(arial, MONEY, null, false);
        lakhs = style(arial, LAKHS, null, false);
        percent = style(arial, PERCENT, null, false);
        bold = style(arialBold, null, null, false);
        boldMoney = style(arialBold, MONEY, null, false);
        branchLabel = style(arialBold, null, IndexedColors.GREY_25_PERCENT, false);
        branchMoney = style(arialBold, MONEY, IndexedColors.GREY_25_PERCENT, false);
        rmLabel = style(arial, null, null, false);
        rmLabel.setIndention((short) 2);
        data = "'" + dataSheetName(in.month()).replace("'", "''") + "'";
    }

    public static byte[] build(Input in) {
        EndWorkbook w = new EndWorkbook(in);
        w.dataSheet();
        w.summarySheet();
        w.partnerSummarySheet();
        w.wb.setSheetOrder("Sub Broker Summary", 0);
        w.wb.setSheetOrder("Summary", 1);
        w.wb.setActiveSheet(1);
        w.wb.setSelectedTab(1);
        // Store each formula's result too: previews (phone, mail, OneDrive web) do not recalculate.
        w.wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            w.wb.write(out);
            w.wb.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String dataSheetName(YearMonth m) {
        return "Data_" + monYY(m);
    }

    public static String fileName(YearMonth m) {
        return "END_File_" + m.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)) + "_" + m.getYear() + ".xlsx";
    }

    static String monYY(YearMonth m) {
        return m.format(DateTimeFormatter.ofPattern("MMM''yy", Locale.ENGLISH));
    }

    // ---- DATA ----------------------------------------------------------------------------------------------

    private void dataSheet() {
        Sheet s = wb.createSheet(dataSheetName(in.month()));
        Row h = s.createRow(0);
        for (int c = 0; c < DATA_HEADERS.length; c++) cell(h, c, DATA_HEADERS[c], header);
        for (int c = 0; c < HELPER_HEADERS.length; c++) cell(h, DATA_HEADERS.length + c, HELPER_HEADERS[c], header);

        int r = 1;
        for (EndRow e : in.rows()) {
            Row row = s.createRow(r++);
            cell(row, 0, e.region(), text);
            cell(row, 1, e.rd(), text);
            cell(row, 2, e.branch(), text);
            cell(row, 3, e.rm(), text);
            cell(row, 4, e.subBroker(), text);
            cell(row, 5, e.referral(), text);
            sharingPercent(row, e);
            cell(row, 7, e.posp(), text);
            cell(row, 8, e.client(), text);
            cell(row, 9, e.pan(), text);
            num(row, AUM, e.aum());                                // AUM/Inv Amt: the FD amount on an FD row
            num(row, MF_TRAIL, e.mfTrail());
            num(row, MF_TRAIL + 1, e.gst());
            num(row, MF_NET, e.mfNet());
            for (int c = PRE_IPO; c <= P2P_COL; c++) row.createCell(c).setCellStyle(money); // other products: FD below, the rest not live yet
            num(row, PRE_IPO, e.preIpo());
            num(row, FD_COL, e.fd());
            num(row, BONDS_COL, e.bonds());
            num(row, LOANS_COL, e.loans());
            num(row, AIF, e.aif());
            num(row, GIFT_COL, e.gift());
            num(row, EQUITY_COL, e.equity());
            num(row, PMS_COL, e.pms());
            num(row, NPS_COL, e.nps());
            num(row, P2P_COL, e.p2p());
            num(row, INSURANCE_COL, e.insurance());
            BigDecimal refSharing = nz(e.subBrokerAmount()).add(nz(e.referralAmount()));
            BigDecimal totalSharing = refSharing.add(nz(e.pospAmount()));
            num(row, TOTAL_REV, e.totalRev());
            num(row, REF_SHARING, refSharing);
            num(row, POSP_SHARING, nz(e.pospAmount()));
            num(row, TOTAL_SHARING, totalSharing);
            num(row, NET_REVENUE, e.totalRev().subtract(totalSharing));
            cell(row, BROKER_TYPE, brokerType(e), text);
            cell(row, REMARKS, e.remarks(), text);
            num(row, SB_SHARING, nz(e.subBrokerAmount()));
            num(row, REF_ONLY_SHARING, nz(e.referralAmount()));
        }
        dataFirst = 2;
        dataLast = Math.max(r, 2);
        Row total = s.createRow(r);
        dataTotal = r + 1;
        cell(total, 0, "TOTAL", bold);
        for (int c = AUM; c <= NET_REVENUE; c++) total.createCell(c).setCellFormula(sum(col(c), dataFirst, dataLast));
        for (int c = SB_SHARING; c <= REF_ONLY_SHARING; c++) total.createCell(c).setCellFormula(sum(col(c), dataFirst, dataLast));
        for (Cell c : total) if (c.getColumnIndex() > 0) c.setCellStyle(boldMoney);

        s.createFreezePane(0, 1);
        s.setAutoFilter(new CellRangeAddress(0, Math.max(r - 1, 0), 0, DATA_HEADERS.length - 1));
        for (int c = 0; c < DATA_HEADERS.length; c++) s.setColumnWidth(c, (c == 8 ? 32 : c < 6 ? 22 : 14) * 256);
        s.setColumnHidden(SB_SHARING, true);
        s.setColumnHidden(REF_ONLY_SHARING, true);
    }

    private void sharingPercent(Row row, EndRow e) {
        List<BigDecimal> pcts = new ArrayList<>();
        if (e.subBrokerPct() != null) pcts.add(e.subBrokerPct());
        if (e.referralPct() != null) pcts.add(e.referralPct());
        if (pcts.size() == 1) {
            Cell c = row.createCell(6);
            c.setCellValue(pcts.get(0).doubleValue());
            c.setCellStyle(percent);
        } else if (pcts.size() > 1) {
            cell(row, 6, String.join(" + ", pcts.stream().map(EndWorkbook::pct).toList()), text);
        }
    }

    /**
     * A sub-broker, referral partner or POSP whose sharing adds up to 0 for the month is left out of the END (R-83): their
     * name and % are blanked on the DATA rows and they are not listed on the Sub Broker Summary. The portal keeps them.
     */
    static List<EndRow> withoutZeroPartners(List<EndRow> rows) {
        Map<String, BigDecimal> sb = new java.util.HashMap<>(), ref = new java.util.HashMap<>(), posp = new java.util.HashMap<>();
        for (EndRow e : rows) {
            if (e.subBroker() != null) sb.merge(e.subBroker(), nz(e.subBrokerAmount()), BigDecimal::add);
            if (e.referral() != null) ref.merge(e.referral(), nz(e.referralAmount()), BigDecimal::add);
            if (e.posp() != null) posp.merge(e.posp(), nz(e.pospAmount()), BigDecimal::add);
        }
        java.util.function.BiPredicate<Map<String, BigDecimal>, String> zero = (m, name) -> name != null && m.get(name).signum() == 0;
        return rows.stream().map(e -> {
            boolean dropSb = zero.test(sb, e.subBroker()), dropRef = zero.test(ref, e.referral()), dropPosp = zero.test(posp, e.posp());
            if (!dropSb && !dropRef && !dropPosp) return e;
            return new EndRow(e.region(), e.rd(), e.branch(), e.rm(), dropSb ? null : e.subBroker(), dropSb ? null : e.subBrokerPct(),
                    dropRef ? null : e.referral(), dropRef ? null : e.referralPct(), dropPosp ? null : e.posp(), e.client(), e.pan(),
                    e.mfTrail(), e.gst(), e.mfNet(), e.subBrokerAmount(), e.referralAmount(), e.pospAmount(), e.remarks(), e.aum(), e.fd(),
                    e.bonds(), e.aif(), e.loans(), e.gift(), e.equity(), e.preIpo(), e.pms(), e.nps(), e.p2p(), e.insurance());
        }).toList();
    }

    static String brokerType(EndRow e) {
        boolean sb = e.subBroker() != null, ref = e.referral() != null, posp = e.posp() != null;
        if (sb && ref) return SUB_BROKER_TYPE + " + " + REFERRAL_TYPE;
        if (sb) return SUB_BROKER_TYPE;
        if (ref) return REFERRAL_TYPE;
        if (posp) return POSP_TYPE;
        return null;
    }

    // ---- Summary -------------------------------------------------------------------------------------------

    private void summarySheet() {
        Sheet s = wb.createSheet("Summary");
        s.setColumnWidth(0, 32 * 256);
        for (int c = 1; c <= 3; c++) s.setColumnWidth(c, 16 * 256);
        s.setColumnWidth(8, 36 * 256);
        for (int c = 9; c <= 10; c++) s.setColumnWidth(c, 14 * 256);
        s.setColumnWidth(12, 26 * 256);
        String cur = monYY(in.month());

        // A–D: Branch & RM (branch rows sum their RM rows)
        header(row(s, 0), 0, "Row Labels", "Sum of Total Rev", "Sum of Total Sharing", "Sum of Net Revenue");
        Map<String, Set<String>> rmsByBranch = new TreeMap<>();
        for (EndRow e : in.rows()) rmsByBranch.computeIfAbsent(e.branch(), b -> new java.util.TreeSet<>()).add(e.rm());
        int r = 1;
        List<Integer> branchRows = new ArrayList<>();
        for (var b : rmsByBranch.entrySet()) {
            Row br = row(s, r++);
            int branchRowNo = r;
            branchRows.add(branchRowNo);
            cell(br, 0, b.getKey(), branchLabel);
            int firstRm = r + 1;
            for (String rm : b.getValue()) {
                Row rr = row(s, r++);
                cell(rr, 0, rm, rmLabel);
                int[] cols = {TOTAL_REV, TOTAL_SHARING, NET_REVENUE};
                for (int i = 0; i < 3; i++) {
                    formula(rr, i + 1, "SUMIFS(" + dataRange(cols[i]) + "," + dataRange(BRANCH) + ",$A$" + branchRowNo + ","
                            + dataRange(RM) + ",A" + r + ")", money);
                }
            }
            for (int i = 1; i <= 3; i++) {
                String c = col(i);
                formula(br, i, "SUM(" + c + firstRm + ":" + c + r + ")", branchMoney);
            }
        }
        Row gt = row(s, r++);
        int grandRow = r;
        cell(gt, 0, "Grand Total", bold);
        formula(gt, 1, data + "!" + col(TOTAL_REV) + dataTotal, boldMoney);
        formula(gt, 2, data + "!" + col(TOTAL_SHARING) + dataTotal, boldMoney);
        formula(gt, 3, data + "!" + col(NET_REVENUE) + dataTotal, boldMoney);
        Row check = row(s, r++);
        cell(check, 0, "Check (should be 0)", text);
        String branchSum = branchRows.isEmpty() ? "0" : String.join("+", branchRows.stream().map(n -> "B" + n).toList());
        formula(check, 1, "B" + grandRow + "-(" + branchSum + ")", money);
        Row inc = row(s, r++);
        cell(inc, 0, "Inc GST", text);
        formula(inc, 1, "B" + grandRow + "*118%", money);
        formula(inc, 2, "C" + grandRow, money);
        formula(inc, 3, "B" + r + "-C" + r, money);

        // I–K: Category (Exc GST / Inc GST)
        header(row(s, 0), 8, "Category", "Exc GST", "Inc GST");
        String t = data + "!";
        String[][] products = {{"Mutual Fund", col(MF_NET)}, {"Pre-IPO", col(PRE_IPO)}, {"FD", col(PRE_IPO + 1)},
                {"Bonds", col(PRE_IPO + 2)}, {"Loans", col(PRE_IPO + 3)}, {"Insurance (" + cur + " business)", col(PRE_IPO + 4)},
                {"Equity", col(PRE_IPO + 5)}, {"PMS", col(PRE_IPO + 6)}, {"AIF", col(AIF)},
                {"GIFT CITY", col(GIFT_COL)}, {"NPS", col(NPS_COL)}, {"P2P", col(P2P_COL)}};
        int c0 = 1;
        for (String[] p : products) {
            Row pr = row(s, c0++);
            cell(pr, 8, p[0], text);
            formula(pr, 9, t + p[1] + dataTotal, money);
            formula(pr, 10, "J" + c0 + "*118%", money);
        }
        int grossRow = c0 + 1;
        Row gr = row(s, c0++);
        cell(gr, 8, "Gross Revenue", bold);
        formula(gr, 9, "SUM(J2:J" + (grossRow - 1) + ")", boldMoney);
        formula(gr, 10, "SUM(K2:K" + (grossRow - 1) + ")", boldMoney);
        int sharingRow = c0 + 1;
        Row sr = row(s, c0++);
        cell(sr, 8, "Sub Broker & Referral Partner Sharing", text);
        formula(sr, 9, t + col(REF_SHARING) + dataTotal, money);
        formula(sr, 10, "J" + sharingRow, money);
        int pospRow = c0 + 1;
        Row pr = row(s, c0++);
        cell(pr, 8, "POSP", text);
        formula(pr, 9, t + col(POSP_SHARING) + dataTotal, money);
        formula(pr, 10, "J" + pospRow, money);
        int totalRow = c0 + 1;
        Row tr = row(s, c0++);
        cell(tr, 8, "Total", bold);
        formula(tr, 9, "J" + sharingRow + "+J" + pospRow, boldMoney);
        formula(tr, 10, "K" + sharingRow + "+K" + pospRow, boldMoney);
        Row nr = row(s, c0++);
        cell(nr, 8, "Net Revenue", bold);
        formula(nr, 9, "J" + grossRow + "-J" + totalRow, boldMoney);
        formula(nr, 10, "K" + grossRow + "-K" + totalRow, boldMoney);

        // M–T: Region MOM in lakhs (the previous processed month as values, this month as formulas)
        String prev = in.previousMonth() == null ? "Previous" : monYY(in.previousMonth());
        Row mh = row(s, 0);
        cell(mh, 12, "Region", header);
        cell(mh, 14, prev, header);
        cell(mh, 16, cur, header);
        cell(mh, 18, "MOM", header);
        Row mh2 = row(s, 1);
        for (int i = 0; i < 3; i++) {
            cell(mh2, 14 + 2 * i, "Gross", header);
            cell(mh2, 15 + 2 * i, "Net", header);
        }
        Map<String, BigDecimal[]> prevByRegion = new TreeMap<>();
        Map<String, BigDecimal[]> prevByBranch = new TreeMap<>();
        for (BranchTotal b : in.previous()) {
            String region = Objects.toString(b.region(), "");
            add(prevByRegion.computeIfAbsent(region, k -> zeros()), b);
            add(prevByBranch.computeIfAbsent(region + " " + b.branch(), k -> zeros()), b);
        }
        Map<String, Set<String>> branchesOf = new TreeMap<>();
        for (EndRow e : in.rows()) branchesOf.computeIfAbsent(Objects.toString(e.region(), ""), k -> new java.util.TreeSet<>()).add(e.branch());
        for (BranchTotal b : in.previous()) branchesOf.computeIfAbsent(Objects.toString(b.region(), ""), k -> new java.util.TreeSet<>()).add(b.branch());

        java.util.LinkedHashMap<String, Boolean> regions = new java.util.LinkedHashMap<>();
        for (RegionLayout layout : in.regions()) regions.put(layout.name(), layout.branchRows());
        new java.util.TreeSet<>(branchesOf.keySet()).forEach(name -> regions.putIfAbsent(name, false));

        int m = 2;
        List<Integer> regionRows = new java.util.ArrayList<>();
        for (Map.Entry<String, Boolean> region : regions.entrySet()) {
            String name = region.getKey();
            if (region.getValue()) {
                for (String branch : branchesOf.getOrDefault(name, Set.of())) {
                    Row br = row(s, m++);
                    cell(br, 12, branch, rmLabel);
                    String byBranch = "," + dataRange(REGION) + "," + quoted(name) + "," + dataRange(BRANCH) + ",M" + m + ")/(10^5)";
                    momRow(br, m, prevByBranch.get(name + " " + branch),
                            "SUMIFS(" + dataRange(TOTAL_REV) + byBranch, "SUMIFS(" + dataRange(NET_REVENUE) + byBranch);
                }
            }
            Row mr = row(s, m++);
            regionRows.add(m);
            cell(mr, 12, name, bold);
            momRow(mr, m, prevByRegion.get(name),
                    "SUMIFS(" + dataRange(TOTAL_REV) + "," + dataRange(REGION) + ",M" + m + ")/(10^5)",
                    "SUMIFS(" + dataRange(NET_REVENUE) + "," + dataRange(REGION) + ",M" + m + ")/(10^5)");
        }
        Row mt = row(s, m++);
        cell(mt, 12, "TOTAL", bold);
        for (int c = 14; c <= 17; c++) {
            String cc = col(c);
            // Region rows only: branch rows are already inside their region.
            formula(mt, c, regionRows.isEmpty() ? "0" : "SUM(" + String.join(",", regionRows.stream().map(x -> cc + x).toList()) + ")", lakhs);
        }
        formula(mt, 18, mom("Q", "O", m), percent);
        formula(mt, 19, mom("R", "P", m), percent);
        cell(row(s, m + 1), 12, "MOM figures in lakhs", text);
        if (in.draft()) cell(row(s, m + 2), 12, "DRAFT — " + cur + " is not locked yet", bold);
        if (in.previousDraft()) cell(row(s, m + 3), 12, prev + " is not locked yet: its figures may still change", text);
    }

    /** O–T of one region-section row: previous month as values (blank if none), this month as formulas, MOM %. */
    private void momRow(Row r, int excelRow, BigDecimal[] previous, String gross, String net) {
        if (previous != null) {
            formula(r, 14, previous[0].toPlainString() + "/(10^5)", lakhs);
            formula(r, 15, previous[1].toPlainString() + "/(10^5)", lakhs);
        } else {
            r.createCell(14).setCellStyle(lakhs);
            r.createCell(15).setCellStyle(lakhs);
        }
        formula(r, 16, gross, lakhs);
        formula(r, 17, net, lakhs);
        formula(r, 18, mom("Q", "O", excelRow), percent);
        formula(r, 19, mom("R", "P", excelRow), percent);
    }

    private static BigDecimal[] zeros() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static void add(BigDecimal[] v, BranchTotal b) {
        v[0] = v[0].add(nz(b.gross()));
        v[1] = v[1].add(nz(b.net()));
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String mom(String now, String before, int r) {
        return "IF(" + before + r + "=0,\"-\",(" + now + r + "-" + before + r + ")/" + before + r + ")";
    }

    // ---- Sub Broker Summary --------------------------------------------------------------------------------

    private void partnerSummarySheet() {
        Sheet s = wb.createSheet("Sub Broker Summary");
        partnerBlock(s, 0, SUB_BROKER_TYPE, SUB_BROKER, SB_SHARING, EndRow::subBroker);
        partnerBlock(s, 3, REFERRAL_TYPE, REFERRAL, REF_ONLY_SHARING, EndRow::referral);
        partnerBlock(s, 6, POSP_TYPE, POSP, POSP_SHARING, EndRow::posp);
    }

    private void partnerBlock(Sheet s, int c, String type, int nameCol, int sharingCol, Function<EndRow, String> partnerOf) {
        s.setColumnWidth(c, 34 * 256);
        s.setColumnWidth(c + 1, 18 * 256);
        Row t = row(s, 0);
        cell(t, c, "Broker Type", bold);
        cell(t, c + 1, type, text);
        header(row(s, 2), c, "Row Labels", "Sum of Total Sharing");
        Set<String> partners = new java.util.TreeSet<>();
        for (EndRow e : in.rows()) if (partnerOf.apply(e) != null) partners.add(partnerOf.apply(e));
        int r = 3;
        for (String p : partners) {
            Row row = row(s, r++);
            cell(row, c, p, text);
            formula(row, c + 1, "SUMIFS(" + dataRange(sharingCol) + "," + dataRange(nameCol) + "," + col(c) + r + ")", money);
        }
        Row g = row(s, r);
        cell(g, c, "Grand Total", bold);
        formula(g, c + 1, partners.isEmpty() ? "0" : "SUM(" + col(c + 1) + "4:" + col(c + 1) + r + ")", boldMoney);
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private String dataRange(int col) {
        String c = col(col);
        return data + "!$" + c + "$" + dataFirst + ":$" + c + "$" + dataLast;
    }

    private static String col(int c) {
        return CellReference.convertNumToColString(c);
    }

    private static String sum(String col, int from, int to) {
        return "SUM(" + col + from + ":" + col + to + ")";
    }

    private void header(Row row, int from, String... labels) {
        for (int c = 0; c < labels.length; c++) cell(row, from + c, labels[c], header);
    }

    private static Row row(Sheet s, int r) {
        Row row = s.getRow(r);
        return row != null ? row : s.createRow(r);
    }

    private static void cell(Row row, int c, String value, CellStyle style) {
        Cell cell = row.createCell(c);
        if (value != null) cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void num(Row row, int c, BigDecimal value) {
        Cell cell = row.createCell(c);
        if (value != null) cell.setCellValue(value.doubleValue());
        cell.setCellStyle(money);
    }

    private static void formula(Row row, int c, String f, CellStyle style) {
        Cell cell = row.createCell(c);
        cell.setCellFormula(f);
        cell.setCellStyle(style);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String pct(BigDecimal fraction) {
        return fraction.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }

    private Font font(boolean bold, boolean white) {
        Font f = wb.createFont();
        f.setFontName("Arial");
        f.setFontHeightInPoints((short) 10);
        f.setBold(bold);
        if (white) f.setColor(IndexedColors.WHITE.getIndex());
        return f;
    }

    private CellStyle style(Font f, String format, IndexedColors fill, boolean border) {
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        if (format != null) s.setDataFormat(wb.createDataFormat().getFormat(format));
        if (fill != null) {
            s.setFillForegroundColor(fill.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (border) s.setBorderBottom(BorderStyle.THIN);
        return s;
    }
}
