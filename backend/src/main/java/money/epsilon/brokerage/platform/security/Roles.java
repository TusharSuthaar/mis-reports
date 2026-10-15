package money.epsilon.brokerage.platform.security;

import money.epsilon.brokerage.platform.user.Role;

/** @PreAuthorize expressions shared by controllers, plus role checks for code. */
public final class Roles {

    /** Masters (org, partners): HO and FINANCE read; RM/RD get row-level access in Phase 6. */
    public static final String MASTER_READ = "hasAnyRole('ADMIN','HO','FINANCE')";
    public static final String MASTER_WRITE = "hasAnyRole('ADMIN','HO')";

    private Roles() {
    }

    /** Full PAN is shown only to FINANCE and ADMIN; everyone else sees it masked. */
    public static boolean canSeeFullPan() {
        return CurrentUser.principal().map(p -> p.role() == Role.FINANCE || p.role() == Role.ADMIN).orElse(false);
    }
}
