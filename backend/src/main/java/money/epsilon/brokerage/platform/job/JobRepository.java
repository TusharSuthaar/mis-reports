package money.epsilon.brokerage.platform.job;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatusIn(Collection<JobStatus> statuses);

    java.util.Optional<Job> findFirstByTypeAndStatusInOrderByIdAsc(String type, Collection<JobStatus> statuses);
}
