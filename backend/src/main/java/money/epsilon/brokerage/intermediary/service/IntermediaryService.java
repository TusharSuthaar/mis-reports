package money.epsilon.brokerage.intermediary.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import money.epsilon.brokerage.intermediary.domain.Intermediary;
import money.epsilon.brokerage.intermediary.domain.IntermediaryTerms;
import money.epsilon.brokerage.intermediary.repository.IntermediaryRepository;
import money.epsilon.brokerage.intermediary.repository.IntermediaryTermsRepository;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.dated.DatedHistory;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.time.BusinessCalendar;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Setup → Platforms: the platforms investments are booked through and their dated brokerage terms (R-75). */
@Service
public class IntermediaryService {

    static final String AUDIT = "PLATFORM";
    static final String AUDIT_TERMS = "PLATFORM_TERMS";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final IntermediaryRepository platforms;
    private final IntermediaryTermsRepository terms;
    private final AuditService audit;
    private final ChangeWindow window;
    private final BusinessCalendar calendar;

    IntermediaryService(IntermediaryRepository platforms, IntermediaryTermsRepository terms, AuditService audit, ChangeWindow window,
                        BusinessCalendar calendar) {
        this.platforms = platforms;
        this.terms = terms;
        this.audit = audit;
        this.window = window;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public List<PlatformView> list() {
        LocalDate today = calendar.today();
        return platforms.findAll().stream().sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .map(p -> new PlatformView(p.getId(), p.getName(), terms.findOn(p.getId(), today).map(this::view).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformView get(long id) {
        Intermediary p = find(id);
        return new PlatformView(p.getId(), p.getName(), terms.findOn(id, calendar.today()).map(this::view).orElse(null));
    }

    @Transactional
    public PlatformView create(String name) {
        String clean = Text.required(name, "Platform name");
        if (platforms.findByNameIgnoreCase(clean).isPresent()) throw new RuleViolationException("Platform '" + clean + "' already exists");
        Intermediary p = platforms.save(new Intermediary(clean));
        audit.record(AUDIT, p.getId(), "CREATE", null, clean);
        return get(p.getId());
    }

    @Transactional
    public PlatformView rename(long id, String name) {
        Intermediary p = find(id);
        String clean = Text.required(name, "Platform name");
        platforms.findByNameIgnoreCase(clean).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new RuleViolationException("Platform '" + clean + "' already exists");
        });
        String before = p.getName();
        p.setName(clean);
        audit.record(AUDIT, id, "UPDATE", before, clean);
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<TermsView> history(long id) {
        find(id);
        return terms.findByIntermediaryIdOrderByValidFromDesc(id).stream().map(this::view).toList();
    }

    /** The terms on a day; refused when none are set, so an entry never runs on guessed terms. */
    @Transactional(readOnly = true)
    public TermsView on(long id, LocalDate day) {
        Intermediary p = find(id);
        return terms.findOn(id, day).map(this::view).orElseThrow(() -> new RuleViolationException(
                "No brokerage terms for " + p.getName() + " on " + day + ": set them under Setup → Platforms"));
    }

    @Transactional
    public void setTerms(long id, boolean gstInclusive, BigDecimal tdsPercent, BigDecimal ourShare, LocalDate from, String reason) {
        find(id);
        BigDecimal tds = percent(tdsPercent, "TDS", false);
        BigDecimal share = percent(ourShare, "Our share", true);
        window.beforeChange(from);
        String why = Text.clean(reason);
        DatedHistory.setFrom(terms.findFirstByIntermediaryIdOrderByValidFromDesc(id), from,
                t -> t.sameAs(gstInclusive, tds, share),
                t -> t.correct(gstInclusive, tds, share, why),
                () -> new IntermediaryTerms(id, gstInclusive, tds, share, from, why),
                terms, audit, AUDIT_TERMS, this::view, why);
    }

    private static BigDecimal percent(BigDecimal v, String what, boolean positive) {
        if (v == null) throw new IllegalArgumentException(what + " % is required");
        if (v.signum() < 0 || (positive && v.signum() == 0) || v.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(what + " % must be " + (positive ? "above 0" : "0 or more") + " and at most 100");
        }
        return v.stripTrailingZeros();
    }

    private Intermediary find(long id) {
        return platforms.findById(id).orElseThrow(() -> new NotFoundException("Platform " + id + " not found"));
    }

    private TermsView view(IntermediaryTerms t) {
        return new TermsView(t.getId(), t.getIntermediaryId(), t.isGstInclusive(), t.getTdsPercent(), t.getOurShare(), t.getValidFrom(),
                t.getValidTo(), t.getReason(), t.getChangedBy(), t.getChangedAt());
    }

    public record PlatformView(Long id, String name, TermsView now) {
    }

    public record TermsView(Long id, Long platformId, boolean gstInclusive, BigDecimal tdsPercent, BigDecimal ourShare, LocalDate validFrom,
                            LocalDate validTo, String reason, String changedBy, Instant changedAt) {
    }
}
