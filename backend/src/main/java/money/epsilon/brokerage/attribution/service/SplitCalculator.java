package money.epsilon.brokerage.attribution.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.partner.domain.PartnerType;

/**
 * The money rule for one brokerage line. Partners are paid in {@code order}; each partner's % is taken from what is
 * left after the partners before it; Epsilon keeps the rest. Partner amounts are rounded to 2 decimals; Epsilon's share
 * takes any rounding remainder, so the parts always add up exactly to the net.
 * <p>Net 100, sub-broker 50%, referral 20% → sub-broker 50.00, referral 10.00 (20% of the remaining 50), Epsilon 40.
 */
public final class SplitCalculator {

    /** What happens when a line's net is negative (a clawback). PROVISIONAL, see mis.sharing.negative-net. */
    public enum NegativeNet {
        /** Partners give back the same % they would have received. */
        SAME_PERCENT,
        /** Epsilon carries the whole clawback; partners are not charged. */
        EPSILON_ABSORBS
    }

    /** A partner on the client in a role, with its share as a fraction (0.8 = 80%). */
    public record Share(PartnerType role, long partnerId, String partnerName, BigDecimal fraction) {
    }

    /** One part of the split. {@code share} is null for Epsilon's part. */
    public record Part(Share share, BigDecimal base, BigDecimal amount) {

        public boolean isCompany() {
            return share == null;
        }
    }

    private SplitCalculator() {
    }

    public static List<Part> split(BigDecimal net, List<Share> shares, List<PartnerType> order, NegativeNet negative) {
        if (shares.stream().anyMatch(s -> !order.contains(s.role()))) {
            throw new IllegalStateException("A partner role is missing from mis.sharing.order: " + shares);
        }
        List<Part> parts = new ArrayList<>();
        BigDecimal remaining = net;
        boolean partnersExcused = net.signum() < 0 && negative == NegativeNet.EPSILON_ABSORBS;
        // Roles in the configured order; two partners of one role (a branch rule's and the client's own) in list order.
        for (PartnerType role : order) {
            for (Share s : shares) {
                if (s.role() != role) continue;
                BigDecimal amount = partnersExcused ? BigDecimal.ZERO.setScale(2) : remaining.multiply(s.fraction()).setScale(2, RoundingMode.HALF_UP);
                parts.add(new Part(s, remaining, amount));
                remaining = remaining.subtract(amount);
            }
        }
        parts.add(new Part(null, net, remaining));
        BigDecimal total = parts.stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(net) != 0) throw new IllegalStateException("Split " + total + " does not add up to net " + net);
        return parts;
    }
}
