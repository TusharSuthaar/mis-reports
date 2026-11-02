package money.epsilon.brokerage.period.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import money.epsilon.brokerage.period.domain.Period;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeriodRepository extends JpaRepository<Period, Long> {

    /** The month with its row locked until the transaction ends: process, lock, unlock and entry changes wait for each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Period p WHERE p.id = :id")
    Optional<Period> findForChange(@Param("id") long id);

    Optional<Period> findByProductTypeAndMonth(String productType, LocalDate month);

    List<Period> findByProductTypeOrderByMonthDesc(String productType);
}
