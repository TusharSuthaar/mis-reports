package money.epsilon.brokerage.ingestion;

import money.epsilon.brokerage.platform.text.Text;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import money.epsilon.brokerage.mapping.service.AliasService;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;

/**
 * Works out which RM / sub-broker a spelling in the file means: a remembered alias first, then an exact name
 * match (ignoring case and extra spaces). Never guesses beyond that.
 */
public class NameMatcher {

    public enum Status {
        /** Same name as one RM / partner. */
        MATCHED,
        /** A spelling we were told about before. */
        REMEMBERED,
        /** Several RMs carry this name; someone has to choose. */
        SEVERAL,
        /** No RM / partner by this name. */
        UNKNOWN
    }

    public record Match(Status status, Long id, List<EmployeeView> candidates) {
    }

    private final String source;
    private final AliasService aliases;
    private final Map<String, List<EmployeeView>> rmsByName;
    private final List<PartnerView> partners;

    public NameMatcher(String source, AliasService aliases, List<EmployeeView> rms, List<PartnerView> partners) {
        this.source = source;
        this.aliases = aliases;
        this.rmsByName = rms.stream().collect(Collectors.groupingBy(e -> Text.key(e.name())));
        this.partners = partners;
    }

    public Match rm(String spelling) {
        if (Text.key(spelling).isEmpty()) return new Match(Status.UNKNOWN, null, List.of());
        Optional<Long> remembered = aliases.resolve(source, EntityType.RM, spelling);
        if (remembered.isPresent()) return new Match(Status.REMEMBERED, remembered.get(), List.of());
        List<EmployeeView> same = rmsByName.getOrDefault(Text.key(spelling), List.of());
        if (same.size() == 1) return new Match(Status.MATCHED, same.get(0).id(), List.of());
        if (same.size() > 1) return new Match(Status.SEVERAL, null, same);
        return new Match(Status.UNKNOWN, null, List.of());
    }

    /** Sub-brokers: alias, else same-named SUB_BROKER partner, else UNKNOWN (a new partner). */
    public Match subBroker(String spelling) {
        Optional<Long> remembered = aliases.resolve(source, EntityType.PARTNER, spelling);
        if (remembered.isPresent()) return new Match(Status.REMEMBERED, remembered.get(), List.of());
        return partnerNamed(spelling).map(p -> new Match(Status.MATCHED, p.id(), List.of()))
                .orElse(new Match(Status.UNKNOWN, null, List.of()));
    }

    public Optional<PartnerView> partnerNamed(String name) {
        String key = Text.key(name);
        return partners.stream().filter(p -> p.type() == PartnerType.SUB_BROKER && Text.key(p.name()).equals(key)).findFirst();
    }

    public static PartnerService.PartnerInput newSubBroker(String name) {
        return new PartnerService.PartnerInput(PartnerType.SUB_BROKER, name, null, null, null, null, null);
    }
}
