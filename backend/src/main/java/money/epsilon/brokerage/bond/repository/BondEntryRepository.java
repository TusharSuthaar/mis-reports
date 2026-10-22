package money.epsilon.brokerage.bond.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import money.epsilon.brokerage.bond.domain.BondEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BondEntryRepository extends JpaRepository<BondEntry, Long> {

    List<BondEntry> findByMonthOrderByInvestmentDateAscIdAsc(LocalDate month);

    List<BondEntry> findByMonthBetweenOrderByMonthDescInvestmentDateDescIdDesc(LocalDate from, LocalDate to);

    long countByStatusAndPaymentStatusIn(BondEntry.Status status, Collection<BondEntry.PaymentStatus> paymentStatuses);

    /** Newest first: for suggestions (bond names, ISINs, the mark-up last used). */
    List<BondEntry> findTop500ByStatusOrderByIdDesc(BondEntry.Status status);

    /** Every entry of these clients, all months, newest first (the client page's holdings). */
    List<BondEntry> findByClientIdInOrderByMonthDescIdDesc(java.util.Collection<Long> clientIds);
}
