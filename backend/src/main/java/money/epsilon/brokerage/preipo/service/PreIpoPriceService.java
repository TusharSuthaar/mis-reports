package money.epsilon.brokerage.preipo.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import money.epsilon.brokerage.intermediary.service.IntermediaryService;
import money.epsilon.brokerage.intermediary.service.IntermediaryService.PlatformView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.excel.ExcelFile;
import money.epsilon.brokerage.platform.excel.ExcelRow;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.preipo.domain.PreIpoPrice;
import money.epsilon.brokerage.preipo.domain.PreIpoScrip;
import money.epsilon.brokerage.preipo.repository.PreIpoPriceRepository;
import money.epsilon.brokerage.preipo.repository.PreIpoScripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Available IPOs" (R-92): the unlisted shares we deal in and each vendor's base price, dated. A price set for a day never
 * replaces an earlier day's, so the price on any date stays known. The client price is base × (1 + mark-up %), 4% unless
 * set otherwise. Vendors are platforms. The price list can be uploaded as the Pre-IPO price sheet or edited price by price.
 */
@Service
public class PreIpoPriceService {

    static final String AUDIT_SCRIP = "PREIPO_SCRIP";
    static final String AUDIT_PRICE = "PREIPO_PRICE";
    public static final BigDecimal DEFAULT_MARKUP = new BigDecimal("4");
    static final String ISIN = "ISIN";
    static final String BASE = "Base Price (INR)";
    static final List<String> NAME = List.of("Scrip Name", "Srcip Name", "Script Name", "Name");
    /** An ISIN: country code, nine letters or digits, a check digit (e.g. INE0DJ201029). */
    static final java.util.regex.Pattern ISIN_FORMAT = java.util.regex.Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private final PreIpoScripRepository scrips;
    private final PreIpoPriceRepository prices;
    private final IntermediaryService platforms;
    private final AuditService audit;

    PreIpoPriceService(PreIpoScripRepository scrips, PreIpoPriceRepository prices, IntermediaryService platforms, AuditService audit) {
        this.scrips = scrips;
        this.prices = prices;
        this.platforms = platforms;
        this.audit = audit;
    }

    // ---- scrips ------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ScripView> scrips() {
        return scrips.findAllByOrderByNameAsc().stream().map(PreIpoPriceService::view).toList();
    }

    @Transactional(readOnly = true)
    public ScripView scrip(long id) {
        return view(findScrip(id));
    }

    @Transactional
    public ScripView createScrip(ScripInput in) {
        ScripInput c = check(in, null);
        PreIpoScrip s = new PreIpoScrip(c.name());
        s.update(c.name(), c.isin(), c.faceValue(), c.lotSize(), true, c.remarks());
        scrips.saveAndFlush(s);
        audit.record(AUDIT_SCRIP, s.getId(), "CREATE", null, view(s));
        return view(s);
    }

    @Transactional
    public ScripView updateScrip(long id, ScripInput in) {
        PreIpoScrip s = findScrip(id);
        ScripInput c = check(in, id);
        ScripView before = view(s);
        s.update(c.name(), c.isin(), c.faceValue(), c.lotSize(), in.active() == null || in.active(), c.remarks());
        scrips.saveAndFlush(s);
        audit.record(AUDIT_SCRIP, id, "UPDATE", before, view(s));
        return view(s);
    }

    private ScripInput check(ScripInput in, Long id) {
        String name = Text.clean(in.name());
        if (name == null) throw new IllegalArgumentException("Enter the scrip name");
        String isin = Text.clean(in.isin()) == null ? null : Text.clean(in.isin()).toUpperCase();
        scrips.findByNameIgnoreCase(name).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new RuleViolationException("A scrip named " + o.getName() + " already exists");
        });
        if (isin != null) scrips.findByIsinIgnoreCase(isin).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new RuleViolationException("ISIN " + isin + " is already " + o.getName());
        });
        if (in.lotSize() != null && in.lotSize() <= 0) throw new IllegalArgumentException("Lot size must be more than 0");
        return new ScripInput(name, isin, in.faceValue(), in.lotSize(), in.active(), Text.clean(in.remarks()));
    }

    // ---- prices ------------------------------------------------------------------------------------------------------

    /** Every active scrip with each vendor's latest price on or before {@code day}. */
    @Transactional(readOnly = true)
    public List<BoardRow> board(LocalDate day) {
        Map<Long, String> vendors = vendorNames();
        Map<Long, Map<Long, PriceView>> latest = new HashMap<>();
        for (PreIpoPrice p : prices.findByAsOfLessThanEqualOrderByAsOfDescIdDesc(day)) {
            latest.computeIfAbsent(p.getScripId(), k -> new LinkedHashMap<>()).putIfAbsent(p.getVendorId(), view(p, vendors));
        }
        return scrips.findAllByOrderByNameAsc().stream().filter(PreIpoScrip::isActive)
                .map(s -> new BoardRow(view(s), new ArrayList<>(latest.getOrDefault(s.getId(), Map.of()).values()).stream()
                        .sorted(java.util.Comparator.comparing(PriceView::basePrice)).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PriceView> history(long scripId) {
        findScrip(scripId);
        Map<Long, String> vendors = vendorNames();
        return prices.findByScripIdOrderByAsOfDescIdDesc(scripId).stream().map(p -> view(p, vendors)).toList();
    }

    /** A vendor's base price for a scrip from {@code asOf}; the same day set again is corrected, an earlier day is kept. */
    @Transactional
    public PriceView setPrice(long scripId, long vendorId, LocalDate asOf, BigDecimal basePrice, BigDecimal markupPercent) {
        findScrip(scripId);
        platforms.get(vendorId);
        PriceView v = put(scripId, vendorId, asOf, basePrice, markupPercent);
        audit.record(AUDIT_PRICE, v.id(), "SET", null, v);
        return v;
    }

    private PriceView put(long scripId, long vendorId, LocalDate asOf, BigDecimal basePrice, BigDecimal markupPercent) {
        if (asOf == null) throw new IllegalArgumentException("Choose the date of the price");
        if (basePrice == null || basePrice.signum() <= 0) throw new IllegalArgumentException("Enter the base price");
        BigDecimal markup = markupPercent == null ? DEFAULT_MARKUP : markupPercent;
        if (markup.signum() < 0 || markup.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("Mark-up % must be between 0 and 100");
        PreIpoPrice p = prices.findByScripIdAndVendorIdAndAsOf(scripId, vendorId, asOf).orElseGet(() -> new PreIpoPrice(scripId, vendorId, asOf));
        p.set(basePrice, markup);
        prices.saveAndFlush(p);
        return view(p, vendorNames());
    }

    /**
     * Uploads the Pre-IPO price sheet ("Purchase Price Availabity"): a header row with ISIN, the scrip name, face value, lot
     * size and one "Base Price (INR)" column per vendor, the vendor's name written above it. Each base price becomes that
     * vendor's price on {@code asOf} at {@code markupPercent} (default 4%). New scrips and vendors are added; rows with no
     * price and no ISIN (notes, other lists further down the sheet) are skipped.
     */
    @Transactional
    public UploadResult upload(byte[] content, String fileName, LocalDate asOf, BigDecimal markupPercent) {
        if (asOf == null) throw new IllegalArgumentException("Choose the date the prices are for");
        int scripsSeen = 0, newScrips = 0, priceCount = 0, skipped = 0, unpriced = 0;
        TreeSet<String> newVendors = new TreeSet<>();
        try (ExcelFile file = ExcelFile.open(content)) {
            ExcelFile.Table t = file.sheetNames().stream().map(n -> file.table(n, List.of(ISIN, BASE), 15).orElse(null))
                    .filter(x -> x != null && x.missing().isEmpty()).findFirst()
                    .orElseThrow(() -> new RuleViolationException("This is not the Pre-IPO price sheet: no sheet has the columns " + ISIN + " and " + BASE));
            String nameCol = NAME.stream().filter(t::has).findFirst()
                    .orElseThrow(() -> new RuleViolationException("The price sheet has no scrip name column (" + String.join(" / ", NAME) + ")"));
            Map<Integer, String> above = file.rowTexts(t.sheetName(), t.headerRow() - 1);
            Map<Integer, Long> vendorOfColumn = new LinkedHashMap<>();
            for (int col : t.columns().indexesOf(BASE)) {
                String vendor = Text.clean(above.get(col));
                if (vendor == null) throw new RuleViolationException("No vendor name above the " + BASE + " column " + (col + 1));
                vendorOfColumn.put(col, vendorId(vendor, newVendors));
            }
            for (ExcelRow r : t.rows()) {
                String name = Text.clean(r.text(nameCol));
                Map<Long, BigDecimal> bases = new LinkedHashMap<>();
                vendorOfColumn.forEach((col, vendor) -> {
                    if (r.valueAt(col) instanceof BigDecimal b && b.signum() > 0) bases.put(vendor, b);
                });
                String isin = Text.clean(r.text(ISIN)) == null ? null : Text.clean(r.text(ISIN)).toUpperCase();
                if (isin != null && !ISIN_FORMAT.matcher(isin).matches()) isin = null;
                // A scrip row has a name (with letters) and a real ISIN or a price; notes and the side lists further down the
                // sheet (numbers in these columns) have neither.
                if (name == null || !name.matches(".*[A-Za-z].*") || (bases.isEmpty() && isin == null)) {
                    skipped++;
                    continue;
                }
                if (bases.isEmpty()) unpriced++;
                PreIpoScrip s = (isin == null ? null : scrips.findByIsinIgnoreCase(isin).orElse(null));
                if (s == null) s = scrips.findByNameIgnoreCase(name).orElse(null);
                if (s == null) {
                    s = new PreIpoScrip(name);
                    s.update(name, isin, number(r, "Face Value"), lot(r), true, null);
                    scrips.saveAndFlush(s);
                    newScrips++;
                } else if (s.getIsin() == null && isin != null) {
                    s.update(s.getName(), isin, s.getFaceValue() == null ? number(r, "Face Value") : s.getFaceValue(),
                            s.getLotSize() == null ? lot(r) : s.getLotSize(), s.isActive(), s.getRemarks());
                }
                scripsSeen++;
                for (var e : bases.entrySet()) {
                    put(s.getId(), e.getKey(), asOf, e.getValue(), markupPercent);
                    priceCount++;
                }
            }
        }
        UploadResult result = new UploadResult(fileName, asOf, scripsSeen, newScrips, priceCount, unpriced, List.copyOf(newVendors), skipped);
        audit.record(AUDIT_PRICE, null, "UPLOAD", null, result);
        return result;
    }

    private static BigDecimal number(ExcelRow r, String header) {
        return r.value(header) instanceof BigDecimal b ? b : null;
    }

    private static Integer lot(ExcelRow r) {
        BigDecimal b = number(r, "Min. Lot size");
        return b == null || b.signum() <= 0 ? null : b.intValue();
    }

    private long vendorId(String name, TreeSet<String> created) {
        String key = Text.key(name);
        for (PlatformView p : platforms.list()) if (Text.key(p.name()).equals(key)) return p.id();
        created.add(name);
        return platforms.create(name).id();
    }

    private Map<Long, String> vendorNames() {
        Map<Long, String> m = new HashMap<>();
        platforms.list().forEach(p -> m.put(p.id(), p.name()));
        return m;
    }

    private PreIpoScrip findScrip(long id) {
        return scrips.findById(id).orElseThrow(() -> new NotFoundException("Pre-IPO scrip " + id + " not found"));
    }

    private static ScripView view(PreIpoScrip s) {
        return new ScripView(s.getId(), s.getName(), s.getIsin(), s.getFaceValue(), s.getLotSize(), s.isActive(), s.getRemarks());
    }

    private static PriceView view(PreIpoPrice p, Map<Long, String> vendors) {
        return new PriceView(p.getId(), p.getScripId(), p.getVendorId(), vendors.get(p.getVendorId()), p.getAsOf(), p.getBasePrice(),
                p.getMarkupPercent(), p.getClientPrice(), p.getChangedBy(), p.getChangedAt());
    }

    public record ScripInput(String name, String isin, BigDecimal faceValue, Integer lotSize, Boolean active, String remarks) {
    }

    public record ScripView(Long id, String name, String isin, BigDecimal faceValue, Integer lotSize, boolean active, String remarks) {
    }

    public record PriceView(Long id, Long scripId, Long vendorId, String vendorName, LocalDate asOf, BigDecimal basePrice,
                            BigDecimal markupPercent, BigDecimal clientPrice, String changedBy, Instant changedAt) {
    }

    /** A scrip and each vendor's latest price, lowest base price first. */
    public record BoardRow(ScripView scrip, List<PriceView> prices) {
    }

    /** {@code withoutPrice}: scrips on the sheet with no vendor price that day (kept in the list, no price set). */
    public record UploadResult(String fileName, LocalDate asOf, int scrips, int newScrips, int prices, int withoutPrice, List<String> newVendors,
                               int skippedRows) {
    }
}
