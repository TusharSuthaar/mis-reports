package money.epsilon.brokerage.sharing.service;

import money.epsilon.brokerage.client.service.ClientMerged;
import money.epsilon.brokerage.platform.audit.AuditService;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** A merged client's partner links: kept on the target when it has none of its own, otherwise dropped (audited). */
@Component
class SharesOnClientMerge {

    private final JdbcClient jdbc;
    private final AuditService audit;

    SharesOnClientMerge(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @EventListener
    public void onClientMerged(ClientMerged e) {
        int sourceLinks = count(e.sourceId());
        if (sourceLinks == 0) return;
        if (count(e.targetId()) == 0) {
            jdbc.sql("UPDATE client_partner_share SET client_id = ? WHERE client_id = ?").params(e.targetId(), e.sourceId()).update();
            audit.record("CLIENT_PARTNER_SHARE", String.valueOf(e.targetId()), "MOVE", new Moved(e.sourceId(), sourceLinks, e.reason()), null);
        } else {
            jdbc.sql("DELETE FROM client_partner_share WHERE client_id = ?").param(e.sourceId()).update();
            audit.record("CLIENT_PARTNER_SHARE", String.valueOf(e.sourceId()), "DELETE",
                    new Moved(e.sourceId(), sourceLinks, "merged into record " + e.targetId() + ", which has its own partners"), null);
        }
    }

    private int count(long clientId) {
        return jdbc.sql("SELECT count(*) FROM client_partner_share WHERE client_id = ?").param(clientId).query(Integer.class).single();
    }

    record Moved(long fromClientId, int links, String reason) {
    }
}
