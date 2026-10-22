package money.epsilon.brokerage.org.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.org.domain.Branch;
import money.epsilon.brokerage.org.domain.BranchRegionAssignment;
import money.epsilon.brokerage.org.domain.Employee;
import money.epsilon.brokerage.org.domain.EmployeeType;
import money.epsilon.brokerage.org.domain.RdAssignment;
import money.epsilon.brokerage.org.domain.Region;
import money.epsilon.brokerage.org.domain.RmBranchAssignment;
import money.epsilon.brokerage.org.repository.BranchRegionAssignmentRepository;
import money.epsilon.brokerage.org.repository.BranchRepository;
import money.epsilon.brokerage.org.repository.EmployeeRepository;
import money.epsilon.brokerage.org.repository.RdAssignmentRepository;
import money.epsilon.brokerage.org.repository.RegionRepository;
import money.epsilon.brokerage.org.repository.RmBranchAssignmentRepository;
import money.epsilon.brokerage.org.service.OrgViews.PersonRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Region → RD → Branch → people, exactly as it stood on a given date. */
@Service
public class OrgChartService {

    private final RegionRepository regions;
    private final BranchRepository branches;
    private final EmployeeRepository employees;
    private final BranchRegionAssignmentRepository placements;
    private final RdAssignmentRepository rds;
    private final RmBranchAssignmentRepository rmBranches;
    private final money.epsilon.brokerage.platform.audit.AuditService audit;

    /** Charts already built, per day, with the change marker they were built at (see {@link #asOf}); the 400 most recent days. */
    private final Map<LocalDate, Cached> cache = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LocalDate, Cached> eldest) {
            return size() > 400;
        }
    });

    private record Cached(long version, OrgChart chart) {
    }

    OrgChartService(RegionRepository regions, BranchRepository branches, EmployeeRepository employees,
                    BranchRegionAssignmentRepository placements, RdAssignmentRepository rds,
                    RmBranchAssignmentRepository rmBranches, money.epsilon.brokerage.platform.audit.AuditService audit) {
        this.audit = audit;
        this.regions = regions;
        this.branches = branches;
        this.employees = employees;
        this.placements = placements;
        this.rds = rds;
        this.rmBranches = rmBranches;
    }

    /**
     * The org on {@code day}. Lists ask for it once per month they show, so a built chart is kept and reused while nothing has
     * changed: every saved change writes an audit entry, so the newest audit id is the change marker.
     */
    @Transactional(readOnly = true)
    public OrgChart asOf(LocalDate day) {
        long version = audit.lastId();
        Cached hit = cache.get(day);
        if (hit != null && hit.version() == version) return hit.chart();
        OrgChart chart = build(day);
        cache.put(day, new Cached(version, chart));
        return chart;
    }

    private OrgChart build(LocalDate day) {
        Map<Long, Region> regionById = regions.findAll().stream().collect(Collectors.toMap(Region::getId, Function.identity()));
        Map<Long, Branch> branchById = branches.findAll().stream().collect(Collectors.toMap(Branch::getId, Function.identity()));
        Map<Long, Employee> employeeById = employees.findAll().stream().collect(Collectors.toMap(Employee::getId, Function.identity()));

        Map<Long, Long> regionOfBranch = placements.findAllOn(day).stream()
                .collect(Collectors.toMap(BranchRegionAssignment::getBranchId, BranchRegionAssignment::getRegionId));
        Map<Long, Long> rdOfRegion = rds.findAllOn(day).stream()
                .collect(Collectors.toMap(RdAssignment::getRegionId, RdAssignment::getEmployeeId));
        Map<Long, List<Employee>> peopleOfBranch = rmBranches.findAllOn(day).stream()
                .collect(Collectors.groupingBy(RmBranchAssignment::getBranchId,
                        Collectors.mapping(a -> employeeById.get(a.getEmployeeId()), Collectors.toList())));

        Comparator<Employee> byName = Comparator.comparing(e -> e.getName().toLowerCase());
        Function<Long, ChartBranch> branchNode = branchId -> new ChartBranch(branchId, branchById.get(branchId).getName(),
                peopleOfBranch.getOrDefault(branchId, List.of()).stream().sorted(byName)
                        .map(e -> new ChartPerson(e.getId(), e.getEmpCode(), e.getName(), e.getType())).toList());

        List<ChartRegion> regionNodes = new ArrayList<>();
        for (Region r : regionById.values()) {
            Employee rd = employeeById.get(rdOfRegion.get(r.getId()));
            List<ChartBranch> regionBranches = regionOfBranch.entrySet().stream()
                    .filter(en -> en.getValue().equals(r.getId()))
                    .map(en -> branchNode.apply(en.getKey()))
                    .sorted(Comparator.comparing(b -> b.name().toLowerCase()))
                    .toList();
            regionNodes.add(new ChartRegion(r.getId(), r.getName(),
                    rd == null ? null : new PersonRef(rd.getId(), rd.getEmpCode(), rd.getName()), regionBranches));
        }
        regionNodes.sort(Comparator.comparing(r -> regionById.get(r.id()), RegionService.ORDER));

        List<ChartBranch> branchesWithoutRegion = branchById.keySet().stream()
                .filter(id -> !regionOfBranch.containsKey(id))
                .map(branchNode)
                .sorted(Comparator.comparing(b -> b.name().toLowerCase()))
                .toList();
        var placed = peopleOfBranch.values().stream().flatMap(List::stream).map(Employee::getId).collect(Collectors.toSet());
        List<ChartPerson> peopleWithoutBranch = employeeById.values().stream()
                .filter(e -> e.employedOn(day) && !placed.contains(e.getId()))
                .sorted(byName)
                .map(e -> new ChartPerson(e.getId(), e.getEmpCode(), e.getName(), e.getType()))
                .toList();
        return new OrgChart(day, List.copyOf(regionNodes), List.copyOf(branchesWithoutRegion), List.copyOf(peopleWithoutBranch));   // shared from the cache: read-only
    }

    public record OrgChart(LocalDate date, List<ChartRegion> regions, List<ChartBranch> branchesWithoutRegion,
                           List<ChartPerson> peopleWithoutBranch) {
    }

    public record ChartRegion(Long id, String name, PersonRef rd, List<ChartBranch> branches) {
    }

    public record ChartBranch(Long id, String name, List<ChartPerson> people) {
    }

    public record ChartPerson(Long id, String empCode, String name, EmployeeType type) {
    }
}
