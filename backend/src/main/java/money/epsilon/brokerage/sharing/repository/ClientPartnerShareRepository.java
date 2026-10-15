package money.epsilon.brokerage.sharing.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.sharing.domain.ClientPartnerShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClientPartnerShareRepository extends JpaRepository<ClientPartnerShare, Long> {

    Optional<ClientPartnerShare> findFirstByClientIdAndProductTypeAndPartnerRoleOrderByValidFromDesc(Long clientId, String productType,
                                                                                                    PartnerType role);

    List<ClientPartnerShare> findByClientIdAndProductTypeOrderByValidFromDesc(Long clientId, String productType);

    List<ClientPartnerShare> findByClientIdInAndProductTypeAndValidToIsNull(Collection<Long> clientIds, String productType);

    @Query("""
            select s from ClientPartnerShare s where s.clientId in :clientIds and s.productType = :productType
            and s.validFrom <= :day and (s.validTo is null or s.validTo >= :day)""")
    List<ClientPartnerShare> findAllOn(Collection<Long> clientIds, String productType, java.time.LocalDate day);

    @Query("select s.partnerId, count(s) from ClientPartnerShare s where s.validTo is null and s.productType = :productType group by s.partnerId")
    List<Object[]> countOpenByPartner(String productType);
}
