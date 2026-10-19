package money.epsilon.brokerage.org.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "region")
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** Place in lists and in the END region section (1 = first). */
    private int position;

    /** END region section: show this region's branches as rows above it. */
    @Column(name = "end_branch_rows")
    private boolean endBranchRows;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Region() {
    }

    public Region(String name, int position) {
        this.name = name;
        this.position = position;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public boolean isEndBranchRows() { return endBranchRows; }
    public void setEndBranchRows(boolean endBranchRows) { this.endBranchRows = endBranchRows; }
}
