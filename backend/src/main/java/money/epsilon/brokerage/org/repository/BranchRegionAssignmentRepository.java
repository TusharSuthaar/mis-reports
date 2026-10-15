package money.epsilon.brokerage.org.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.org.domain.BranchRegionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BranchRegionAssignmentRepository extends JpaRepository<BranchRegionAssignment, Long> {

    Optional<BranchRegionAssignment> findFirstByBranchIdOrderByValidFromDesc(Long branchId);

    List<BranchRegionAssignment> findByBranchIdOrderByValidFromDesc(Long branchId);

    @Query("select a from BranchRegionAssignment a where a.validFrom <= :day and (a.validTo is null or a.validTo >= :day)")
    List<BranchRegionAssignment> findAllOn(LocalDate day);
}
