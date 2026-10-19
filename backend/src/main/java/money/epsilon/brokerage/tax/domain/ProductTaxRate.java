package money.epsilon.brokerage.tax.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.dated.DatedRow;

/** GST and TDS of one product over a period (R-71). Percent values: 18 = 18%. */
@Entity
@Table(name = "product_tax_rate")
public class ProductTaxRate extends DatedRow {

    @Column(name = "product_type")
    private String productType;

    @Column(name = "gst_percent")
    private BigDecimal gstPercent;

    @Column(name = "tds_percent")
    private BigDecimal tdsPercent;

    /** Whether a new entry starts with TDS ticked (the entry can still change it). */
    @Column(name = "tds_by_default")
    private boolean tdsByDefault;

    protected ProductTaxRate() {
    }

    public ProductTaxRate(String productType, BigDecimal gstPercent, BigDecimal tdsPercent, boolean tdsByDefault, LocalDate from, String reason) {
        super(from, null, reason);
        this.productType = productType;
        this.gstPercent = gstPercent;
        this.tdsPercent = tdsPercent;
        this.tdsByDefault = tdsByDefault;
    }

    public String getProductType() { return productType; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public BigDecimal getTdsPercent() { return tdsPercent; }
    public boolean isTdsByDefault() { return tdsByDefault; }

    public boolean sameAs(BigDecimal gst, BigDecimal tds, boolean byDefault) {
        return gstPercent.compareTo(gst) == 0 && tdsPercent.compareTo(tds) == 0 && tdsByDefault == byDefault;
    }

    /** A correction from this row's first day (audited by the caller). */
    public void correct(BigDecimal gst, BigDecimal tds, boolean byDefault, String reason) {
        this.gstPercent = gst;
        this.tdsPercent = tds;
        this.tdsByDefault = byDefault;
        setReason(reason);
    }
}
