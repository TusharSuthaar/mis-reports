package money.epsilon.brokerage.preipo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import money.epsilon.brokerage.platform.security.CurrentUser;

/** An unlisted share we deal in (R-92): name, ISIN, face value, minimum lot. */
@Entity
@Table(name = "preipo_scrip")
public class PreIpoScrip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String isin;
    @Column(name = "face_value") private BigDecimal faceValue;
    @Column(name = "lot_size") private Integer lotSize;
    private boolean active;
    private String remarks;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected PreIpoScrip() {
    }

    public PreIpoScrip(String name) {
        this.name = name;
        this.active = true;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    public void update(String name, String isin, BigDecimal faceValue, Integer lotSize, boolean active, String remarks) {
        this.name = name;
        this.isin = isin;
        this.faceValue = faceValue;
        this.lotSize = lotSize;
        this.active = active;
        this.remarks = remarks;
        touch();
    }

    private void touch() {
        this.updatedBy = CurrentUser.username();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getIsin() { return isin; }
    public BigDecimal getFaceValue() { return faceValue; }
    public Integer getLotSize() { return lotSize; }
    public boolean isActive() { return active; }
    public String getRemarks() { return remarks; }
}
