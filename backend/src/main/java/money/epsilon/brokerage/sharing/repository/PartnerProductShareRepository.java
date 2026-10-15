package money.epsilon.brokerage.sharing.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.sharing.domain.PartnerProductShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartnerProductShareRepository extends JpaRepository<PartnerProductShare, Long> {

    Optional<PartnerProductShare> findFirstByPartnerIdAndProductTypeOrderByValidFromDesc(Long partnerId, String productType);

    List<PartnerProductShare> findByPartnerIdOrderByProductTypeAscValidFromDesc(Long partnerId);

    List<PartnerProductShare> findByValidToIsNull();

    @Query("""
            select s from PartnerProductShare s where s.partnerId in :partnerIds and s.productType = :productType
            and s.validFrom <= :day and (s.validTo is null or s.validTo >= :day)""")
    List<PartnerProductShare> findAllOn(Collection<Long> partnerIds, String productType, LocalDate day);
}
