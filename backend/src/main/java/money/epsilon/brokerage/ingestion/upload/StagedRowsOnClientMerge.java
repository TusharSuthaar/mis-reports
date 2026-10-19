package money.epsilon.brokerage.ingestion.upload;

import money.epsilon.brokerage.client.service.ClientMerged;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Rows of an upload not saved yet follow a merged client to the client it was merged into. */
@Component
class StagedRowsOnClientMerge {

    private final JdbcClient jdbc;

    StagedRowsOnClientMerge(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    public void onClientMerged(ClientMerged e) {
        jdbc.sql("UPDATE staged_row SET client_id = ? WHERE client_id = ?").params(e.targetId(), e.sourceId()).update();
    }
}
