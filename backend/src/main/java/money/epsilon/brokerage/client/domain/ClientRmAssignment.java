package money.epsilon.brokerage.client.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.dated.DatedRow;

/** The client's RM from validFrom to validTo (inclusive; open = current). Exactly one RM at any date. */
@Entity
@Table(name = "client_rm_assignment")
public class ClientRmAssignment extends DatedRow {

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "rm_id")
    private Long rmId;

    protected ClientRmAssignment() {
    }

    public ClientRmAssignment(Long clientId, Long rmId, LocalDate from, String reason) {
        super(from, null, reason);
        this.clientId = clientId;
        this.rmId = rmId;
    }

    public Long getClientId() { return clientId; }
    public Long getRmId() { return rmId; }

    /** A correction of who the RM was from this entry's first day (audited by the caller). */
    public void correctRm(Long rmId, String reason) {
        this.rmId = rmId;
        setReason(reason);
    }
}
