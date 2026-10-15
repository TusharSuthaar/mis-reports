package money.epsilon.brokerage.org.repository;

import java.util.Optional;
import money.epsilon.brokerage.org.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByNameIgnoreCase(String name);
}
