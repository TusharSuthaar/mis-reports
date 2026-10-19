package money.epsilon.brokerage.aif.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One drawdown of an AIF commitment: the amount called and the date it was paid. */
@Entity
@Table(name = "aif_instalment")
public class AifInstalment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aif_entry_id")
    private Long aifEntryId;

    private int seq;
    private BigDecimal amount;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    protected AifInstalment() {
    }

    public AifInstalment(Long aifEntryId, int seq, BigDecimal amount, LocalDate paidOn) {
        this.aifEntryId = aifEntryId;
        this.seq = seq;
        this.amount = amount;
        this.paidOn = paidOn;
    }

    public Long getId() { return id; }
    public Long getAifEntryId() { return aifEntryId; }
    public int getSeq() { return seq; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaidOn() { return paidOn; }
}
