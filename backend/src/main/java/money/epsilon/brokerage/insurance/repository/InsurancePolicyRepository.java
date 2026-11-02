package money.epsilon.brokerage.insurance.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {

    List<InsurancePolicy> findByMonthOrderByIdAsc(LocalDate month);

    List<InsurancePolicy> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    List<InsurancePolicy> findTop500ByStatusOrderByIdDesc(InsurancePolicy.Status status);
}
