package money.epsilon.brokerage.bond.service;

import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** Bond deals become the month's bond lines (R-74), shared with the partner on each deal at its Bonds %. */
@Component
class BondLineSource implements LineSource {

    private final BondService bonds;
    private final LedgerService ledger;

    BondLineSource(BondService bonds, LedgerService ledger) {
        this.bonds = bonds;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "BOND";
    }

    @Override
    public String label() {
        return "Bonds";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return bonds.forLedger(month).stream().map(x -> SourceLine.of(x.bondEntryId(), x.clientId(), x.gross(), x.gst(), x.net(), x.partnerId())).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replaceBondLines(periodId, entries.stream().map(x -> new LedgerService.BondLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.bondEntryId();
    }
}
