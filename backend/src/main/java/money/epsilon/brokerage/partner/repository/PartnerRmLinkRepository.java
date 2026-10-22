package money.epsilon.brokerage.partner.repository;

import java.util.List;
import java.util.Optional;
import money.epsilon.brokerage.partner.domain.PartnerRmLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRmLinkRepository extends JpaRepository<PartnerRmLink, Long> {

    Optional<PartnerRmLink> findFirstByPartnerIdAndRmIdOrderByValidFromDesc(Long partnerId, Long rmId);

    List<PartnerRmLink> findByPartnerIdOrderByValidFromDesc(Long partnerId);

    List<PartnerRmLink> findByRmIdAndValidToIsNull(Long rmId);

    List<PartnerRmLink> findByRmIdAndValidFromAfter(Long rmId, java.time.LocalDate day);

    long countByPartnerIdAndValidToIsNull(Long partnerId);

    /** partner id, number of RMs linked now — for every partner with any. */
    @org.springframework.data.jpa.repository.Query("select l.partnerId, count(l) from PartnerRmLink l where l.validTo is null group by l.partnerId")
    List<Object[]> countOpenByPartner();
}
