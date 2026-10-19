package money.epsilon.brokerage.intermediary.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.intermediary.domain.IntermediaryTerms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IntermediaryTermsRepository extends JpaRepository<IntermediaryTerms, Long> {

    Optional<IntermediaryTerms> findFirstByIntermediaryIdOrderByValidFromDesc(Long intermediaryId);

    List<IntermediaryTerms> findByIntermediaryIdOrderByValidFromDesc(Long intermediaryId);

    @Query("""
            select t from IntermediaryTerms t where t.intermediaryId = :id
            and t.validFrom <= :day and (t.validTo is null or t.validTo >= :day)""")
    Optional<IntermediaryTerms> findOn(Long id, LocalDate day);
}
