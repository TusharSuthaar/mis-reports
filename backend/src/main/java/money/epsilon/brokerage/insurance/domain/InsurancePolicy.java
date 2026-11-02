package money.epsilon.brokerage.insurance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.security.CurrentUser;

/**
 * One insurance policy of a business month (R-94), as in the insurance team's "Policy level data" sheet. The client is
 * found by PAN and its RM comes from the client master; the POSP (if any) is a partner of type POSP.
 * <p>Expected commission = net premium × expected %; the POSP's % = expected % × agreed sharing %, and the POSP's
 * commission = net premium × the POSP's % (R-95). Either commission may be typed as an amount instead (as given): the sheet
 * types some.
 */
@Entity
@Table(name = "insurance_policy")
public class InsurancePolicy {

    public enum Status { ACTIVE, CANCELLED }

    public enum Category { LIFE, HEALTH, MOTOR, GENERAL }

    public enum BusinessType { NEW, RENEWAL, PORT, ENDORSEMENT, FYCI }

    public enum Source { SCREEN, UPLOAD }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate month;
    @Column(name = "client_id") private Long clientId;
    @Column(name = "rm_id") private Long rmId;
    @Column(name = "posp_id") private Long pospId;
    private String insurer;
    @Enumerated(EnumType.STRING) private Category category;
    @Column(name = "sub_type") private String subType;
    @Column(name = "product_name") private String productName;
    @Column(name = "policy_no") private String policyNo;
    @Enumerated(EnumType.STRING) @Column(name = "business_type") private BusinessType businessType;
    private String ppt;
    @Column(name = "policy_term") private Integer policyTerm;
    @Column(name = "sum_assured") private BigDecimal sumAssured;
    @Column(name = "od_premium") private BigDecimal odPremium;
    @Column(name = "tp_premium") private BigDecimal tpPremium;
    @Column(name = "total_premium") private BigDecimal totalPremium;
    @Column(name = "net_premium") private BigDecimal netPremium;
    @Column(name = "expected_percent") private BigDecimal expectedPercent;
    @Column(name = "expected_comm") private BigDecimal expectedComm;
    @Column(name = "expected_as_given") private boolean expectedAsGiven;
    @Column(name = "actual_percent") private BigDecimal actualPercent;
    @Column(name = "actual_comm") private BigDecimal actualComm;
    @Column(name = "posp_share") private BigDecimal pospShare;
    @Column(name = "posp_percent") private BigDecimal pospPercent;
    @Column(name = "posp_comm") private BigDecimal pospComm;
    @Column(name = "posp_as_given") private boolean pospAsGiven;
    private String remarks;
    @Enumerated(EnumType.STRING) private Source source;
    @Column(name = "upload_row") private Integer uploadRow;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected InsurancePolicy() {
    }

    public InsurancePolicy(long clientId, Source source, Integer uploadRow) {
        this.clientId = clientId;
        this.source = source;
        this.uploadRow = uploadRow;
        this.status = Status.ACTIVE;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /**
     * What is typed for a policy. Percentages are in percent (15 = 15%). {@code expectedComm} / {@code pospComm} are typed
     * amounts that stand instead of the calculation; null = calculate.
     */
    public record Details(LocalDate month, long rmId, Long pospId, String insurer, Category category, String subType, String productName,
                          String policyNo, BusinessType businessType, String ppt, Integer policyTerm, BigDecimal sumAssured,
                          BigDecimal odPremium, BigDecimal tpPremium, BigDecimal totalPremium, BigDecimal netPremium,
                          BigDecimal expectedPercent, BigDecimal expectedComm, BigDecimal actualPercent, BigDecimal actualComm,
                          BigDecimal pospShare, BigDecimal pospComm, String remarks) {
    }

    /** The commissions of a policy (R-95), worked out from what was typed. */
    public record Figures(BigDecimal expectedComm, boolean expectedAsGiven, BigDecimal actualComm, BigDecimal pospPercent,
                          BigDecimal pospComm, boolean pospAsGiven) {
    }

    public static Figures figures(Details d) {
        BigDecimal net = d.netPremium();
        boolean expGiven = d.expectedComm() != null;
        BigDecimal expected = expGiven ? d.expectedComm().setScale(2, RoundingMode.HALF_UP) : percentOf(net, d.expectedPercent());
        BigDecimal actual = d.actualPercent() != null ? percentOf(net, d.actualPercent())
                : d.actualComm() == null ? null : d.actualComm().setScale(2, RoundingMode.HALF_UP);
        BigDecimal pospPct = d.pospId() == null || d.pospShare() == null || d.expectedPercent() == null ? null
                : d.expectedPercent().multiply(d.pospShare()).divide(HUNDRED, 6, RoundingMode.HALF_UP);
        boolean pospGiven = d.pospId() != null && d.pospComm() != null;
        BigDecimal posp = d.pospId() == null ? BigDecimal.ZERO.setScale(2)
                : pospGiven ? d.pospComm().setScale(2, RoundingMode.HALF_UP)
                : pospPct != null ? percentOf(net, pospPct)
                : d.pospShare() != null ? percentOf(expected, d.pospShare())
                : BigDecimal.ZERO.setScale(2);
        return new Figures(expected, expGiven, actual, pospPct, posp, pospGiven);
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        if (amount == null || percent == null) return BigDecimal.ZERO.setScale(2);
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public void fill(Details d) {
        Figures f = figures(d);
        this.month = d.month();
        this.rmId = d.rmId();
        this.pospId = d.pospId();
        this.insurer = d.insurer();
        this.category = d.category();
        this.subType = d.subType();
        this.productName = d.productName();
        this.policyNo = d.policyNo();
        this.businessType = d.businessType();
        this.ppt = d.ppt();
        this.policyTerm = d.policyTerm();
        this.sumAssured = d.sumAssured();
        this.odPremium = d.odPremium();
        this.tpPremium = d.tpPremium();
        this.totalPremium = d.totalPremium();
        this.netPremium = d.netPremium();
        this.expectedPercent = d.expectedPercent();
        this.expectedComm = f.expectedComm();
        this.expectedAsGiven = f.expectedAsGiven();
        this.actualPercent = d.actualPercent();
        this.actualComm = f.actualComm();
        this.pospShare = d.pospId() == null ? null : d.pospShare();
        this.pospPercent = f.pospPercent();
        this.pospComm = f.pospComm();
        this.pospAsGiven = f.pospAsGiven();
        this.remarks = d.remarks();
        touch();
    }

    /** The client is corrected (e.g. the policy was mapped to the wrong PAN): its RM comes with it. */
    public void moveTo(long clientId) {
        this.clientId = clientId;
        touch();
    }

    public void cancel() {
        this.status = Status.CANCELLED;
        touch();
    }

    private void touch() {
        this.updatedBy = CurrentUser.username();
        this.updatedAt = Instant.now();
    }

    public Details details() {
        return new Details(month, rmId, pospId, insurer, category, subType, productName, policyNo, businessType, ppt, policyTerm, sumAssured,
                odPremium, tpPremium, totalPremium, netPremium, expectedPercent, expectedAsGiven ? expectedComm : null, actualPercent,
                actualPercent == null ? actualComm : null, pospShare, pospAsGiven ? pospComm : null, remarks);
    }

    public Long getId() { return id; }
    public LocalDate getMonth() { return month; }
    public Long getClientId() { return clientId; }
    public Long getRmId() { return rmId; }
    public Long getPospId() { return pospId; }
    public String getInsurer() { return insurer; }
    public Category getCategory() { return category; }
    public String getSubType() { return subType; }
    public String getProductName() { return productName; }
    public String getPolicyNo() { return policyNo; }
    public BusinessType getBusinessType() { return businessType; }
    public String getPpt() { return ppt; }
    public Integer getPolicyTerm() { return policyTerm; }
    public BigDecimal getSumAssured() { return sumAssured; }
    public BigDecimal getOdPremium() { return odPremium; }
    public BigDecimal getTpPremium() { return tpPremium; }
    public BigDecimal getTotalPremium() { return totalPremium; }
    public BigDecimal getNetPremium() { return netPremium; }
    public BigDecimal getExpectedPercent() { return expectedPercent; }
    public BigDecimal getExpectedComm() { return expectedComm; }
    public boolean isExpectedAsGiven() { return expectedAsGiven; }
    public BigDecimal getActualPercent() { return actualPercent; }
    public BigDecimal getActualComm() { return actualComm; }
    public BigDecimal getPospShare() { return pospShare; }
    public BigDecimal getPospPercent() { return pospPercent; }
    public BigDecimal getPospComm() { return pospComm; }
    public boolean isPospAsGiven() { return pospAsGiven; }
    public String getRemarks() { return remarks; }
    public Source getSource() { return source; }
    public Integer getUploadRow() { return uploadRow; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
