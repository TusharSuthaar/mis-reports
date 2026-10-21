package money.epsilon.brokerage.pms.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.pms.domain.PmsPayout;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmsPayoutRepository extends JpaRepository<PmsPayout, Long> {

    List<PmsPayout> findByPeriodFromBetweenOrderByPeriodFromDescIdDesc(LocalDate from, LocalDate to);
}
