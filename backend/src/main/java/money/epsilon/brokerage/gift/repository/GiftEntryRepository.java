package money.epsilon.brokerage.gift.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import money.epsilon.brokerage.gift.domain.GiftEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GiftEntryRepository extends JpaRepository<GiftEntry, Long> {

    List<GiftEntry> findByMonthOrderByIdAsc(LocalDate month);

    List<GiftEntry> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    long countByStatusAndPaymentStatusIn(GiftEntry.Status status, Collection<GiftEntry.PaymentStatus> paymentStatuses);

    /** Newest first: for suggestions (fund names). */
    List<GiftEntry> findTop500ByStatusOrderByIdDesc(GiftEntry.Status status);

    /**
     * GiftEntrys of a month range narrowed in the database (audit M-04): active only unless cancelled ones are asked for,
     * one RM, one partner. Same order as findByMonthBetweenOrderByMonthDescIdDesc.
     */
    @org.springframework.data.jpa.repository.Query("SELECT x FROM GiftEntry x WHERE x.month BETWEEN :from AND :to"
            + " AND (:withCancelled = TRUE OR x.status = :active) AND (:rmId IS NULL OR x.rmId = :rmId)"
            + " AND (:partnerId IS NULL OR x.partnerId = :partnerId) ORDER BY x.month DESC, x.id DESC")
    List<GiftEntry> search(@org.springframework.data.repository.query.Param("from") LocalDate from,
                     @org.springframework.data.repository.query.Param("to") LocalDate to,
                     @org.springframework.data.repository.query.Param("withCancelled") boolean withCancelled,
                     @org.springframework.data.repository.query.Param("active") GiftEntry.Status active,
                     @org.springframework.data.repository.query.Param("rmId") Long rmId,
                     @org.springframework.data.repository.query.Param("partnerId") Long partnerId);
}
