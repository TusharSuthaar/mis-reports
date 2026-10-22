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

    /** Every entry of these clients, all months, newest first (the client page's holdings). */
    List<LoanEntry> findByClientIdInOrderByMonthDescIdDesc(java.util.Collection<Long> clientIds);
}
