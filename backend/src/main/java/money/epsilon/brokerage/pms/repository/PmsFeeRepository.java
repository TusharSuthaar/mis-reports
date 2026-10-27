package money.epsilon.brokerage.pms.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.pms.domain.PmsFee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmsFeeRepository extends JpaRepository<PmsFee, Long> {

    List<PmsFee> findByMonthOrderByIdAsc(LocalDate month);

    List<PmsFee> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);
}
