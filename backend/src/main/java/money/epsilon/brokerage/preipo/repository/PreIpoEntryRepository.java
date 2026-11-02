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

    /**
     * PreIpoEntrys of a month range narrowed in the database (audit M-04): active only unless cancelled ones are asked for,
     * one RM, one partner. Same order as findByMonthBetweenOrderByMonthDescDealDateDescIdDesc.
     */
    @org.springframework.data.jpa.repository.Query("SELECT x FROM PreIpoEntry x WHERE x.month BETWEEN :from AND :to"
            + " AND (:withCancelled = TRUE OR x.status = :active) AND (:rmId IS NULL OR x.rmId = :rmId)"
            + " AND (:partnerId IS NULL OR x.partnerId = :partnerId) ORDER BY x.month DESC, x.dealDate DESC, x.id DESC")
    List<PreIpoEntry> search(@org.springframework.data.repository.query.Param("from") LocalDate from,
                     @org.springframework.data.repository.query.Param("to") LocalDate to,
                     @org.springframework.data.repository.query.Param("withCancelled") boolean withCancelled,
                     @org.springframework.data.repository.query.Param("active") PreIpoEntry.Status active,
                     @org.springframework.data.repository.query.Param("rmId") Long rmId,
                     @org.springframework.data.repository.query.Param("partnerId") Long partnerId);
}
