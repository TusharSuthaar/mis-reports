package money.epsilon.brokerage.preipo.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.preipo.domain.PreIpoPrice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreIpoPriceRepository extends JpaRepository<PreIpoPrice, Long> {

    Optional<PreIpoPrice> findByScripIdAndVendorIdAndAsOf(Long scripId, Long vendorId, LocalDate asOf);

    /** Every price on or before a day, newest first (the latest per scrip and vendor is the first seen). */
    List<PreIpoPrice> findByAsOfLessThanEqualOrderByAsOfDescIdDesc(LocalDate day);

    List<PreIpoPrice> findByScripIdOrderByAsOfDescIdDesc(Long scripId);
}
