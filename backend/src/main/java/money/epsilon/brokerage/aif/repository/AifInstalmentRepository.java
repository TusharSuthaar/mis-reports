package money.epsilon.brokerage.aif.repository;

import java.util.Collection;
import java.util.List;
import money.epsilon.brokerage.aif.domain.AifInstalment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AifInstalmentRepository extends JpaRepository<AifInstalment, Long> {

    List<AifInstalment> findByAifEntryIdInOrderByAifEntryIdAscSeqAsc(Collection<Long> aifEntryIds);

    void deleteByAifEntryId(Long aifEntryId);
}
