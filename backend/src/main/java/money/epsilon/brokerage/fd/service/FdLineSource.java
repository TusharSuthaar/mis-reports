package money.epsilon.brokerage.fd.service;

import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** FD entries become the month's FD lines (R-74), shared with the partner on each FD at its FD %. */
@Component
class FdLineSource implements LineSource {

    private final FdService fds;
    private final LedgerService ledger;

    FdLineSource(FdService fds, LedgerService ledger) {
        this.fds = fds;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "FD";
    }

    @Override
    public String label() {
        return "FD";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return fds.forLedger(month).stream().map(x -> SourceLine.of(x.fdEntryId(), x.clientId(), x.gross(), x.gst(), x.net(), x.partnerId())).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replaceFdLines(periodId, entries.stream().map(x -> new LedgerService.FdLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.fdEntryId();
    }
}
