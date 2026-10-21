package money.epsilon.brokerage.sharing.domain;

import java.util.Arrays;
import java.util.Optional;
import money.epsilon.brokerage.platform.text.Text;

/**
 * Products a partner can share in, with the stored code and the column heading used in the Master MIS partner sheets.
 * Insurance is shared with POSPs, not sub-brokers / referral partners (rule R-56): their Insurance % is normally 0.
 */
public enum Product {
    MF("MF", "MF"),
    PREIPO("PREIPO", "Pre-IPO"),
    FD("FD", "FD"),
    BOND("BOND", "Bonds"),
    LOAN("LOAN", "Loans"),
    EQUITY("EQUITY", "Equity"),
    PMS("PMS", "PMS"),
    AIF("AIF", "AIF"),
    ADVISORY("ADVISORY", "Advisory"),
    GIFT("GIFT", "GIFT"),
    NPS("NPS", "NPS"),
    INS("INS", "Insurance");

    private final String code;
    private final String heading;

    Product(String code, String heading) {
        this.code = code;
        this.heading = heading;
    }

    public String code() {
        return code;
    }

    /** The column heading in the Master MIS partner sheets. */
    public String heading() {
        return heading;
    }

    /** By code ("PREIPO") or heading ("Pre-IPO"), case and spaces ignored. */
    public static Optional<Product> find(String text) {
        String k = Text.key(text);
        return Arrays.stream(values()).filter(p -> Text.key(p.code).equals(k) || Text.key(p.heading).equals(k)).findFirst();
    }
}
