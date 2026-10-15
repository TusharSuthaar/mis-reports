package money.epsilon.brokerage.partner.repository;

import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.partner.domain.Partner;
import money.epsilon.brokerage.partner.domain.PartnerType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    Optional<Partner> findByTypeAndNameIgnoreCase(PartnerType type, String name);

    List<Partner> findByTypeAndPanHash(PartnerType type, String panHash);
}
