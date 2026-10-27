package money.epsilon.brokerage.insurance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.BusinessType;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.Category;
import money.epsilon.brokerage.platform.text.Text;

/** How the insurance policy sheet writes percentages, months, categories and business types (R-94). */
public final class InsuranceSheetText {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Pattern MONTH_TEXT = Pattern.compile("([A-Za-z]{3})[A-Za-z]*\\W*(\\d{4}|\\d{2})");
    private static final DateTimeFormatter MON = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMM").toFormatter(Locale.ENGLISH);

    private InsuranceSheetText() {
    }

    /** A fraction (0.15) as a percent (15); a value above 1 is taken as already in percent. */
    public static BigDecimal percent(BigDecimal v) {
        if (v == null) return null;
        BigDecimal p = v.abs().compareTo(BigDecimal.ONE) <= 0 ? v.multiply(HUNDRED) : v;
        return p.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    public static Category category(String text) {
        String k = Text.key(text);
        for (Category c : Category.values()) if (k.startsWith(c.name())) return c;
        return null;
    }

    public static BusinessType businessType(String text) {
        String k = Text.key(text);
        if (k.startsWith("PORT")) return BusinessType.PORT;
        if (k.startsWith("FRESH")) return BusinessType.NEW;
        for (BusinessType b : BusinessType.values()) if (k.startsWith(b.name())) return b;
        return null;
    }

    /** "Aug'26", "August 2026" or a date cell; null when it is none of these. */
    public static YearMonth monthOf(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return YearMonth.from(d);
        Matcher m = MONTH_TEXT.matcher(v.toString().trim());
        if (!m.find()) return null;
        try {
            int month = MON.parse(m.group(1)).get(ChronoField.MONTH_OF_YEAR);
            int year = Integer.parseInt(m.group(2));
            return YearMonth.of(year < 100 ? 2000 + year : year, month);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
