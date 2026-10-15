package money.epsilon.brokerage.client.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/** A client. Deliberately holds no RM: that lives in {@link ClientRmAssignment}, over time. */
@Entity
@Table(name = "client")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "pan_encrypted")
    private String panEncrypted;

    @Column(name = "pan_hash")
    private String panHash;

    @Column(name = "family_head_code")
    private String familyHeadCode;

    @Column(name = "family_head_client_id")
    private Long familyHeadClientId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Client() {
    }

    public Client(String name, String panEncrypted, String panHash, String familyHeadCode) {
        this.name = name;
        this.panEncrypted = panEncrypted;
        this.panHash = panHash;
        this.familyHeadCode = familyHeadCode;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPanEncrypted() { return panEncrypted; }
    public String getPanHash() { return panHash; }
    public String getFamilyHeadCode() { return familyHeadCode; }
    public Long getFamilyHeadClientId() { return familyHeadClientId; }

    public void setFamilyHeadClientId(Long familyHeadClientId) { this.familyHeadClientId = familyHeadClientId; }
}
