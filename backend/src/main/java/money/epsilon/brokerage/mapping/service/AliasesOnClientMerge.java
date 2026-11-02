package money.epsilon.brokerage.mapping.service;

import money.epsilon.brokerage.client.service.ClientMerged;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Confirmed client matches follow a merged client to the client it was merged into. */
@Component
class AliasesOnClientMerge {

    private final AliasService aliases;

    AliasesOnClientMerge(AliasService aliases) {
        this.aliases = aliases;
    }

    @EventListener
    public void onClientMerged(ClientMerged e) {
        aliases.retarget(EntityType.CLIENT, e.sourceId(), e.targetId());
    }
}
