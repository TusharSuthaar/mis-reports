package money.epsilon.brokerage.masterimport;

import java.util.List;

/**
 * One sheet of the masters workbook: its name and columns, in the order they are applied. Columns are matched by
 * name (case, spaces and punctuation ignored), never by position.
 */
record SheetSpec(String name, List<Column> columns, String help) {

    record Column(String header, boolean required) {
    }

    static Column req(String header) {
        return new Column(header, true);
    }

    static Column opt(String header) {
        return new Column(header, false);
    }

    static final SheetSpec REGIONS = new SheetSpec("Regions",
            List.of(req("Region")),
            "One row per region.");
    static final SheetSpec BRANCHES = new SheetSpec("Branches",
            List.of(req("Branch"), opt("Region"), opt("From")),
            "New branch: Region and From are required. Existing branch with a different Region: moves it from 'From'.");
    static final SheetSpec EMPLOYEES = new SheetSpec("Employees",
            List.of(req("Emp Code"), req("Name"), opt("Type"),
                    opt("Joined On"), opt("Exited On")),
            "Type is RM or HOUSE (HEAD OFFICE, STAFF books); blank means RM. Exited On records an exit.");
    static final SheetSpec RD = new SheetSpec("RD",
            List.of(req("Region"), req("Emp Code"), req("From")),
            "The RD of each region, from a date. The RD must be an RM.");
    static final SheetSpec RM_BRANCH = new SheetSpec("RM-Branch",
            List.of(req("Emp Code"), req("Branch"), req("From")),
            "Which branch each RM / HOUSE book belongs to, from a date.");
    static final SheetSpec PARTNERS = new SheetSpec("Partners",
            List.of(req("Type"), req("Name"), opt("PAN"),
                    opt("GSTIN"), opt("ARN/POSP Code"), opt("Bank Ref"), opt("Status")),
            "Type is SUB_BROKER, REFERRAL or POSP. A partner is matched by Type + Name; blank cells leave values unchanged.");
    static final SheetSpec PARTNER_RM = new SheetSpec("Partner-RM",
            List.of(req("Partner Type"), req("Partner Name"), req("Emp Code"),
                    req("From"), opt("To")),
            "Which RMs each partner works with. To (inclusive) is optional.");

    /** In dependency order: a sheet may refer to anything on an earlier sheet. */
    static final List<SheetSpec> ALL = List.of(REGIONS, BRANCHES, EMPLOYEES, RD, RM_BRANCH, PARTNERS, PARTNER_RM);

}
