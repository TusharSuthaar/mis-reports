package money.epsilon.brokerage.aif.service;

import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** AIF commitments become the month's AIF lines (R-78); a deal's own sharing % wins over the partner's AIF %. */
@Component
class AifLineSource implements LineSource {

    private final AifService aifs;
    private final LedgerService ledger;

    AifLineSource(AifService aifs, LedgerService ledger) {
        this.aifs = aifs;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "AIF";
    }

    @Override
    public String label() {
        return "AIF";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return aifs.forLedger(month).stream().map(x -> new SourceLine(x.aifEntryId(), x.clientId(), x.gross(), x.gst(), x.net(), x.partnerId(), x.sharingPercent(), null, null)).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replaceAifLines(periodId, entries.stream().map(x -> new LedgerService.AifLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.aifEntryId();
    }
}
