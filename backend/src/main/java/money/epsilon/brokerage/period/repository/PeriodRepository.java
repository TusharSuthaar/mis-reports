package money.epsilon.brokerage.period.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.period.domain.Period;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodRepository extends JpaRepository<Period, Long> {

    Optional<Period> findByProductTypeAndMonth(String productType, LocalDate month);

    List<Period> findByProductTypeOrderByMonthDesc(String productType);
}
