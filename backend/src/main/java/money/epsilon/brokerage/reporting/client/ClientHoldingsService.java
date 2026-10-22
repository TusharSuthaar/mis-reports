package money.epsilon.brokerage.reporting.client;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import money.epsilon.brokerage.aif.service.AifService;
import money.epsilon.brokerage.bond.service.BondService;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.client.service.ClientService.ClientView;
import money.epsilon.brokerage.client.service.ClientService.Identity;
import money.epsilon.brokerage.equity.service.EquityService;
import money.epsilon.brokerage.fd.service.FdService;
import money.epsilon.brokerage.gift.service.GiftService;
import money.epsilon.brokerage.ingestion.finesse.FinesseBrokerageParser;
import money.epsilon.brokerage.ingestion.finesse.FinesseClientMasterService;
import money.epsilon.brokerage.loan.domain.LoanEntry;
import money.epsilon.brokerage.loan.service.LoanService;
import money.epsilon.brokerage.nps.service.NpsService;
import money.epsilon.brokerage.p2p.service.P2pService;
import money.epsilon.brokerage.pms.domain.PmsInvestment;
import money.epsilon.brokerage.pms.service.PmsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything one PAN holds (R-09). The PAN is the client's own; under it come the client itself, any other record still
 * carrying that PAN (until merged), and minors whose Finesse code has it as guardian PAN and no PAN of their own (R-03).
 * Their FD, bond, AIF, loan, GIFT City, PMS, NPS and P2P entries (cancelled left out), plus equity brokerage matched by
 * the PAN or a former PAN, so trades not yet linked to a client record show too. A client without a PAN: itself only.
 */
@Service
public class ClientHoldingsService {

    public enum Relation { THIS, SAME_PAN, MINOR }

    /** One client record under the PAN; {@code pan} as the user may see it. */
    public record Member(long clientId, String name, String pan, Relation relation) {
    }

    /** One holding: {@code id} opens it on the Products screen (null for equity, which is per month). */
    public record Row(String product, String tab, Long id, Long clientId, String clientName, YearMonth month, String item,
                      BigDecimal amount, BigDecimal gross) {
    }

    public record Holdings(List<Member> members, List<Row> rows) {
    }

    private final ClientService clients;
    private final FinesseClientMasterService finesse;
    private final FdService fd;
    private final BondService bonds;
    private final AifService aif;
    private final LoanService loans;
    private final GiftService gift;
    private final PmsService pms;
    private final NpsService nps;
    private final P2pService p2p;
    private final EquityService equity;

    ClientHoldingsService(ClientService clients, FinesseClientMasterService finesse, FdService fd, BondService bonds, AifService aif,
                          LoanService loans, GiftService gift, PmsService pms, NpsService nps, P2pService p2p, EquityService equity) {
        this.clients = clients;
        this.finesse = finesse;
        this.fd = fd;
        this.bonds = bonds;
        this.aif = aif;
        this.loans = loans;
        this.gift = gift;
        this.pms = pms;
        this.nps = nps;
        this.p2p = p2p;
        this.equity = equity;
    }

    @Transactional(readOnly = true)
    public Holdings holdings(long clientId) {
        Identity me = clients.identity(clientId);
        Map<Long, Relation> group = new LinkedHashMap<>();
        group.put(clientId, Relation.THIS);
        Set<String> panHashes = new LinkedHashSet<>(me.formerPanHashes());
        if (me.panHash() != null) {
            panHashes.add(me.panHash());
            clients.idsWithPan(me.panHash()).forEach(id -> group.putIfAbsent(id, Relation.SAME_PAN));
            List<Long> minors = clients.idsByExternalCode(FinesseBrokerageParser.SOURCE, finesse.minorCodes(me.panHash())).values()
                    .stream().distinct().sorted().toList();
            Map<Long, String> withOwnPan = clients.pans(minors);
            minors.stream().filter(id -> !withOwnPan.containsKey(id)).forEach(id -> group.putIfAbsent(id, Relation.MINOR));
        }
        Set<Long> ids = group.keySet();
        Map<Long, ClientView> views = clients.views(ids);
        List<Member> members = group.entrySet().stream()
                .map(e -> new Member(e.getKey(), views.get(e.getKey()).name(), views.get(e.getKey()).pan(), e.getValue())).toList();

        List<Row> rows = new ArrayList<>();
        fd.forClients(ids).forEach(e -> rows.add(new Row("FD", "FD", e.id(), e.clientId(), e.clientName(), e.month(), e.fdName(), e.amount(), e.gross())));
        bonds.forClients(ids).forEach(e -> rows.add(new Row("Bond", "BOND", e.id(), e.clientId(), e.clientName(), e.month(), e.bondName(),
                e.consideration(), e.gross())));
        aif.forClients(ids).forEach(e -> rows.add(new Row("AIF", "AIF", e.id(), e.clientId(), e.clientName(), e.month(), e.fundName(),
                e.commitment(), e.gross())));
        loans.forClients(ids).forEach(e -> rows.add(new Row("Loan", "LOAN", e.id(), e.clientId(), e.clientName(), e.month(),
                e.lender() + " " + e.loanType() + (e.payoutKind() == LoanEntry.PayoutKind.TRAIL ? " (trail)" : ""), e.sanctionedAmount(), e.gross())));
        gift.forClients(ids).forEach(e -> rows.add(new Row("GIFT City", "GIFT", e.id(), e.clientId(), e.clientName(), e.month(), e.fund(),
                e.amountInr(), e.gross())));
        pms.forClients(ids).forEach(e -> rows.add(new Row("PMS", "PMS", e.id(), e.clientId(), e.clientName(), e.month(),
                e.amc() + " · " + e.strategy() + (e.holdingStatus() == PmsInvestment.Holding.REDEEMED ? " (redeemed)" : ""), e.amount(), null)));
        nps.forClients(ids).forEach(e -> rows.add(new Row("NPS", "NPS", e.id(), e.clientId(), e.clientName(), e.month(), e.pop(), e.amount(), e.revenue())));
        p2p.forClients(ids).forEach(e -> rows.add(new Row("P2P", "P2P", e.id(), e.clientId(), e.clientName(), e.month(),
                e.platform() + " · " + e.scheme(), e.amount(), e.gross())));
        equity.forPans(panHashes, ids).forEach(m -> rows.add(new Row("Equity", "EQUITY", null, m.clientId(), m.clientName(), m.month(),
                m.partyCode() + " · " + m.trades() + (m.trades() == 1 ? " trade" : " trades") + " · " + m.exchanges(), null, m.share())));
        rows.sort(Comparator.comparing(Row::month).reversed().thenComparing(Row::product));
        return new Holdings(members, rows);
    }
}
