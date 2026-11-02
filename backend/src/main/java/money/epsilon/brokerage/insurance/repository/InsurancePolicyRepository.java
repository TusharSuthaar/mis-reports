package money.epsilon.brokerage.insurance.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {

    List<InsurancePolicy> findByMonthOrderByIdAsc(LocalDate month);

    List<InsurancePolicy> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    List<InsurancePolicy> findTop500ByStatusOrderByIdDesc(InsurancePolicy.Status status);

    /**
     * InsurancePolicys of a month range narrowed in the database (audit M-04): active only unless cancelled ones are asked for,
     * one RM, one POSP. Same order as findByMonthBetweenOrderByMonthDescIdDesc.
     */
    @org.springframework.data.jpa.repository.Query("SELECT x FROM InsurancePolicy x WHERE x.month BETWEEN :from AND :to"
            + " AND (:withCancelled = TRUE OR x.status = :active) AND (:rmId IS NULL OR x.rmId = :rmId)"
            + " AND (:pospId IS NULL OR x.pospId = :pospId) ORDER BY x.month DESC, x.id DESC")
    List<InsurancePolicy> search(@org.springframework.data.repository.query.Param("from") LocalDate from,
                     @org.springframework.data.repository.query.Param("to") LocalDate to,
                     @org.springframework.data.repository.query.Param("withCancelled") boolean withCancelled,
                     @org.springframework.data.repository.query.Param("active") InsurancePolicy.Status active,
                     @org.springframework.data.repository.query.Param("rmId") Long rmId,
                     @org.springframework.data.repository.query.Param("pospId") Long pospId);
}
