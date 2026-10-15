package money.epsilon.brokerage.period.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.period.domain.Period;
import money.epsilon.brokerage.period.domain.Period.Status;
import money.epsilon.brokerage.period.repository.PeriodRepository;
import java.time.Instant;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calendar-month periods per product. Locking (and finance review) comes with Phase 5. */
@Service
public class PeriodService {

    private final PeriodRepository periods;
    private final AuditService audit;

    PeriodService(PeriodRepository periods, AuditService audit) {
        this.periods = periods;
        this.audit = audit;
    }

    /** The period for that month, created OPEN if it does not exist yet. A LOCKED month is allowed: its upload becomes a correction. */
    @Transactional
    public PeriodView forUpload(String productType, YearMonth month) {
        return view(periods.findByProductTypeAndMonth(productType, month.atDay(1))
                .orElseGet(() -> periods.save(new Period(productType, month))));
    }

    /** Where corrections to a locked month go: the first later month that is not locked (created if needed); it is (re)opened. */
    @Transactional
    public PeriodView nextOpenAfter(String productType, YearMonth month) {
        YearMonth m = month.plusMonths(1);
        while (true) {
            Period p = periods.findByProductTypeAndMonth(productType, m.atDay(1)).orElse(null);
            if (p == null) return view(periods.save(new Period(productType, m)));
            if (p.getStatus() != Status.LOCKED) {
                p.reopen();
                return view(p);
            }
            m = m.plusMonths(1);
        }
    }

    /** The figures of the month changed (lines saved or replaced): it must be processed again. */
    @Transactional
    public void reopen(long id) {
        Period p = find(id);
        if (p.getStatus() == Status.LOCKED) throw new RuleViolationException("A locked month cannot change");
        p.reopen();
    }

    @Transactional
    public void markProcessed(long id, String by) {
        Period p = find(id);
        if (p.getStatus() == Status.LOCKED) throw new RuleViolationException("A locked month cannot be processed again");
        p.markProcessed(by);
    }

    @Transactional
    public PeriodView lock(long id, String by) {
        Period p = find(id);
        if (p.getStatus() != Status.PROCESSED) throw new RuleViolationException("Process the month before locking it");
        p.lock(by);
        audit.record("PERIOD", id, "LOCK", null, view(p));
        return view(p);
    }

    @Transactional
    public PeriodView unlock(long id, String reason) {
        Period p = find(id);
        if (p.getStatus() != Status.LOCKED) throw new RuleViolationException("The month is not locked");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to unlock a month");
        PeriodView before = view(p);
        p.unlock();
        audit.record("PERIOD", id, "UNLOCK", before, new Unlock(view(p), reason.trim()));
        return view(p);
    }

    private Period find(long id) {
        return periods.findById(id).orElseThrow(() -> new NotFoundException("Period " + id + " not found"));
    }

    record Unlock(PeriodView period, String reason) {
    }

    @Transactional(readOnly = true)
    public PeriodView get(long id) {
        return view(find(id));
    }

    @Transactional(readOnly = true)
    public List<PeriodView> list(String productType) {
        return periods.findByProductTypeOrderByMonthDesc(productType).stream().map(PeriodService::view).toList();
    }

    private static PeriodView view(Period p) {
        return new PeriodView(p.getId(), p.getProductType(), YearMonth.from(p.getMonth()), p.getFromDate(), p.getToDate(), p.getStatus(),
                p.getProcessedBy(), p.getProcessedAt(), p.getLockedBy(), p.getLockedAt());
    }

    public record PeriodView(Long id, String productType, YearMonth month, LocalDate fromDate, LocalDate toDate, Status status,
                             String processedBy, Instant processedAt, String lockedBy, Instant lockedAt) {
    }
}
