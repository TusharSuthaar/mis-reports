package money.epsilon.brokerage.preipo.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import money.epsilon.brokerage.preipo.domain.PreIpoEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreIpoEntryRepository extends JpaRepository<PreIpoEntry, Long> {

    List<PreIpoEntry> findByMonthOrderByDealDateAscIdAsc(LocalDate month);

    List<PreIpoEntry> findByMonthBetweenOrderByMonthDescDealDateDescIdDesc(LocalDate from, LocalDate to);

    long countByStatusAndBillingStatusIn(PreIpoEntry.Status status, Collection<PreIpoEntry.BillingStatus> billing);

    /** Every entry of these clients, all months, newest first (the client page's holdings). */
    List<PreIpoEntry> findByClientIdInOrderByMonthDescIdDesc(java.util.Collection<Long> clientIds);
}
