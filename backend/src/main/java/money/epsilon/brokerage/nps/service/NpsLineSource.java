package money.epsilon.brokerage.nps.service;

import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** NPS entries with revenue become the month's lines (R-86), shared with the partner on each at its NPS %. */
@Component
class NpsLineSource implements LineSource {

    private final NpsService npses;
    private final LedgerService ledger;

    NpsLineSource(NpsService npses, LedgerService ledger) {
        this.npses = npses;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "NPS";
    }

    @Override
    public String label() {
        return "NPS";
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return npses.forLedger(month).stream().map(x -> SourceLine.of(x.npsEntryId(), x.clientId(), x.gross(), x.gst(), x.net(), x.partnerId())).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replaceNpsLines(periodId, entries.stream().map(x -> new LedgerService.OtherLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.npsEntryId();
    }
}
