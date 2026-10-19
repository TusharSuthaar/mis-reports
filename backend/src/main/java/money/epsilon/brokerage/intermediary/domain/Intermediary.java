package money.epsilon.brokerage.intermediary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A platform an investment is booked through (e.g. a bond platform). Shown as "Platform" on the screens. */
@Entity
@Table(name = "intermediary")
public class Intermediary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Intermediary() {
    }

    public Intermediary(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
