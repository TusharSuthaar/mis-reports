package money.epsilon.brokerage.org.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.org.domain.RmBranchAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RmBranchAssignmentRepository extends JpaRepository<RmBranchAssignment, Long> {

    Optional<RmBranchAssignment> findFirstByEmployeeIdOrderByValidFromDesc(Long employeeId);

    Optional<RmBranchAssignment> findFirstByEmployeeIdOrderByValidFromAsc(Long employeeId);

    List<RmBranchAssignment> findByEmployeeIdOrderByValidFromDesc(Long employeeId);

    @Query("select a from RmBranchAssignment a where a.validFrom <= :day and (a.validTo is null or a.validTo >= :day)")
    List<RmBranchAssignment> findAllOn(LocalDate day);
}
