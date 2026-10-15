package money.epsilon.brokerage.platform.user;

public enum Role {
    RM, RD, HO, FINANCE, ADMIN;

    /** Spring Security authority name, e.g. ROLE_ADMIN. */
    public String authority() {
        return "ROLE_" + name();
    }
}
