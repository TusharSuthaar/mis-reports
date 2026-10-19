package money.epsilon.brokerage.client.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.client.domain.ClientRmAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClientRmAssignmentRepository extends JpaRepository<ClientRmAssignment, Long> {

    Optional<ClientRmAssignment> findFirstByClientIdOrderByValidFromDesc(Long clientId);

    List<ClientRmAssignment> findByClientIdOrderByValidFromDesc(Long clientId);

    List<ClientRmAssignment> findByClientIdInAndValidToIsNull(Collection<Long> clientIds);

    List<ClientRmAssignment> findByRmIdAndValidToIsNull(Long rmId);

    List<ClientRmAssignment> findByRmIdAndValidFromAfter(Long rmId, LocalDate day);

    @Query("select a from ClientRmAssignment a where a.clientId = :clientId and a.validFrom <= :day and (a.validTo is null or a.validTo >= :day)")
    Optional<ClientRmAssignment> findOn(Long clientId, LocalDate day);

    @Query("select a from ClientRmAssignment a where a.clientId in :clientIds and a.validFrom <= :day and (a.validTo is null or a.validTo >= :day)")
    List<ClientRmAssignment> findAllOn(Collection<Long> clientIds, LocalDate day);
}
