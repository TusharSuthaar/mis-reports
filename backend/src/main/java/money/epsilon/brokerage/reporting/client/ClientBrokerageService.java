package money.epsilon.brokerage.reporting.client;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LedgerService.ClientLine;
import money.epsilon.brokerage.period.domain.Period;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.period.service.PeriodService.PeriodView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A client's brokerage month by month (R-09), as saved and split: MF trail (incl. GST), GST, net, each partner's share and
 * what Epsilon keeps, with the RM / branch the month was split under. A month saved but not processed yet has no split.
 */
@Service
public class ClientBrokerageService {

    private final LedgerService ledger;
    private final PeriodService periods;
    private final JdbcClient jdbc;

    ClientBrokerageService(LedgerService ledger, PeriodService periods, JdbcClient jdbc) {
        this.ledger = ledger;
        this.periods = periods;
        this.jdbc = jdbc;
    }

    public record PartnerAmount(String role, String name, BigDecimal percent, BigDecimal amount) {
    }

    /** {@code adjustsMonth}: a correction posted in {@code month} for that earlier (locked) month. */
    public record MonthRow(long lineId, YearMonth month, Period.Status status, YearMonth adjustsMonth, BigDecimal gross, BigDecimal gst,
                           BigDecimal net, List<PartnerAmount> partners, BigDecimal partnersTotal, BigDecimal epsilon, String rm,
                           String branch) {
    }

    private record Split(String rm, String branch, List<PartnerAmount> partners, BigDecimal epsilon) {
    }

    @Transactional(readOnly = true)
    public List<MonthRow> months(long clientId, String productType) {
        List<ClientLine> lines = ledger.clientLines(clientId, productType);
        if (lines.isEmpty()) return List.of();
        Map<Long, PeriodView> periodById = periods.list("MF").stream().collect(Collectors.toMap(PeriodView::id, Function.identity()));
        Map<Long, Split> splits = new HashMap<>();
        jdbc.sql("SELECT * FROM v_line_split WHERE client_id = ? AND product_type = ?").params(clientId, productType)
                .query((rs, n) -> {
                    List<PartnerAmount> partners = new ArrayList<>();
                    if (rs.getString("sub_broker") != null) {
                        partners.add(new PartnerAmount("Sub-broker", rs.getString("sub_broker"), rs.getBigDecimal("sub_broker_pct"), rs.getBigDecimal("sub_broker_amount")));
                    }
                    if (rs.getString("referral") != null) {
                        partners.add(new PartnerAmount("Referral", rs.getString("referral"), rs.getBigDecimal("referral_pct"), rs.getBigDecimal("referral_amount")));
                    }
                    if (rs.getString("posp") != null) partners.add(new PartnerAmount("POSP", rs.getString("posp"), null, rs.getBigDecimal("posp_amount")));
                    return Map.entry(rs.getLong("line_id"), new Split(rs.getString("rm"), rs.getString("branch"), partners, rs.getBigDecimal("epsilon_amount")));
                })
                .list().forEach(e -> splits.put(e.getKey(), e.getValue()));
        return lines.stream().map(l -> {
            PeriodView p = periodById.get(l.periodId());
            Split s = splits.get(l.id());
            List<PartnerAmount> partners = s == null ? List.of() : s.partners();
            return new MonthRow(l.id(), p == null ? null : p.month(), p == null ? null : p.status(),
                    l.adjustsPeriodId() == null || periodById.get(l.adjustsPeriodId()) == null ? null : periodById.get(l.adjustsPeriodId()).month(),
                    l.gross(), l.gst(), l.net(), partners,
                    s == null ? null : partners.stream().map(PartnerAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                    s == null ? null : s.epsilon(), s == null ? null : s.rm(), s == null ? null : s.branch());
        }).sorted(Comparator.comparing(MonthRow::month, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(MonthRow::lineId)).toList();
    }
}
