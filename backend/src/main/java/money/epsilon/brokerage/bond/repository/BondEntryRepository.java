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

    /**
     * BondEntrys of a month range narrowed in the database (audit M-04): active only unless cancelled ones are asked for,
     * one RM, one partner. Same order as findByMonthBetweenOrderByMonthDescInvestmentDateDescIdDesc.
     */
    @org.springframework.data.jpa.repository.Query("SELECT x FROM BondEntry x WHERE x.month BETWEEN :from AND :to"
            + " AND (:withCancelled = TRUE OR x.status = :active) AND (:rmId IS NULL OR x.rmId = :rmId)"
            + " AND (:partnerId IS NULL OR x.partnerId = :partnerId) ORDER BY x.month DESC, x.investmentDate DESC, x.id DESC")
    List<BondEntry> search(@org.springframework.data.repository.query.Param("from") LocalDate from,
                     @org.springframework.data.repository.query.Param("to") LocalDate to,
                     @org.springframework.data.repository.query.Param("withCancelled") boolean withCancelled,
                     @org.springframework.data.repository.query.Param("active") BondEntry.Status active,
                     @org.springframework.data.repository.query.Param("rmId") Long rmId,
                     @org.springframework.data.repository.query.Param("partnerId") Long partnerId);
}
