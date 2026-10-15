package money.epsilon.brokerage.partner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/** A revenue-sharing partner. PAN is held only encrypted, with a keyed hash for lookups. */
@Entity
@Table(name = "partner")
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private PartnerType type;

    private String name;

    @Column(name = "pan_encrypted")
    private String panEncrypted;

    @Column(name = "pan_hash")
    private String panHash;

    private String gstin;

    @Column(name = "arn_or_posp_code")
    private String arnOrPospCode;

    @Column(name = "bank_ref")
    private String bankRef;

    @Enumerated(EnumType.STRING)
    private PartnerStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Partner() {
    }

    public Partner(PartnerType type, String name) {
        this.type = type;
        this.name = name;
        this.status = PartnerStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void setPan(String encrypted, String hash) {
        this.panEncrypted = encrypted;
        this.panHash = hash;
    }

    public Long getId() { return id; }
    public PartnerType getType() { return type; }
    public String getName() { return name; }
    public String getPanEncrypted() { return panEncrypted; }
    public String getPanHash() { return panHash; }
    public String getGstin() { return gstin; }
    public String getArnOrPospCode() { return arnOrPospCode; }
    public String getBankRef() { return bankRef; }
    public PartnerStatus getStatus() { return status; }

    public void setName(String name) { this.name = name; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public void setArnOrPospCode(String arnOrPospCode) { this.arnOrPospCode = arnOrPospCode; }
    public void setBankRef(String bankRef) { this.bankRef = bankRef; }
    public void setStatus(PartnerStatus status) { this.status = status; }
}
