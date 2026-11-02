package money.epsilon.brokerage.preipo.service;

import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** Unlisted deals become the month's lines (R-93); a deal's own sharing % wins over the partner's Unlisted %. */
@Component
class PreIpoLineSource implements LineSource {

    private final PreIpoService preIpos;
    private final LedgerService ledger;

    PreIpoLineSource(PreIpoService preIpos, LedgerService ledger) {
        this.preIpos = preIpos;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "PREIPO";
    }

    @Override
    public String label() {
        return "Unlisted";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return preIpos.forLedger(month).stream().map(x -> new SourceLine(x.preIpoEntryId(), x.clientId(), x.gross(), x.gst(), x.net(), x.partnerId(), x.sharingPercent(), null, null)).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replacePreIpoLines(periodId, entries.stream().map(x -> new LedgerService.PreIpoLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.preIpoEntryId();
    }
}
