package money.epsilon.brokerage.aif.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import money.epsilon.brokerage.aif.domain.AifEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AifEntryRepository extends JpaRepository<AifEntry, Long> {

    List<AifEntry> findByMonthOrderByIdAsc(LocalDate month);

    List<AifEntry> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    long countByStatusAndPaymentStatusIn(AifEntry.Status status, Collection<AifEntry.PaymentStatus> paymentStatuses);

    /** Newest first: for suggestions (fund names, the mark-up last used). */
    List<AifEntry> findTop500ByStatusOrderByIdDesc(AifEntry.Status status);

    /**
     * AifEntrys of a month range narrowed in the database (audit M-04): active only unless cancelled ones are asked for,
     * one RM, one partner. Same order as findByMonthBetweenOrderByMonthDescIdDesc.
     */
    @org.springframework.data.jpa.repository.Query("SELECT x FROM AifEntry x WHERE x.month BETWEEN :from AND :to"
            + " AND (:withCancelled = TRUE OR x.status = :active) AND (:rmId IS NULL OR x.rmId = :rmId)"
            + " AND (:partnerId IS NULL OR x.partnerId = :partnerId) ORDER BY x.month DESC, x.id DESC")
    List<AifEntry> search(@org.springframework.data.repository.query.Param("from") LocalDate from,
                     @org.springframework.data.repository.query.Param("to") LocalDate to,
                     @org.springframework.data.repository.query.Param("withCancelled") boolean withCancelled,
                     @org.springframework.data.repository.query.Param("active") AifEntry.Status active,
                     @org.springframework.data.repository.query.Param("rmId") Long rmId,
                     @org.springframework.data.repository.query.Param("partnerId") Long partnerId);

    /** Every entry of these clients, all months, newest first (the client page's holdings). */
    List<AifEntry> findByClientIdInOrderByMonthDescIdDesc(java.util.Collection<Long> clientIds);
}
