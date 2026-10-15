package money.epsilon.brokerage.platform.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.EnumSet;
import money.epsilon.brokerage.platform.security.AppUserPrincipal;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.user.Role;
import money.epsilon.brokerage.platform.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository repository;
    private final JobRunner runner;
    private final ObjectMapper mapper;

    JobService(JobRepository repository, JobRunner runner, ObjectMapper mapper) {
        this.repository = repository;
        this.runner = runner;
        this.mapper = mapper;
    }

    /**
     * Queues a job and starts it in the background. If called inside a transaction, the job starts only after
     * that transaction commits, so it never runs for work that was rolled back.
     */
    @Transactional
    public long submit(String type, JsonNode payload) {
        if (!runner.supports(type)) {
            throw new IllegalArgumentException("Unknown job type: " + type);
        }
        long id = repository.save(new Job(type, runner.toJson(payload), CurrentUser.username())).getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runner.run(id);
            }
        });
        return id;
    }

    /** The submitter and ADMIN may see a job; anyone else gets 404. */
    @Transactional(readOnly = true)
    public JobView get(long id) {
        Job job = repository.findById(id).orElseThrow(() -> new NotFoundException("Job " + id + " not found"));
        boolean admin = CurrentUser.principal().map(AppUserPrincipal::role).orElse(null) == Role.ADMIN;
        if (!admin && !job.getCreatedBy().equals(CurrentUser.username())) {
            throw new NotFoundException("Job " + id + " not found");
        }
        return toView(job);
    }

    /** Work in flight when the app stopped cannot resume; mark it failed so nobody waits on it. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedJobs() {
        var interrupted = repository.findByStatusIn(EnumSet.of(JobStatus.QUEUED, JobStatus.RUNNING));
        interrupted.forEach(j -> j.fail("Interrupted by application restart"));
        if (!interrupted.isEmpty()) log.warn("Marked {} interrupted job(s) as FAILED", interrupted.size());
    }

    private JobView toView(Job j) {
        return new JobView(j.getId(), j.getType(), j.getStatus(), readJson(j.getResult()), j.getError(),
                j.getCreatedBy(), j.getCreatedAt(), j.getStartedAt(), j.getFinishedAt());
    }

    private JsonNode readJson(String json) {
        try {
            return json == null ? null : mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt job JSON", e);
        }
    }

    public record JobView(Long id, String type, JobStatus status, JsonNode result, String error, String createdBy,
                          Instant createdAt, Instant startedAt, Instant finishedAt) {
    }
}
