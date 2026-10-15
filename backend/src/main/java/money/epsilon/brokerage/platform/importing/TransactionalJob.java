package money.epsilon.brokerage.platform.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import money.epsilon.brokerage.platform.job.JobHandler;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one shape of a background import / load (upload → preview → <b>apply as a job</b>): the payload is read, the work
 * runs in one transaction (all or nothing; {@code status.setRollbackOnly()} for a dry run), and the result is stored on
 * the job. Subclasses write their own business audit record inside {@link #apply}.
 *
 * @param <P> the job's payload record
 */
public abstract class TransactionalJob<P> implements JobHandler {

    private final String type;
    private final String title;
    private final Class<P> payloadType;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    protected TransactionalJob(String type, String title, Class<P> payloadType, TransactionTemplate tx, ObjectMapper mapper) {
        this.type = type;
        this.title = title;
        this.payloadType = payloadType;
        this.tx = tx;
        this.mapper = mapper;
    }

    /** The work, inside the transaction. @return the job result (stored as JSON) */
    protected abstract Object apply(P payload, TransactionStatus status);

    @Override
    public final String type() {
        return type;
    }

    @Override
    public final String title() {
        return title;
    }

    @Override
    public final JsonNode run(JsonNode payloadJson) throws Exception {
        P payload = mapper.treeToValue(payloadJson, payloadType);
        Object result = tx.execute(status -> apply(payload, status));
        return result == null ? null : mapper.valueToTree(result);
    }
}
