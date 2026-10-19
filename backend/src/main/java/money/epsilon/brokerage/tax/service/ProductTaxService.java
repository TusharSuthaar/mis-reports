package money.epsilon.brokerage.tax.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.dated.DatedHistory;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.time.BusinessCalendar;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.sharing.domain.Product;
import money.epsilon.brokerage.tax.domain.ProductTaxRate;
import money.epsilon.brokerage.tax.repository.ProductTaxRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Products &amp; taxes master: GST and TDS per product, changed "from a date" (R-71). */
@Service
public class ProductTaxService {

    static final String AUDIT = "PRODUCT_TAX";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductTaxRateRepository rates;
    private final AuditService audit;
    private final ChangeWindow window;
    private final BusinessCalendar calendar;

    ProductTaxService(ProductTaxRateRepository rates, AuditService audit, ChangeWindow window, BusinessCalendar calendar) {
        this.rates = rates;
        this.audit = audit;
        this.window = window;
        this.calendar = calendar;
    }

    /** One line per product with its rate today (null when never set). */
    @Transactional(readOnly = true)
    public List<ProductTaxLine> list() {
        LocalDate today = calendar.today();
        return Arrays.stream(Product.values())
                .map(p -> new ProductTaxLine(p, p.heading(), rates.findOn(p.code(), today).map(this::view).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxRateView> history(Product product) {
        return rates.findByProductTypeOrderByValidFromDesc(product.code()).stream().map(this::view).toList();
    }

    /** The rate in force on {@code day}; refused when none is set, so an entry never runs on a guessed rate. */
    @Transactional(readOnly = true)
    public TaxRateView on(Product product, LocalDate day) {
        return rates.findOn(product.code(), day).map(this::view).orElseThrow(() -> new RuleViolationException(
                "No GST/TDS set for " + product.heading() + " on " + day + ": set it under Setup → Products & taxes"));
    }

    @Transactional
    public void set(Product product, BigDecimal gstPercent, BigDecimal tdsPercent, boolean tdsByDefault, LocalDate from, String reason) {
        if (product == null) throw new IllegalArgumentException("Choose a product");
        BigDecimal gst = percent(gstPercent, "GST");
        BigDecimal tds = percent(tdsPercent, "TDS");
        window.beforeChange(from);
        String why = Text.clean(reason);
        DatedHistory.setFrom(rates.findFirstByProductTypeOrderByValidFromDesc(product.code()), from,
                r -> r.sameAs(gst, tds, tdsByDefault),
                r -> r.correct(gst, tds, tdsByDefault, why),
                () -> new ProductTaxRate(product.code(), gst, tds, tdsByDefault, from, why),
                rates, audit, AUDIT, this::view, why);
    }

    private static BigDecimal percent(BigDecimal value, String what) {
        if (value == null) throw new IllegalArgumentException(what + " % is required (0 if none)");
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) throw new IllegalArgumentException(what + " % must be between 0 and 100");
        return value.stripTrailingZeros();
    }

    private TaxRateView view(ProductTaxRate r) {
        return new TaxRateView(r.getId(), Product.find(r.getProductType()).orElse(null), r.getGstPercent(), r.getTdsPercent(),
                r.isTdsByDefault(), r.getValidFrom(), r.getValidTo(), r.getReason(), r.getChangedBy(), r.getChangedAt());
    }

    public record ProductTaxLine(Product product, String name, TaxRateView now) {
    }

    public record TaxRateView(Long id, Product product, BigDecimal gstPercent, BigDecimal tdsPercent, boolean tdsByDefault,
                              LocalDate validFrom, LocalDate validTo, String reason, String changedBy, Instant changedAt) {
    }
}
