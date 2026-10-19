package money.epsilon.brokerage.org.service;

import java.time.Instant;
import java.time.LocalDate;
import money.epsilon.brokerage.org.domain.EmployeeStatus;
import money.epsilon.brokerage.org.domain.EmployeeType;
import money.epsilon.brokerage.platform.dated.DatedRow;

/** Read models the org module hands to controllers and other modules. "Current" = the open (valid_to null) row. */
public final class OrgViews {

    private OrgViews() {
    }

    public record PersonRef(Long id, String empCode, String name) {
    }

    public record RegionView(Long id, String name, PersonRef currentRd, int position, boolean endBranchRows) {
    }

    public record BranchView(Long id, String name, Long regionId, String regionName) {
    }

    public record EmployeeView(Long id, String empCode, String name, EmployeeType type, EmployeeStatus status,
                               LocalDate joinedOn, LocalDate exitedOn, Long branchId, String branchName) {
    }

    /** One row of a dated history; "target" is what the subject was linked to (a region, branch, RD...). */
    public record HistoryEntry(Long id, Long targetId, String targetName, LocalDate validFrom, LocalDate validTo,
                               String reason, String changedBy, Instant changedAt) {

        public static HistoryEntry of(DatedRow row, Long targetId, String targetName) {
            return new HistoryEntry(row.getId(), targetId, targetName, row.getValidFrom(), row.getValidTo(),
                    row.getReason(), row.getChangedBy(), row.getChangedAt());
        }
    }

    /** What goes into the audit log for an assignment change. */
    public record AssignmentAudit(Long subjectId, Long targetId, LocalDate validFrom, LocalDate validTo, String reason) {

        public static AssignmentAudit of(Long subjectId, Long targetId, DatedRow row) {
            return new AssignmentAudit(subjectId, targetId, row.getValidFrom(), row.getValidTo(), row.getReason());
        }
    }
}
