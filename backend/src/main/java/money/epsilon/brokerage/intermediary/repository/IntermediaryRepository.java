package money.epsilon.brokerage.intermediary.repository;

import java.util.Optional;
import money.epsilon.brokerage.intermediary.domain.Intermediary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntermediaryRepository extends JpaRepository<Intermediary, Long> {

    Optional<Intermediary> findByNameIgnoreCase(String name);
}
