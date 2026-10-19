package money.epsilon.brokerage.org.repository;

import java.util.Optional;
import money.epsilon.brokerage.org.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByNameIgnoreCase(String name);

    @org.springframework.data.jpa.repository.Query("select coalesce(max(r.position), 0) from Region r")
    int maxPosition();
}
