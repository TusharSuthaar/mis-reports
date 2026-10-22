package money.epsilon.brokerage.org.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.org.domain.Employee;
import money.epsilon.brokerage.org.domain.EmployeeType;
import money.epsilon.brokerage.org.domain.RdAssignment;
import money.epsilon.brokerage.org.domain.Region;
import money.epsilon.brokerage.org.repository.EmployeeRepository;
import money.epsilon.brokerage.org.repository.RdAssignmentRepository;
import money.epsilon.brokerage.org.repository.RegionRepository;
import money.epsilon.brokerage.org.service.OrgViews.AssignmentAudit;
import money.epsilon.brokerage.org.service.OrgViews.HistoryEntry;
import money.epsilon.brokerage.org.service.OrgViews.PersonRef;
import money.epsilon.brokerage.org.service.OrgViews.RegionView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.dated.DatedHistory;
import money.epsilon.brokerage.platform.dated.Timeline;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regions and their RDs. */
@Service
public class RegionService {

    static final String AUDIT_REGION = "REGION";
    static final String AUDIT_RD = "RD_ASSIGNMENT";

    private final RegionRepository regions;
    private final RdAssignmentRepository rds;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final ChangeWindow window;

    RegionService(RegionRepository regions, RdAssignmentRepository rds, EmployeeRepository employees, AuditService audit,
                  ChangeWindow window) {
        this.regions = regions;
        this.rds = rds;
        this.employees = employees;
        this.audit = audit;
        this.window = window;
    }

    @Transactional(readOnly = true)
    public List<RegionView> list() {
        Map<Long, Employee> people = employees.findAll().stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        // Every region's latest RD row in one read, not one query per region.
        Map<Long, RdAssignment> latest = rds.findAll().stream().collect(Collectors.toMap(RdAssignment::getRegionId, Function.identity(),
                (a, b) -> LATEST_RD.compare(a, b) >= 0 ? a : b));
        return regions.findAll().stream()
                .sorted(ORDER)
                .map(r -> view(r, Optional.ofNullable(latest.get(r.getId())), people))
                .toList();
    }

    @Transactional(readOnly = true)
    public RegionView get(long id) {
        return view(find(id), null);
    }

    @Transactional(readOnly = true)
    public Optional<RegionView> findByName(String name) {
        return regions.findByNameIgnoreCase(Text.required(name, "Region")).map(r -> view(r, null));
    }

    @Transactional
    public RegionView create(String name) {
        String clean = Text.required(name, "Region name");
        if (regions.findByNameIgnoreCase(clean).isPresent()) {
            throw new RuleViolationException("Region '" + clean + "' already exists");
        }
        Region region = regions.save(new Region(clean, regions.maxPosition() + 1));
        RegionView view = view(region, null);
        audit.record(AUDIT_REGION, region.getId(), "CREATE", null, view);
        return view;
    }

    @Transactional
    public RegionView rename(long id, String name) {
        Region region = find(id);
        String clean = Text.required(name, "Region name");
        regions.findByNameIgnoreCase(clean).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new RuleViolationException("Region '" + clean + "' already exists");
        });
        RegionView before = view(region, null);
        region.setName(clean);
        RegionView after = view(region, null);
        audit.record(AUDIT_REGION, id, "UPDATE", before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> rdHistory(long regionId) {
        find(regionId);
        return rds.findByRegionIdOrderByValidFromDesc(regionId).stream()
                .map(a -> HistoryEntry.of(a, a.getEmployeeId(), employees.findById(a.getEmployeeId()).map(Employee::getName).orElse("?")))
                .toList();
    }

    /** Makes {@code employeeId} the RD of the region from {@code from}; closes the region's and the person's previous RD rows. */
    @Transactional
    public void setRd(long regionId, long employeeId, LocalDate from, String reason) {
        find(regionId);
        window.beforeChange(from);
        Employee rd = employees.findById(employeeId).orElseThrow(() -> new NotFoundException("Employee " + employeeId + " not found"));
        if (rd.getType() != EmployeeType.RM) throw new RuleViolationException("Only an RM can be an RD (" + rd.getName() + " is " + rd.getType() + ")");
        if (!rd.employedOn(from)) throw new RuleViolationException(rd.getName() + " is not employed on " + from.format(Timeline.DISPLAY));

        Optional<RdAssignment> regionLatest = rds.findFirstByRegionIdOrderByValidFromDesc(regionId);
        if (regionLatest.filter(a -> a.getValidTo() == null && a.getEmployeeId().equals(employeeId)).isPresent()) {
            throw new RuleViolationException(rd.getName() + " is already the RD of this region");
        }
        DatedHistory.makeRoom(regionLatest, from, rds, audit, AUDIT_RD, this::auditView, reason);
        DatedHistory.add(new RdAssignment(regionId, employeeId, from, rd.getExitedOn(), reason), rds, audit, AUDIT_RD, this::auditView);
    }

    /** The region has no RD after {@code lastDay}. */
    @Transactional
    public void endRd(long regionId, LocalDate lastDay, String reason) {
        RdAssignment open = rds.findFirstByRegionIdOrderByValidFromDesc(regionId).filter(a -> a.getValidTo() == null)
                .orElseThrow(() -> new RuleViolationException("This region has no current RD"));
        window.beforeChange(lastDay.plusDays(1));
        DatedHistory.end(open, lastDay, rds, audit, AUDIT_RD, this::auditView, reason);
    }

    private AssignmentAudit auditView(RdAssignment a) {
        return AssignmentAudit.of(a.getRegionId(), a.getEmployeeId(), a);
    }

    /** Moves the region to {@code position} (1 = first; the others shift) and/or sets its END branch rows. */
    @Transactional
    public RegionView arrange(long id, Integer position, Boolean endBranchRows) {
        Region region = find(id);
        RegionView before = view(region, null);
        if (position != null) {
            List<Region> ordered = new java.util.ArrayList<>(regions.findAll().stream().sorted(ORDER).toList());
            ordered.removeIf(r -> r.getId().equals(id));
            ordered.add(Math.max(0, Math.min(position - 1, ordered.size())), region);
            for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i + 1);
        }
        if (endBranchRows != null) region.setEndBranchRows(endBranchRows);
        RegionView after = view(region, null);
        audit.record(AUDIT_REGION, id, "UPDATE", before, after);
        return after;
    }

    static final Comparator<Region> ORDER = Comparator.comparingInt(Region::getPosition).thenComparing(r -> r.getName().toLowerCase());

    private Region find(long id) {
        return regions.findById(id).orElseThrow(() -> new NotFoundException("Region " + id + " not found"));
    }

    private static final Comparator<RdAssignment> LATEST_RD = Comparator.comparing(RdAssignment::getValidFrom).thenComparing(RdAssignment::getId);

    private RegionView view(Region r, Map<Long, Employee> people) {
        return view(r, rds.findFirstByRegionIdOrderByValidFromDesc(r.getId()), people);
    }

    private RegionView view(Region r, Optional<RdAssignment> latestRd, Map<Long, Employee> people) {
        PersonRef rd = latestRd
                .filter(a -> a.getValidTo() == null)
                .map(a -> people != null ? people.get(a.getEmployeeId()) : employees.findById(a.getEmployeeId()).orElse(null))
                .map(e -> new PersonRef(e.getId(), e.getEmpCode(), e.getName()))
                .orElse(null);
        return new RegionView(r.getId(), r.getName(), rd, r.getPosition(), r.isEndBranchRows());
    }
}
