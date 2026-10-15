package money.epsilon.brokerage.ingestion.upload;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface UploadRepository extends JpaRepository<Upload, Long> {

    boolean existsBySha256AndStatusNot(String sha256, Upload.Status status);

    Optional<Upload> findFirstByPeriodIdAndStatus(Long periodId, Upload.Status status);

    List<Upload> findByPeriodIdInOrderByUploadedAtDesc(Collection<Long> periodIds);
}
