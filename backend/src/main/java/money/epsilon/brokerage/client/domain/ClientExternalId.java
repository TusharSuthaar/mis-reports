package money.epsilon.brokerage.client.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The client's code in an outside system, e.g. source INVESTWELL, code = CLIENT IWELL CODE. */
@Entity
@Table(name = "client_external_id")
public class ClientExternalId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id")
    private Long clientId;

    private String source;

    private String code;

    private String code2;

    protected ClientExternalId() {
    }

    public ClientExternalId(Long clientId, String source, String code, String code2) {
        this.clientId = clientId;
        this.source = source;
        this.code = code;
        this.code2 = code2;
    }

    public Long getClientId() { return clientId; }
    public String getSource() { return source; }
    public String getCode() { return code; }
    public String getCode2() { return code2; }
}
