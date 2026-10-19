package money.epsilon.brokerage.intermediary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.dated.DatedRow;

/**
 * A platform's brokerage terms over a period (R-75): whether its brokerage already includes GST, the TDS it deducts, and
 * the share of the mark-up it pays Epsilon (100 = all of it). Percent values: 5 = 5%.
 */
@Entity
@Table(name = "intermediary_terms")
public class IntermediaryTerms extends DatedRow {

    @Column(name = "intermediary_id")
    private Long intermediaryId;

    @Column(name = "gst_inclusive")
    private boolean gstInclusive;

    @Column(name = "tds_percent")
    private BigDecimal tdsPercent;

    @Column(name = "our_share")
    private BigDecimal ourShare;

    protected IntermediaryTerms() {
    }

    public IntermediaryTerms(Long intermediaryId, boolean gstInclusive, BigDecimal tdsPercent, BigDecimal ourShare, LocalDate from, String reason) {
        super(from, null, reason);
        this.intermediaryId = intermediaryId;
        this.gstInclusive = gstInclusive;
        this.tdsPercent = tdsPercent;
        this.ourShare = ourShare;
    }

    public Long getIntermediaryId() { return intermediaryId; }
    public boolean isGstInclusive() { return gstInclusive; }
    public BigDecimal getTdsPercent() { return tdsPercent; }
    public BigDecimal getOurShare() { return ourShare; }

    public boolean sameAs(boolean inclusive, BigDecimal tds, BigDecimal share) {
        return gstInclusive == inclusive && tdsPercent.compareTo(tds) == 0 && ourShare.compareTo(share) == 0;
    }

    public void correct(boolean inclusive, BigDecimal tds, BigDecimal share, String reason) {
        this.gstInclusive = inclusive;
        this.tdsPercent = tds;
        this.ourShare = share;
        setReason(reason);
    }
}
