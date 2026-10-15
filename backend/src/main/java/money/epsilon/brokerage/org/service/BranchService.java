package money.epsilon.brokerage.org.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.org.domain.Branch;
import money.epsilon.brokerage.org.domain.BranchRegionAssignment;
import money.epsilon.brokerage.org.domain.Region;
import money.epsilon.brokerage.org.repository.BranchRegionAssignmentRepository;
import money.epsilon.brokerage.org.repository.BranchRepository;
import money.epsilon.brokerage.org.repository.RegionRepository;
import money.epsilon.brokerage.org.service.OrgViews.AssignmentAudit;
import money.epsilon.brokerage.org.service.OrgViews.BranchView;
import money.epsilon.brokerage.org.service.OrgViews.HistoryEntry;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.dated.DatedHistory;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Branches and which region they belong to over time. */
@Service
public class BranchService {

    static final String AUDIT_BRANCH = "BRANCH";
    static final String AUDIT_PLACEMENT = "BRANCH_REGION";

    private final BranchRepository branches;
    private final RegionRepository regions;
    private final BranchRegionAssignmentRepository placements;
    private final AuditService audit;
    private final ChangeWindow window;

    BranchService(BranchRepository branches, RegionRepository regions, BranchRegionAssignmentRepository placements,
                  AuditService audit, ChangeWindow window) {
        this.branches = branches;
        this.regions = regions;
        this.placements = placements;
        this.audit = audit;
        this.window = window;
    }

    @Transactional(readOnly = true)
    public List<BranchView> list() {
        Map<Long, Region> regionById = regions.findAll().stream().collect(Collectors.toMap(Region::getId, Function.identity()));
        return branches.findAll().stream()
                .sorted(Comparator.comparing(b -> b.getName().toLowerCase()))
                .map(b -> view(b, regionById))
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchView get(long id) {
        return view(find(id), null);
    }

    @Transactional(readOnly = true)
    public Optional<BranchView> findByName(String name) {
        return branches.findByNameIgnoreCase(Text.required(name, "Branch")).map(b -> view(b, null));
    }

    /** A branch is always created inside a region, from a date. */
    @Transactional
    public BranchView create(String name, long regionId, LocalDate from, String reason) {
        String clean = Text.required(name, "Branch name");
        if (branches.findByNameIgnoreCase(clean).isPresent()) {
            throw new RuleViolationException("Branch '" + clean + "' already exists");
        }
        requireRegion(regionId);
        Branch branch = branches.save(new Branch(clean));
        audit.record(AUDIT_BRANCH, branch.getId(), "CREATE", null, new BranchView(branch.getId(), branch.getName(), null, null));
        DatedHistory.add(new BranchRegionAssignment(branch.getId(), regionId, from, reason), placements, audit, AUDIT_PLACEMENT, this::auditView);
        return view(branch, null);
    }

    @Transactional
    public BranchView rename(long id, String name) {
        Branch branch = find(id);
        String clean = Text.required(name, "Branch name");
        branches.findByNameIgnoreCase(clean).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new RuleViolationException("Branch '" + clean + "' already exists");
        });
        BranchView before = view(branch, null);
        branch.setName(clean);
        BranchView after = view(branch, null);
        audit.record(AUDIT_BRANCH, id, "UPDATE", before, after);
        return after;
    }

    /** Moves the branch to another region from {@code from}. */
    @Transactional
    public void moveToRegion(long branchId, long regionId, LocalDate from, String reason) {
        find(branchId);
        window.beforeChange(from);
        requireRegion(regionId);
        Optional<BranchRegionAssignment> latest = placements.findFirstByBranchIdOrderByValidFromDesc(branchId);
        if (latest.filter(a -> a.getValidTo() == null && a.getRegionId().equals(regionId)).isPresent()) {
            throw new RuleViolationException("The branch is already in this region");
        }
        DatedHistory.makeRoom(latest, from, placements, audit, AUDIT_PLACEMENT, this::auditView, reason);
        DatedHistory.add(new BranchRegionAssignment(branchId, regionId, from, reason), placements, audit, AUDIT_PLACEMENT, this::auditView);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> regionHistory(long branchId) {
        find(branchId);
        return placements.findByBranchIdOrderByValidFromDesc(branchId).stream()
                .map(a -> HistoryEntry.of(a, a.getRegionId(), regions.findById(a.getRegionId()).map(Region::getName).orElse("?")))
                .toList();
    }

    private AssignmentAudit auditView(BranchRegionAssignment a) {
        return AssignmentAudit.of(a.getBranchId(), a.getRegionId(), a);
    }

    private void requireRegion(long regionId) {
        if (!regions.existsById(regionId)) throw new NotFoundException("Region " + regionId + " not found");
    }

    private Branch find(long id) {
        return branches.findById(id).orElseThrow(() -> new NotFoundException("Branch " + id + " not found"));
    }

    private BranchView view(Branch b, Map<Long, Region> regionById) {
        Optional<BranchRegionAssignment> current = placements.findFirstByBranchIdOrderByValidFromDesc(b.getId())
                .filter(a -> a.getValidTo() == null);
        Region region = current.map(a -> regionById != null ? regionById.get(a.getRegionId()) : regions.findById(a.getRegionId()).orElse(null))
                .orElse(null);
        return new BranchView(b.getId(), b.getName(), region == null ? null : region.getId(), region == null ? null : region.getName());
    }
}
