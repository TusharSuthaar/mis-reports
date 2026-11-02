package money.epsilon.brokerage.insurance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** Each policy's expected commission becomes a line (R-95), shared only with the policy's POSP, at the POSP's commission. */
@Component
class InsuranceLineSource implements LineSource {

    private final InsuranceService policies;
    private final LedgerService ledger;

    InsuranceLineSource(InsuranceService policies, LedgerService ledger) {
        this.policies = policies;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "INS";
    }

    @Override
    public String label() {
        return "Insurance";
    }

    @Override
    public int order() {
        return 110;
    }

    @Override
    public boolean sharedWithPosp() {
        return true;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return policies.forLedger(month).stream().map(x -> new SourceLine(x.policyId(), x.clientId(), x.expectedComm(), BigDecimal.ZERO, x.expectedComm(), x.pospId(), null,
                        x.pospId() == null || x.pospComm().signum() == 0 ? null : x.pospComm().divide(x.expectedComm(), 12, RoundingMode.HALF_UP), x.pospName())).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replaceInsuranceLines(periodId, entries.stream().map(x -> new LedgerService.OtherLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.insurancePolicyId();
    }

    @Override
    public List<NameMatch> nameMatches(YearMonth month) {
        return policies.nameMatches(month).stream().map(m -> new NameMatch(m.policyId(), m.policyNo(), m.nameInFile(), m.clientId(), m.clientName(),
                m.pan(), m.rmName(), m.expectedComm())).toList();
    }
}
