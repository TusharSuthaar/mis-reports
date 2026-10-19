package money.epsilon.brokerage.platform.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.platform.logging.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes a job on the job thread pool. Status changes commit in their own short transactions; the handler
 * runs outside them and manages its own.
 */
@Component
class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private static final int MAX_ERROR_LENGTH = 4000;

    private final JobRepository repository;
    private final ObjectProvider<JobHandler> handlerBeans;
    private volatile Map<String, JobHandler> handlers;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final UserDetailsService users;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;
    private final TransactionTemplate ownTx;

    JobRunner(JobRepository repository, ObjectProvider<JobHandler> handlers, TransactionTemplate tx, ObjectMapper mapper,
              UserDetailsService users, org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.ownTx = new TransactionTemplate(tx.getTransactionManager());
        this.ownTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.users = users;
        this.handlerBeans = handlers;
        this.tx = tx;
        this.mapper = mapper;
    }

    /** Same-type locks for {@link JobHandler#oneAtATime()} jobs: a second one that slipped past the submit check waits. */
    private final Map<String, java.util.concurrent.locks.ReentrantLock> locks = new java.util.concurrent.ConcurrentHashMap<>();

    boolean oneAtATime(String type) {
        JobHandler h = handlers().get(type);
        return h != null && h.oneAtATime();
    }

    boolean supports(String type) {
        return handlers().containsKey(type);
    }

    String titleOf(String type) {
        JobHandler h = handlers().get(type);
        return h == null ? type : h.title();
    }

    /** Looked up on first use, not at start-up: handlers may themselves depend on the job service. */
    private Map<String, JobHandler> handlers() {
        if (handlers == null) {
            handlers = handlerBeans.orderedStream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
        }
        return handlers;
    }

    @Async(JobConfig.EXECUTOR)
    public void run(long jobId) {
        Job started = tx.execute(s -> {
            Job job = repository.findById(jobId).orElseThrow();
            job.start();
            return job;
        });
        actAs(started.getCreatedBy());
        JobHandler handler = handlers().get(started.getType());
        // Log lines of the job carry the submitting request's reference, so one search finds both.
        MDC.put(RequestContext.REQUEST_ID, started.getRequestId() != null ? started.getRequestId() : "JOB-" + jobId);
        MDC.put(RequestContext.JOB_ID, String.valueOf(jobId));
        MDC.put(RequestContext.USER, started.getCreatedBy());
        MDC.put(RequestContext.MODULE, RequestContext.moduleOf(handler.getClass()));
        MDC.put(RequestContext.ACTION, started.getType());
        log.info("Job {} started", jobId);
        JobProgress.bind(new JobProgress(snapshot -> saveProgress(jobId, snapshot)));
        try {
            JsonNode payload = started.getPayload() == null ? null : mapper.readTree(started.getPayload());
            JsonNode result;
            if (handler.oneAtATime()) {
                var lock = locks.computeIfAbsent(started.getType(), t -> new java.util.concurrent.locks.ReentrantLock());
                lock.lock();
                try {
                    result = handler.run(payload);
                } finally {
                    lock.unlock();
                }
            } else {
                result = handler.run(payload);
            }
            String json = result == null ? null : mapper.writeValueAsString(result);
            tx.executeWithoutResult(s -> repository.findById(jobId).orElseThrow().complete(json));
            log.info("Job {} done", jobId);
        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String error = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
            tx.executeWithoutResult(s -> repository.findById(jobId).orElseThrow().fail(error));
        } finally {
            JobProgress.unbind();
            SecurityContextHolder.clearContext();
            MDC.clear();
        }
    }

    /** Own transaction: the job's own work may still be open in another one. A failed write never fails the job. */
    private void saveProgress(long jobId, JobProgress.Snapshot snapshot) {
        try {
            String json = mapper.writeValueAsString(snapshot);
            ownTx.executeWithoutResult(s -> jdbc.sql("UPDATE job SET progress = CAST(? AS jsonb) WHERE id = ?").params(json, jobId).update());
        } catch (Exception e) {
            log.warn("Job {}: progress not saved: {}", jobId, e.getMessage());
        }
    }

    /** The handler runs as the user who submitted the job, so audit records name them. */
    private void actAs(String username) {
        SecurityContextHolder.clearContext();
        try {
            UserDetails user = users.loadUserByUsername(username);
            if (user instanceof CredentialsContainer c) c.eraseCredentials();
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        } catch (UsernameNotFoundException e) {
            // submitted by "system": run without a user
        }
    }

    String toJson(JsonNode node) {
        try {
            return node == null ? null : mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload is not valid JSON", e);
        }
    }
}
