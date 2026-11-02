package money.epsilon.brokerage.loan.service;

import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** Loan payouts become the month's loan lines (R-80), shared with the partner on each at its Loans %. */
@Component
class LoanLineSource implements LineSource {

    private final LoanService loans;
    private final LedgerService ledger;

    LoanLineSource(LoanService loans, LedgerService ledger) {
        this.loans = loans;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "LOAN";
    }

    @Override
    public String label() {
        return "Loans";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return loans.forLedger(month).stream().map(x -> SourceLine.of(x.loanEntryId(), x.clientId(), x.gross(), x.gst(), x.net(), x.partnerId())).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replaceLoanLines(periodId, entries.stream().map(x -> new LedgerService.LoanLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.loanEntryId();
    }
}
