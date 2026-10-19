package money.epsilon.brokerage.tax.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.tax.domain.ProductTaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductTaxRateRepository extends JpaRepository<ProductTaxRate, Long> {

    Optional<ProductTaxRate> findFirstByProductTypeOrderByValidFromDesc(String productType);

    List<ProductTaxRate> findByProductTypeOrderByValidFromDesc(String productType);

    @Query("""
            select r from ProductTaxRate r where r.productType = :productType
            and r.validFrom <= :day and (r.validTo is null or r.validTo >= :day)""")
    Optional<ProductTaxRate> findOn(String productType, LocalDate day);
}
