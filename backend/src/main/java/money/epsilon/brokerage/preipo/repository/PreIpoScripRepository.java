package money.epsilon.brokerage.preipo.repository;

import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.preipo.domain.PreIpoScrip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreIpoScripRepository extends JpaRepository<PreIpoScrip, Long> {

    List<PreIpoScrip> findAllByOrderByNameAsc();

    Optional<PreIpoScrip> findByNameIgnoreCase(String name);

    Optional<PreIpoScrip> findByIsinIgnoreCase(String isin);
}
