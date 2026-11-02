package money.epsilon.brokerage.p2p.service;

import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** P2P entries with brokerage become the month's lines (R-87), shared with the partner on each at its P2P %. */
@Component
class P2pLineSource implements LineSource {

    private final P2pService p2ps;
    private final LedgerService ledger;

    P2pLineSource(P2pService p2ps, LedgerService ledger) {
        this.p2ps = p2ps;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "P2P";
    }

    @Override
    public String label() {
        return "P2P";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return p2ps.forLedger(month).stream().map(x -> SourceLine.of(x.p2pEntryId(), x.clientId(), x.gross(), x.gst(), x.net(), x.partnerId())).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replaceP2pLines(periodId, entries.stream().map(x -> new LedgerService.OtherLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.p2pEntryId();
    }
}
