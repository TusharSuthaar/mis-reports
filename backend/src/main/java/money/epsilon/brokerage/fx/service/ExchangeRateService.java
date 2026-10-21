package money.epsilon.brokerage.fx.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.time.BusinessCalendar;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchange rates by day (R-84). A day already in the list is answered from it; otherwise the rate is fetched once from
 * Frankfurter (ECB) and kept. A rate set by hand replaces that day's rate for later look-ups; saved entries keep their own.
 */
@Service
public class ExchangeRateService {

    static final String AUDIT = "EXCHANGE_RATE";
    private static final List<String> CURRENCIES = List.of("USD");

    private final JdbcClient jdbc;
    private final RateSource source;
    private final AuditService audit;
    private final BusinessCalendar calendar;

    ExchangeRateService(JdbcClient jdbc, RateSource source, AuditService audit, BusinessCalendar calendar) {
        this.jdbc = jdbc;
        this.source = source;
        this.audit = audit;
        this.calendar = calendar;
    }

    /** {@code rate} null = not in the list and could not be fetched ({@code note} says so). */
    public record Rate(String currency, LocalDate day, BigDecimal rate, LocalDate rateDate, String source, String note, String reason,
                       String changedBy, Instant changedAt) {
    }

    /** The rate for the day: from the list, else fetched once and kept. A day after today uses today. */
    @Transactional
    public Rate toInr(String currency, LocalDate day) {
        String cur = check(currency);
        LocalDate today = calendar.today();
        LocalDate on = day == null || day.isAfter(today) ? today : day;
        Optional<Rate> saved = find(cur, on);
        if (saved.isPresent()) return saved.get();
        Optional<RateSource.Fetched> fetched = source.toInr(cur, on);
        if (fetched.isEmpty()) {
            return new Rate(cur, on, null, null, null, "Could not fetch the rate: type it on the entry", null, null, null);
        }
        if (on.equals(today) && fetched.get().rateDate().isBefore(on)) {
            // Today's ECB rate is not out yet: use the latest one, but do not keep it against today.
            return new Rate(cur, on, fetched.get().rate(), fetched.get().rateDate(), "ECB",
                    "ECB rate of " + fetched.get().rateDate() + " (today's is not out yet)", null, null, null);
        }
        jdbc.sql("""
                        INSERT INTO exchange_rate (currency, day, rate, rate_date, source, changed_by) VALUES (?, ?, ?, ?, 'ECB', ?)
                        ON CONFLICT (currency, day) DO NOTHING""")
                .params(cur, on, fetched.get().rate(), fetched.get().rateDate(), CurrentUser.username()).update();
        return find(cur, on).orElseThrow();
    }

    /** Sets (or corrects) a day's rate by hand; audited. */
    @Transactional
    public Rate setByHand(String currency, LocalDate day, BigDecimal rate, String reason) {
        String cur = check(currency);
        if (day == null) throw new IllegalArgumentException("Choose the day");
        if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException("Enter a rate above 0");
        Optional<Rate> before = find(cur, day);
        jdbc.sql("""
                        INSERT INTO exchange_rate (currency, day, rate, rate_date, source, reason, changed_by) VALUES (?, ?, ?, ?, 'MANUAL', ?, ?)
                        ON CONFLICT (currency, day) DO UPDATE SET rate = excluded.rate, rate_date = excluded.rate_date, source = 'MANUAL',
                            reason = excluded.reason, changed_by = excluded.changed_by, changed_at = now()""")
                .params(cur, day, rate.setScale(4, RoundingMode.HALF_UP), day, Text.clean(reason), CurrentUser.username()).update();
        Rate after = find(cur, day).orElseThrow();
        audit.record(AUDIT, cur + " " + day, before.isPresent() ? "UPDATE" : "CREATE", before.orElse(null), after);
        return after;
    }

    /** The list, newest day first. */
    @Transactional(readOnly = true)
    public List<Rate> list(String currency) {
        return jdbc.sql("SELECT * FROM exchange_rate WHERE currency = ? ORDER BY day DESC LIMIT 500").param(check(currency))
                .query((rs, n) -> row(rs)).list();
    }

    private Optional<Rate> find(String currency, LocalDate day) {
        return jdbc.sql("SELECT * FROM exchange_rate WHERE currency = ? AND day = ?").params(currency, day).query((rs, n) -> row(rs)).optional();
    }

    private static Rate row(java.sql.ResultSet rs) throws java.sql.SQLException {
        String src = rs.getString("source");
        LocalDate rateDate = rs.getDate("rate_date").toLocalDate();
        LocalDate day = rs.getDate("day").toLocalDate();
        String note = "MANUAL".equals(src) ? "set by hand" : "ECB rate of " + rateDate + (rateDate.equals(day) ? "" : " (last working day)");
        return new Rate(rs.getString("currency"), day, rs.getBigDecimal("rate"), rateDate, src, note, rs.getString("reason"),
                rs.getString("changed_by"), rs.getTimestamp("changed_at").toInstant());
    }

    private static String check(String currency) {
        String c = currency == null ? "" : currency.trim().toUpperCase();
        if (!CURRENCIES.contains(c)) throw new IllegalArgumentException("Only USD rates are kept for now");
        return c;
    }
}
