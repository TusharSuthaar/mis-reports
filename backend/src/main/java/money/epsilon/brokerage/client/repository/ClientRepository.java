package money.epsilon.brokerage.client.repository;

import money.epsilon.brokerage.client.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
}
