package money.epsilon.brokerage.pms.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** Each client's PMS revenue typed for the month becomes a line (R-85), shared with the partner on the investment at its PMS %. */
@Component
class PmsLineSource implements LineSource {

    private final PmsFeeService fees;
    private final LedgerService ledger;

    PmsLineSource(PmsFeeService fees, LedgerService ledger) {
        this.fees = fees;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "PMS";
    }

    @Override
    public String label() {
        return "PMS";
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return fees.forLedger(month).stream().map(x -> SourceLine.of(x.pmsFeeId(), x.clientId(), x.amount(), BigDecimal.ZERO, x.amount(), x.partnerId())).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replacePmsLines(periodId, entries.stream().map(x -> new LedgerService.OtherLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.pmsFeeId();
    }
}
