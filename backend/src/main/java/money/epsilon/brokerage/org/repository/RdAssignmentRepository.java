package money.epsilon.brokerage.org.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.org.domain.RdAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RdAssignmentRepository extends JpaRepository<RdAssignment, Long> {

    Optional<RdAssignment> findFirstByRegionIdOrderByValidFromDesc(Long regionId);

    Optional<RdAssignment> findFirstByEmployeeIdOrderByValidFromDesc(Long employeeId);

    List<RdAssignment> findByRegionIdOrderByValidFromDesc(Long regionId);

    /** A person can be RD of several regions at once. */
    List<RdAssignment> findByEmployeeIdAndValidToIsNull(Long employeeId);

    @Query("select a from RdAssignment a where a.validFrom <= :day and (a.validTo is null or a.validTo >= :day)")
    List<RdAssignment> findAllOn(LocalDate day);
}
