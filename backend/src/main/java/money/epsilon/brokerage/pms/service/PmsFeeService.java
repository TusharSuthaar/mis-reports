package money.epsilon.brokerage.pms.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.pms.domain.PmsFee;
import money.epsilon.brokerage.pms.domain.PmsInvestment;
import money.epsilon.brokerage.pms.repository.PmsFeeRepository;
import money.epsilon.brokerage.pms.repository.PmsInvestmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each client's PMS revenue per month (R-85), typed for the month the AMC's payout is received, against the client's PMS
 * investment: client, RM and partner come from the investment. These are the month's PMS lines. Every change is audited.
 */
@Service
public class PmsFeeService {

    static final String AUDIT = "PMS_FEE";

    private final PmsFeeRepository fees;
    private final PmsInvestmentRepository investments;
    private final ClientService clients;
    private final EmployeeService employees;
    private final PartnerService partners;
    private final AuditService audit;

    PmsFeeService(PmsFeeRepository fees, PmsInvestmentRepository investments, ClientService clients, EmployeeService employees,
                  PartnerService partners, AuditService audit) {
        this.fees = fees;
        this.investments = investments;
        this.clients = clients;
        this.employees = employees;
        this.partners = partners;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FeeView> list(YearMonth from, YearMonth to, boolean includeCancelled) {
        LocalDate f = from == null ? LocalDate.of(2000, 1, 1) : from.atDay(1);
        LocalDate t = to == null ? LocalDate.of(2999, 12, 1) : to.atDay(1);
        return views(fees.findByMonthBetweenOrderByMonthDescIdDesc(f, t).stream()
                .filter(x -> includeCancelled || x.getStatus() == PmsFee.Status.ACTIVE).toList());
    }

    @Transactional
    public FeeView create(long investmentId, LocalDate month, BigDecimal amount, String remarks) {
        PmsInvestment inv = investment(investmentId);
        if (inv.getStatus() != PmsInvestment.Status.ACTIVE) throw new RuleViolationException("This PMS investment is cancelled");
        PmsFee fee = new PmsFee(investmentId);
        fee.set(month(month), amount(amount), Text.clean(remarks));
        fees.saveAndFlush(fee);
        FeeView v = views(List.of(fee)).get(0);
        audit.record(AUDIT, fee.getId(), "CREATE", null, v);
        return v;
    }

    @Transactional
    public FeeView update(long id, LocalDate month, BigDecimal amount, String remarks) {
        PmsFee fee = find(id);
        if (fee.getStatus() == PmsFee.Status.CANCELLED) throw new RuleViolationException("This PMS revenue entry is cancelled");
        FeeView before = views(List.of(fee)).get(0);
        fee.set(month(month), amount(amount), Text.clean(remarks));
        fees.saveAndFlush(fee);
        FeeView after = views(List.of(fee)).get(0);
        audit.record(AUDIT, id, "UPDATE", before, after);
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        PmsFee fee = find(id);
        if (fee.getStatus() == PmsFee.Status.CANCELLED) return;
        FeeView before = views(List.of(fee)).get(0);
        fee.cancel();
        fees.saveAndFlush(fee);
        audit.record(AUDIT, id, "CANCEL", before, Map.of("reason", Objects.requireNonNullElse(Text.clean(reason), "")));
    }

    /** The month's PMS revenue as processing needs it: no GST on the line, the amount is the END's PMS figure. */
    @Transactional(readOnly = true)
    public List<PmsForLedger> forLedger(YearMonth month) {
        List<PmsFee> list = fees.findByMonthOrderByIdAsc(month.atDay(1)).stream().filter(x -> x.getStatus() == PmsFee.Status.ACTIVE).toList();
        Map<Long, PmsInvestment> inv = new HashMap<>();
        investments.findAllById(list.stream().map(PmsFee::getInvestmentId).collect(Collectors.toSet())).forEach(i -> inv.put(i.getId(), i));
        return list.stream().filter(x -> inv.get(x.getInvestmentId()).getStatus() == PmsInvestment.Status.ACTIVE)
                .map(x -> new PmsForLedger(x.getId(), inv.get(x.getInvestmentId()).getClientId(), inv.get(x.getInvestmentId()).getPartnerId(),
                        x.getAmount()))
                .toList();
    }

    public record PmsForLedger(long pmsFeeId, long clientId, Long partnerId, BigDecimal amount) {
    }

    /** What the END shows for each PMS line: the AMC and strategy. */
    @Transactional(readOnly = true)
    public Map<Long, String> labels(Collection<Long> feeIds) {
        Map<Long, String> result = new HashMap<>();
        List<PmsFee> list = fees.findAllById(feeIds);
        Map<Long, PmsInvestment> inv = new HashMap<>();
        investments.findAllById(list.stream().map(PmsFee::getInvestmentId).collect(Collectors.toSet())).forEach(i -> inv.put(i.getId(), i));
        list.forEach(x -> {
            PmsInvestment i = inv.get(x.getInvestmentId());
            result.put(x.getId(), i == null ? "PMS" : "PMS: " + i.getAmc() + (i.getStrategy() == null ? "" : " · " + i.getStrategy()));
        });
        return result;
    }

    private static LocalDate month(LocalDate month) {
        if (month == null) throw new IllegalArgumentException("Choose the month the payout was received");
        return month.withDayOfMonth(1);
    }

    private static BigDecimal amount(BigDecimal amount) {
        if (amount == null || amount.signum() == 0) throw new IllegalArgumentException("Enter the PMS revenue for the month");
        return amount;
    }

    private PmsInvestment investment(long id) {
        return investments.findById(id).orElseThrow(() -> new NotFoundException("PMS investment " + id + " not found"));
    }

    private PmsFee find(long id) {
        return fees.findById(id).orElseThrow(() -> new NotFoundException("PMS revenue entry " + id + " not found"));
    }

    private List<FeeView> views(List<PmsFee> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, PmsInvestment> inv = new HashMap<>();
        investments.findAllById(list.stream().map(PmsFee::getInvestmentId).collect(Collectors.toSet())).forEach(i -> inv.put(i.getId(), i));
        Set<Long> clientIds = inv.values().stream().map(PmsInvestment::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, String> rmNames = new HashMap<>(), partnerNames = new HashMap<>();
        inv.values().forEach(i -> {
            rmNames.computeIfAbsent(i.getRmId(), id -> employees.get(id).name());
            if (i.getPartnerId() != null) partnerNames.computeIfAbsent(i.getPartnerId(), id -> partners.get(id).name());
        });
        return list.stream().map(x -> {
            PmsInvestment i = inv.get(x.getInvestmentId());
            String pan = pans.get(i.getClientId());
            return new FeeView(x.getId(), x.getInvestmentId(), YearMonth.from(x.getMonth()), i.getClientId(), names.get(i.getClientId()),
                    pan == null ? null : fullPan ? pan : PanCipher.mask(pan), rmNames.get(i.getRmId()),
                    i.getPartnerId() == null ? null : partnerNames.get(i.getPartnerId()), i.getAmc(), i.getStrategy(), x.getAmount(),
                    x.getRemarks(), x.getStatus(), x.getCreatedBy());
        }).toList();
    }

    public record FeeView(Long id, Long investmentId, YearMonth month, Long clientId, String clientName, String pan, String rmName,
                          String partnerName, String amc, String strategy, BigDecimal amount, String remarks, PmsFee.Status status,
                          String createdBy) {
    }
}
