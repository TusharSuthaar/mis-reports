package money.epsilon.brokerage.sharing.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.platform.text.Text;

/**
 * Products a partner can share in, with the stored code and the column heading used in the Master MIS partner sheets.
 * Insurance is shared with POSPs, not sub-brokers / referral partners (rule R-56): their Insurance % is normally 0.
 * Pre-IPO is shown as "Unlisted" (R-97); files and sheets may still call it Pre-IPO, and both names are read.
 */
public enum Product {
    MF("MF", "MF"),
    PREIPO("PREIPO", "Pre-IPO", "Unlisted"),
    FD("FD", "FD"),
    BOND("BOND", "Bonds"),
    LOAN("LOAN", "Loans"),
    EQUITY("EQUITY", "Equity"),
    PMS("PMS", "PMS"),
    AIF("AIF", "AIF"),
    ADVISORY("ADVISORY", "Advisory"),
    GIFT("GIFT", "GIFT"),
    NPS("NPS", "NPS"),
    P2P("P2P", "P2P"),
    INS("INS", "Insurance");

    private final String code;
    private final String heading;
    private final String label;

    Product(String code, String heading) {
        this(code, heading, heading);
    }

    Product(String code, String heading, String label) {
        this.code = code;
        this.heading = heading;
        this.label = label;
    }

    public String code() {
        return code;
    }

    /** The column heading in the Master MIS partner sheets. */
    public String heading() {
        return heading;
    }

    /** The name shown on screens, messages and the END file ("Unlisted" for Pre-IPO). */
    public String label() {
        return label;
    }

    /** Every name a file or sheet may use for the product: the heading and, where it differs, the label. */
    public List<String> names() {
        return heading.equals(label) ? List.of(heading) : List.of(heading, label);
    }

    /** By code ("PREIPO") or any of its names ("Pre-IPO", "Pre IPO", "Unlisted"), case, spaces and dashes ignored. */
    public static Optional<Product> find(String text) {
        String k = loose(text);
        return Arrays.stream(values())
                .filter(p -> loose(p.code).equals(k) || p.names().stream().anyMatch(n -> loose(n).equals(k)))
                .findFirst();
    }

    private static String loose(String text) {
        return Text.key(text).replaceAll("[\\s_-]", "");
    }
}
