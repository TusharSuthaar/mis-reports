package money.epsilon.brokerage.ledger.service;

import money.epsilon.brokerage.client.service.ClientMerged;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Saved brokerage is append-only (R-44): a client with saved lines cannot be merged away. */
@Component
class LedgerOnClientMerge {

    private final JdbcClient jdbc;

    LedgerOnClientMerge(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    public void onClientMerged(ClientMerged e) {
        int lines = jdbc.sql("SELECT count(*) FROM brokerage_line WHERE client_id = ?").param(e.sourceId()).query(Integer.class).single();
        if (lines > 0) {
            throw new RuleViolationException(e.sourceName() + " has saved brokerage (" + lines + " lines), so it cannot be merged away: "
                    + "merge the other way, into this client, instead");
        }
    }
}
