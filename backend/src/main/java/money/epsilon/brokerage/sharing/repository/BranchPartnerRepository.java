package money.epsilon.brokerage.sharing.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.sharing.domain.BranchPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BranchPartnerRepository extends JpaRepository<BranchPartner, Long> {

    Optional<BranchPartner> findFirstByBranchIdOrderByValidFromDesc(Long branchId);

    List<BranchPartner> findByBranchIdOrderByValidFromDesc(Long branchId);

    @Query("select b from BranchPartner b where b.validFrom <= :day and (b.validTo is null or b.validTo >= :day)")
    List<BranchPartner> findAllOn(LocalDate day);
}
