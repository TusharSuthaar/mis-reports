package money.epsilon.brokerage.pms.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.pms.domain.PmsInvestment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmsInvestmentRepository extends JpaRepository<PmsInvestment, Long> {

    List<PmsInvestment> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    /** Newest first: for suggestions (AMCs, strategies). */
    List<PmsInvestment> findTop500ByStatusOrderByIdDesc(PmsInvestment.Status status);

    /** Every entry of these clients, all months, newest first (the client page's holdings). */
    List<PmsInvestment> findByClientIdInOrderByMonthDescIdDesc(java.util.Collection<Long> clientIds);
}
