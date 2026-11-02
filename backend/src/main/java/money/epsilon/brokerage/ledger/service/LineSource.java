package money.epsilon.brokerage.ledger.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * A hand-entered product whose entries become ledger lines when a month is processed (audit M-03). Each product module
 * provides one; month processing finds them all and holds no code of its own per product. A new product adds a source,
 * not a branch in month processing.
 */
public interface LineSource {

    /** The ledger product code (FD, BOND…), which is also the product its partner % is read for. */
    String product();

    /** Its name in messages ("Bonds", "GIFT City"). */
    String label();

    /** Its place among the month's lines and in the END: lower first. Equity's report sits at 60. */
    int order();

    /** Shared only with the POSP on the entry, at the entry's own commission (insurance), not at a partner's product %. */
    default boolean sharedWithPosp() {
        return false;
    }

    /** The month's entries with revenue. */
    List<SourceLine> entries(YearMonth month);

    /** Writes them as the month's lines of this product, replacing the earlier ones. */
    void writeLines(long periodId, List<SourceLine> entries);

    /** The entry a line came from, or null when the line is not this product's. */
    Long entryOf(LedgerService.ActiveLine line);

    /**
     * The month's entries whose client was found by name only and waits for a person to confirm it (R-100): the month is
     * not processed while there are any. None by default.
     */
    default List<NameMatch> nameMatches(YearMonth month) {
        return List.of();
    }

    /** An entry waiting for its client to be confirmed: {@code ref} as people know it (policy no.), the client suggested. */
    record NameMatch(long entryId, String ref, String nameInFile, long clientId, String clientName, String pan, String rmName, BigDecimal amount) {
    }

    /**
     * One entry: {@code partnerId} the partner on it (the POSP for insurance); {@code dealPercent} a deal's own sharing %
     * (AIF, Unlisted), which wins over the partner's %; {@code pospFraction} and {@code partnerName} for a POSP share.
     */
    record SourceLine(long entryId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net, Long partnerId, BigDecimal dealPercent,
                      BigDecimal pospFraction, String partnerName) {

        public static SourceLine of(long entryId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net, Long partnerId) {
            return new SourceLine(entryId, clientId, gross, gst, net, partnerId, null, null, null);
        }
    }
}
