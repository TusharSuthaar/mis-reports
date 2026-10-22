package money.epsilon.brokerage.fd.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.fd.domain.FdEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FdEntryRepository extends JpaRepository<FdEntry, Long> {

    List<FdEntry> findByMonthOrderByDepositDateAscIdAsc(LocalDate month);

    List<FdEntry> findByMonthBetweenOrderByMonthDescDepositDateDescIdDesc(LocalDate from, LocalDate to);

    long countByStatusAndPaymentStatusIn(FdEntry.Status status, java.util.Collection<FdEntry.PaymentStatus> paymentStatuses);

    /** Newest first: for suggestions (company and platform names, the margin last used). */
    List<FdEntry> findTop500ByStatusOrderByIdDesc(FdEntry.Status status);

    /** Every entry of these clients, all months, newest first (the client page's holdings). */
    List<FdEntry> findByClientIdInOrderByMonthDescIdDesc(java.util.Collection<Long> clientIds);
}
