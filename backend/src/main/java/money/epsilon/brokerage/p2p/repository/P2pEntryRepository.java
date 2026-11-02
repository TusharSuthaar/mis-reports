package money.epsilon.brokerage.p2p.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.p2p.domain.P2pEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface P2pEntryRepository extends JpaRepository<P2pEntry, Long> {

    List<P2pEntry> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    /** Newest first: for suggestions (platforms, schemes). */
    List<P2pEntry> findTop500ByStatusOrderByIdDesc(P2pEntry.Status status);

    List<P2pEntry> findByMonthOrderByIdAsc(LocalDate month);

    /**
     * P2pEntrys of a month range narrowed in the database (audit M-04): active only unless cancelled ones are asked for,
     * one RM, one partner. Same order as findByMonthBetweenOrderByMonthDescIdDesc.
     */
    @org.springframework.data.jpa.repository.Query("SELECT x FROM P2pEntry x WHERE x.month BETWEEN :from AND :to"
            + " AND (:withCancelled = TRUE OR x.status = :active) AND (:rmId IS NULL OR x.rmId = :rmId)"
            + " AND (:partnerId IS NULL OR x.partnerId = :partnerId) ORDER BY x.month DESC, x.id DESC")
    List<P2pEntry> search(@org.springframework.data.repository.query.Param("from") LocalDate from,
                     @org.springframework.data.repository.query.Param("to") LocalDate to,
                     @org.springframework.data.repository.query.Param("withCancelled") boolean withCancelled,
                     @org.springframework.data.repository.query.Param("active") P2pEntry.Status active,
                     @org.springframework.data.repository.query.Param("rmId") Long rmId,
                     @org.springframework.data.repository.query.Param("partnerId") Long partnerId);

    /** Every entry of these clients, all months, newest first (the client page's holdings). */
    List<P2pEntry> findByClientIdInOrderByMonthDescIdDesc(java.util.Collection<Long> clientIds);
}
