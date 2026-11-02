package money.epsilon.brokerage.gift.service;

import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LineSource;
import org.springframework.stereotype.Component;

/** GIFT City entries with brokerage become the month's lines (R-82), shared with the partner on each at its GIFT %. */
@Component
class GiftLineSource implements LineSource {

    private final GiftService gifts;
    private final LedgerService ledger;

    GiftLineSource(GiftService gifts, LedgerService ledger) {
        this.gifts = gifts;
        this.ledger = ledger;
    }

    @Override
    public String product() {
        return "GIFT";
    }

    @Override
    public String label() {
        return "GIFT City";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public List<SourceLine> entries(YearMonth month) {
        return gifts.forLedger(month).stream().map(x -> SourceLine.of(x.giftEntryId(), x.clientId(), x.gross(), x.gst(), x.net(), x.partnerId())).toList();
    }

    @Override
    public void writeLines(long periodId, List<SourceLine> entries) {
        ledger.replaceGiftLines(periodId, entries.stream().map(x -> new LedgerService.GiftLineInput(x.entryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
    }

    @Override
    public Long entryOf(LedgerService.ActiveLine line) {
        return line.giftEntryId();
    }
}
