package money.epsilon.brokerage.client.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.client.domain.ClientExternalId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientExternalIdRepository extends JpaRepository<ClientExternalId, Long> {

    Optional<ClientExternalId> findBySourceAndCode(String source, String code);

    List<ClientExternalId> findBySourceAndCodeIn(String source, Collection<String> codes);

    List<ClientExternalId> findByClientIdIn(Collection<Long> clientIds);
}
