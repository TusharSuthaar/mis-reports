package money.epsilon.brokerage.masterimport;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import money.epsilon.brokerage.masterimport.WorkbookReader.SheetData;
import money.epsilon.brokerage.masterimport.WorkbookReader.SheetRow;
import money.epsilon.brokerage.org.domain.EmployeeType;
import money.epsilon.brokerage.org.service.BranchService;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgViews.BranchView;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.org.service.OrgViews.RegionView;
import money.epsilon.brokerage.org.service.RegionService;
import money.epsilon.brokerage.partner.domain.PartnerStatus;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.partner.service.PartnerRmLinkService;
import money.epsilon.brokerage.partner.service.PartnerRmLinkService.LinkView;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerInput;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;
import money.epsilon.brokerage.platform.dated.Timeline;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Component;

/**
 * Applies the masters workbook through the normal services (same validation and audit as the screens). Runs inside
 * the caller's transaction; the caller rolls everything back if any row fails.
 */
@Component
class MasterImportProcessor {

    enum Outcome { CREATED, UPDATED, UNCHANGED }

    record SheetCount(String sheet, int created, int updated, int unchanged) {
    }

    record RowError(String sheet, int row, String message) {
    }

    private final RegionService regions;
    private final BranchService branches;
    private final EmployeeService employees;
    private final PartnerService partners;
    private final PartnerRmLinkService links;

    MasterImportProcessor(RegionService regions, BranchService branches, EmployeeService employees,
                          PartnerService partners, PartnerRmLinkService links) {
        this.regions = regions;
        this.branches = branches;
        this.employees = employees;
        this.partners = partners;
        this.links = links;
    }

    /** @return per-sheet counts; row errors are added to {@code errors}. Stops early on an unexpected failure. */
    List<SheetCount> apply(Map<SheetSpec, SheetData> sheets, String reason, List<RowError> errors) {
        List<SheetCount> counts = new ArrayList<>();
        for (SheetData data : sheets.values()) {
            String sheet = data.spec().name();
            if (!data.missingColumns().isEmpty()) {
                errors.add(new RowError(sheet, 0, "Header row not found. Required columns: " + String.join(", ", data.missingColumns())));
                continue;
            }
            int[] n = new int[3];
            for (SheetRow row : data.rows()) {
                try {
                    n[applyRow(data.spec(), row, reason).ordinal()]++;
                } catch (RuleViolationException | IllegalArgumentException | NotFoundException | DateTimeParseException e) {
                    errors.add(new RowError(sheet, row.rowNumber(), e.getMessage()));
                } catch (RuntimeException e) {
                    errors.add(new RowError(sheet, row.rowNumber(), "Unexpected error, import stopped here: " + rootMessage(e)));
                    counts.add(new SheetCount(sheet, n[0], n[1], n[2]));
                    return counts;
                }
            }
            counts.add(new SheetCount(sheet, n[0], n[1], n[2]));
        }
        return counts;
    }

    private Outcome applyRow(SheetSpec spec, SheetRow row, String reason) {
        if (spec == SheetSpec.REGIONS) return region(row);
        if (spec == SheetSpec.BRANCHES) return branch(row, reason);
        if (spec == SheetSpec.EMPLOYEES) return employee(row, reason);
        if (spec == SheetSpec.RD) return rd(row, reason);
        if (spec == SheetSpec.RM_BRANCH) return rmBranch(row, reason);
        if (spec == SheetSpec.PARTNERS) return partner(row);
        if (spec == SheetSpec.PARTNER_RM) return partnerRm(row, reason);
        throw new IllegalStateException("No handler for sheet " + spec.name());
    }

    private Outcome region(SheetRow row) {
        String name = Text.required(row.get("Region"), "Region");
        if (regions.findByName(name).isPresent()) return Outcome.UNCHANGED;
        regions.create(name);
        return Outcome.CREATED;
    }

    private Outcome branch(SheetRow row, String reason) {
        String name = Text.required(row.get("Branch"), "Branch");
        Optional<BranchView> existing = branches.findByName(name);
        String regionName = row.get("Region");
        if (existing.isEmpty()) {
            if (regionName == null) throw new IllegalArgumentException("Region is required for a new branch");
            branches.create(name, regionId(regionName), required(row.date("From"), "From is required for a new branch"), reason);
            return Outcome.CREATED;
        }
        if (regionName == null) return Outcome.UNCHANGED;
        long regionId = regionId(regionName);
        if (Objects.equals(existing.get().regionId(), regionId)) return Outcome.UNCHANGED;
        branches.moveToRegion(existing.get().id(), regionId, required(row.date("From"), "From is required to move a branch"), reason);
        return Outcome.UPDATED;
    }

    private Outcome employee(SheetRow row, String reason) {
        String code = Text.required(row.get("Emp Code"), "Emp Code");
        String name = Text.required(row.get("Name"), "Name");
        EmployeeType type = employeeType(row.get("Type"));
        LocalDate joined = row.date("Joined On").orElse(null);
        LocalDate exited = row.date("Exited On").orElse(null);
        Optional<EmployeeView> existing = employees.findByCode(code);
        if (existing.isEmpty()) {
            EmployeeView created = employees.create(code, name, type == null ? EmployeeType.RM : type, joined);
            if (exited != null) employees.exit(created.id(), exited, reason);
            return Outcome.CREATED;
        }
        EmployeeView e = existing.get();
        boolean changed = false;
        boolean detailsDiffer = !name.equals(e.name()) || (type != null && type != e.type()) || (joined != null && !joined.equals(e.joinedOn()));
        if (detailsDiffer) {
            employees.update(e.id(), name, type, joined);
            changed = true;
        }
        if (exited != null) {
            if (e.exitedOn() == null) {
                employees.exit(e.id(), exited, reason);
                changed = true;
            } else if (!exited.equals(e.exitedOn())) {
                throw new RuleViolationException(name + " already exited on " + e.exitedOn().format(Timeline.DISPLAY));
            }
        }
        return changed ? Outcome.UPDATED : Outcome.UNCHANGED;
    }

    private Outcome rd(SheetRow row, String reason) {
        RegionView region = regions.findByName(Text.required(row.get("Region"), "Region"))
                .orElseThrow(() -> new NotFoundException("Region '" + row.get("Region") + "' not found"));
        EmployeeView person = employee(row.get("Emp Code"));
        if (region.currentRd() != null && region.currentRd().id().equals(person.id())) return Outcome.UNCHANGED;
        regions.setRd(region.id(), person.id(), required(row.date("From"), "From is required"), reason);
        return region.currentRd() == null ? Outcome.CREATED : Outcome.UPDATED;
    }

    private Outcome rmBranch(SheetRow row, String reason) {
        EmployeeView person = employee(row.get("Emp Code"));
        BranchView branch = branches.findByName(Text.required(row.get("Branch"), "Branch"))
                .orElseThrow(() -> new NotFoundException("Branch '" + row.get("Branch") + "' not found"));
        if (Objects.equals(person.branchId(), branch.id())) return Outcome.UNCHANGED;
        employees.assignBranch(person.id(), branch.id(), required(row.date("From"), "From is required"), reason);
        return person.branchId() == null ? Outcome.CREATED : Outcome.UPDATED;
    }

    private Outcome partner(SheetRow row) {
        PartnerType type = partnerType(row.get("Type"));
        String name = Text.required(row.get("Name"), "Name");
        PartnerStatus status = partnerStatus(row.get("Status"));
        PartnerInput input = new PartnerInput(type, name, row.get("PAN"), row.get("GSTIN"), row.get("ARN/POSP Code"),
                row.get("Bank Ref"), status);
        Optional<PartnerView> existing = partners.findByTypeAndName(type, name);
        if (existing.isEmpty()) {
            partners.create(input);
            return Outcome.CREATED;
        }
        PartnerView p = existing.get();
        boolean differs = (input.pan() != null && !partners.hasPan(p.id(), input.pan()))
                || (input.gstin() != null && !input.gstin().equalsIgnoreCase(Objects.toString(p.gstin(), "")))
                || (input.arnOrPospCode() != null && !Objects.equals(Text.clean(input.arnOrPospCode()), p.arnOrPospCode()))
                || (input.bankRef() != null && !Objects.equals(Text.clean(input.bankRef()), p.bankRef()))
                || (status != null && status != p.status());
        if (!differs) return Outcome.UNCHANGED;
        partners.update(p.id(), new PartnerInput(null, null, input.pan(), input.gstin(), input.arnOrPospCode(), input.bankRef(), status));
        return Outcome.UPDATED;
    }

    private Outcome partnerRm(SheetRow row, String reason) {
        PartnerType type = partnerType(row.get("Partner Type"));
        String name = Text.required(row.get("Partner Name"), "Partner Name");
        PartnerView partner = partners.findByTypeAndName(type, name)
                .orElseThrow(() -> new NotFoundException("Partner '" + name + "' (" + type + ") not found"));
        EmployeeView rm = employee(row.get("Emp Code"));
        LocalDate from = required(row.date("From"), "From is required");
        LocalDate to = row.date("To").orElse(null);
        Optional<LinkView> latest = links.latest(partner.id(), rm.id());
        if (latest.isPresent() && latest.get().validFrom().equals(from)) {
            LinkView l = latest.get();
            if (Objects.equals(l.validTo(), to)) return Outcome.UNCHANGED;
            if (l.validTo() == null && to != null) {
                links.endLink(partner.id(), l.id(), to, reason);
                return Outcome.UPDATED;
            }
            throw new RuleViolationException("A link from " + from.format(Timeline.DISPLAY) + " already exists with a different end date");
        }
        links.link(partner.id(), rm.id(), from, to, reason);
        return Outcome.CREATED;
    }

    private EmployeeView employee(String code) {
        String c = Text.required(code, "Emp Code");
        return employees.findByCode(c).orElseThrow(() -> new NotFoundException("Emp Code '" + c + "' not found"));
    }

    private long regionId(String name) {
        return regions.findByName(name).map(RegionView::id)
                .orElseThrow(() -> new NotFoundException("Region '" + name + "' not found"));
    }

    private static LocalDate required(Optional<LocalDate> date, String message) {
        return date.orElseThrow(() -> new IllegalArgumentException(message));
    }

    static EmployeeType employeeType(String s) {
        if (s == null) return null;
        return switch (Text.compact(s)) {
            case "RM" -> EmployeeType.RM;
            case "HOUSE" -> EmployeeType.HOUSE;
            default -> throw new IllegalArgumentException("Type must be RM or HOUSE, not '" + s + "'");
        };
    }

    static PartnerType partnerType(String s) {
        if (s == null) throw new IllegalArgumentException("Partner type is required");
        return switch (Text.compact(s)) {
            case "SUBBROKER" -> PartnerType.SUB_BROKER;
            case "REFERRAL", "REFERRALPARTNER" -> PartnerType.REFERRAL;
            case "POSP" -> PartnerType.POSP;
            default -> throw new IllegalArgumentException("Partner type must be SUB_BROKER, REFERRAL or POSP, not '" + s + "'");
        };
    }

    static PartnerStatus partnerStatus(String s) {
        if (s == null) return null;
        return switch (Text.compact(s)) {
            case "ACTIVE" -> PartnerStatus.ACTIVE;
            case "INACTIVE" -> PartnerStatus.INACTIVE;
            default -> throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE, not '" + s + "'");
        };
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
