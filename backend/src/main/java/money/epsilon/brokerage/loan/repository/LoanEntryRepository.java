package money.epsilon.brokerage.loan.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import money.epsilon.brokerage.loan.domain.LoanEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanEntryRepository extends JpaRepository<LoanEntry, Long> {

    List<LoanEntry> findByMonthOrderByDisbursalDateAscIdAsc(LocalDate month);

    List<LoanEntry> findByMonthBetweenOrderByMonthDescDisbursalDateDescIdDesc(LocalDate from, LocalDate to);

    long countByStatusAndPaymentStatusIn(LoanEntry.Status status, Collection<LoanEntry.PaymentStatus> paymentStatuses);

    /** Newest first: for suggestions (lenders, loan types, the payout % last used). */
    List<LoanEntry> findTop500ByStatusOrderByIdDesc(LoanEntry.Status status);

    /**
     * LoanEntrys of a month range narrowed in the database (audit M-04): active only unless cancelled ones are asked for,
     * one RM, one partner. Same order as findByMonthBetweenOrderByMonthDescDisbursalDateDescIdDesc.
     */
    @org.springframework.data.jpa.repository.Query("SELECT x FROM LoanEntry x WHERE x.month BETWEEN :from AND :to"
            + " AND (:withCancelled = TRUE OR x.status = :active) AND (:rmId IS NULL OR x.rmId = :rmId)"
            + " AND (:partnerId IS NULL OR x.partnerId = :partnerId) ORDER BY x.month DESC, x.disbursalDate DESC, x.id DESC")
    List<LoanEntry> search(@org.springframework.data.repository.query.Param("from") LocalDate from,
                     @org.springframework.data.repository.query.Param("to") LocalDate to,
                     @org.springframework.data.repository.query.Param("withCancelled") boolean withCancelled,
                     @org.springframework.data.repository.query.Param("active") LoanEntry.Status active,
                     @org.springframework.data.repository.query.Param("rmId") Long rmId,
                     @org.springframework.data.repository.query.Param("partnerId") Long partnerId);
}
