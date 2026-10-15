package money.epsilon.brokerage.org.domain;

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
import java.time.LocalDate;

@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emp_code")
    private String empCode;

    private String name;

    @Enumerated(EnumType.STRING)
    private EmployeeType type;

    @Enumerated(EnumType.STRING)
    private EmployeeStatus status;

    @Column(name = "joined_on")
    private LocalDate joinedOn;

    @Column(name = "exited_on")
    private LocalDate exitedOn;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Employee() {
    }

    public Employee(String empCode, String name, EmployeeType type, LocalDate joinedOn) {
        this.empCode = empCode;
        this.name = name;
        this.type = type;
        this.joinedOn = joinedOn;
        this.status = EmployeeStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** True if the day falls inside the employment period (open ends allowed). */
    public boolean employedOn(LocalDate day) {
        return (joinedOn == null || !day.isBefore(joinedOn)) && (exitedOn == null || !day.isAfter(exitedOn));
    }

    public void exit(LocalDate lastDay) {
        this.exitedOn = lastDay;
        this.status = EmployeeStatus.EXITED;
    }

    public Long getId() { return id; }
    public String getEmpCode() { return empCode; }
    public String getName() { return name; }
    public EmployeeType getType() { return type; }
    public EmployeeStatus getStatus() { return status; }
    public LocalDate getJoinedOn() { return joinedOn; }
    public LocalDate getExitedOn() { return exitedOn; }

    public void setName(String name) { this.name = name; }
    public void setType(EmployeeType type) { this.type = type; }
    public void setJoinedOn(LocalDate joinedOn) { this.joinedOn = joinedOn; }
}
