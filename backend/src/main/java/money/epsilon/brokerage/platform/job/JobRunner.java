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

    JobRunner(JobRepository repository, ObjectProvider<JobHandler> handlers, TransactionTemplate tx, ObjectMapper mapper,
              UserDetailsService users) {
        this.repository = repository;
        this.users = users;
        this.handlerBeans = handlers;
        this.tx = tx;
        this.mapper = mapper;
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
        try {
            JsonNode payload = started.getPayload() == null ? null : mapper.readTree(started.getPayload());
            JsonNode result = handler.run(payload);
            String json = result == null ? null : mapper.writeValueAsString(result);
            tx.executeWithoutResult(s -> repository.findById(jobId).orElseThrow().complete(json));
            log.info("Job {} done", jobId);
        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String error = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
            tx.executeWithoutResult(s -> repository.findById(jobId).orElseThrow().fail(error));
        } finally {
            SecurityContextHolder.clearContext();
            MDC.clear();
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
