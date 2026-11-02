package money.epsilon.brokerage.pms.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.pms.domain.PmsInvestment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmsInvestmentRepository extends JpaRepository<PmsInvestment, Long> {

    List<PmsInvestment> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    /** Newest first: for suggestions (AMCs, strategies). */
    List<PmsInvestment> findTop500ByStatusOrderByIdDesc(PmsInvestment.Status status);

    /**
     * PmsInvestments of a month range narrowed in the database (audit M-04): active only unless cancelled ones are asked for,
     * one RM, one partner. Same order as findByMonthBetweenOrderByMonthDescIdDesc.
     */
    @org.springframework.data.jpa.repository.Query("SELECT x FROM PmsInvestment x WHERE x.month BETWEEN :from AND :to"
            + " AND (:withCancelled = TRUE OR x.status = :active) AND (:rmId IS NULL OR x.rmId = :rmId)"
            + " AND (:partnerId IS NULL OR x.partnerId = :partnerId) ORDER BY x.month DESC, x.id DESC")
    List<PmsInvestment> search(@org.springframework.data.repository.query.Param("from") LocalDate from,
                     @org.springframework.data.repository.query.Param("to") LocalDate to,
                     @org.springframework.data.repository.query.Param("withCancelled") boolean withCancelled,
                     @org.springframework.data.repository.query.Param("active") PmsInvestment.Status active,
                     @org.springframework.data.repository.query.Param("rmId") Long rmId,
                     @org.springframework.data.repository.query.Param("partnerId") Long partnerId);
}
