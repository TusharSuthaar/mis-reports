package money.epsilon.brokerage.insurance.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.BusinessType;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.Category;

/**
 * What each kind of insurance has and needs when typed on the screen (R-96), as the policy sheet fills it in: its sub
 * types, the business types it can have, what its sum and term are called, and which fields are required. The screen
 * shows only a category's own fields. Uploads are not held to these (the sheet leaves Motor's sub type blank).
 * Each category also lists the insurers that sell it, for the screen's pick list (another can still be typed).
 */
public final class InsuranceCategoryRules {

    /** How the net premium (without GST) follows from what is typed: Motor = OD + TP; Life and Health = total (no GST); General = total ÷ 1.18. */
    public enum Premium { OD_TP, TOTAL, TOTAL_LESS_GST }

    public record Rule(Category category, String label, String subTypeLabel, List<String> subTypes, List<BusinessType> businessTypes,
                       String sumLabel, boolean sumRequired, String pptLabel, List<String> pptChoices, boolean termRequired,
                       Premium premium, BigDecimal gstPercent, List<String> insurers) {
    }

    private static final BigDecimal GST = new BigDecimal("18");

    /** General insurance's sub type for the policy sheet's CAT "SME" (fire, burglary and other business covers). */
    public static final String SME = "SME";

    /** Life insurers (names as the insurance team writes them where they already have policies). */
    static final List<String> LIFE_INSURERS = List.of("Aditya Birla Sun Life Insurance", "Ageas Federal Life Insurance", "Axis Max Life Insurance Co. Ltd.",
            "Bajaj Life Insurance", "Bandhan Life Insurance", "Canara HSBC Life Insurance", "Edelweiss Life Insurance", "Generali Central Life Insurance",
            "HDFC Life Insurance Company Limited", "ICICI Prudential Life Insurance", "IndiaFirst Life Insurance", "Kotak Mahindra Life Insurance",
            "LIC", "PNB Metlife", "Pramerica Life Insurance", "Reliance Nippon Life Insurance", "SBI Life Insurance", "Shriram Life Insurance",
            "Star Union Dai-ichi Life Insurance", "Tata AIA Life Insurance Company Ltd");

    /** General insurers: they sell motor, general (fire, SME, travel, …) and health cover. */
    static final List<String> GENERAL_INSURERS = List.of("Acko General Insurance", "Bajaj General Insurance", "Cholamandalam MS General Insurance",
            "Generali Central General Insurance", "Go Digit", "HDFC Ergo", "ICICI Lombard", "IFFCO Tokio", "Liberty General Insurance",
            "Magma General Insurance", "National Insurance", "Navi General Insurance", "Raheja QBE", "Reliance General Insurance", "Royal Sundaram",
            "SBI General", "Shriram General Insurance", "TATA AIG", "The New India Assurance Co. Ltd", "The Oriental Insurance", "United India Insurance",
            "Universal Sompo", "Zuno General Insurance", "Zurich Kotak");

    /** Standalone health insurers, listed with the general insurers for health policies. */
    static final List<String> HEALTH_INSURERS = List.of("Aditya Birla Health Insurance", "Care Health Insurance", "Galaxy Health Insurance",
            "ManipalCigna Health Insurance", "Narayana Health Insurance", "Niva Bupa", "Star Health");

    private static List<String> sorted(List<String> a, List<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static final Map<Category, Rule> RULES = Map.of(
            Category.LIFE, new Rule(Category.LIFE, "Life", "Plan type",
                    List.of("Term", "Savings – Non-PAR", "Savings – PAR", "ULIP", "Endowment", "Money back", "Pension / Annuity", "Child plan", "Whole life"),
                    List.of(BusinessType.NEW, BusinessType.RENEWAL, BusinessType.FYCI),
                    "Sum assured", true, "Premium paying term (years)", List.of(), true, Premium.TOTAL, BigDecimal.ZERO, LIFE_INSURERS),
            Category.HEALTH, new Rule(Category.HEALTH, "Health", "Cover type",
                    List.of("Individual", "Floater", "Senior citizen", "Top-up / Super top-up", "Critical illness", "Group health", "Personal accident"),
                    List.of(BusinessType.NEW, BusinessType.RENEWAL, BusinessType.PORT, BusinessType.ENDORSEMENT),
                    "Sum insured", true, "Policy period (years)", List.of("1", "2", "3"), false, Premium.TOTAL, BigDecimal.ZERO,
                    sorted(HEALTH_INSURERS, GENERAL_INSURERS)),
            Category.MOTOR, new Rule(Category.MOTOR, "Motor", "Vehicle",
                    List.of("Private car", "Two-wheeler", "Commercial vehicle", "Taxi", "Tractor / Misc."),
                    List.of(BusinessType.NEW, BusinessType.RENEWAL, BusinessType.ENDORSEMENT),
                    "IDV", false, "Policy period (OD + TP years)", List.of("1", "1+3", "1+5", "3", "5"), false, Premium.OD_TP, GST, GENERAL_INSURERS),
            Category.GENERAL, new Rule(Category.GENERAL, "General", "Policy type",
                    List.of("Fire – Residential", "Fire – Corporate", "Marine", "WC – Corporate", "PA", "Travel", "Shop / Office package", "Liability",
                            "Engineering", "Cyber", SME),
                    List.of(BusinessType.NEW, BusinessType.RENEWAL, BusinessType.ENDORSEMENT),
                    "Sum insured", true, "Policy period (years)", List.of("1", "2", "3", "5"), false, Premium.TOTAL_LESS_GST, GST, GENERAL_INSURERS));

    private InsuranceCategoryRules() {
    }

    /** The category's required fields, for a policy typed on the screen. @throws IllegalArgumentException naming what is missing */
    public static void checkScreen(InsurancePolicy.Details d) {
        Rule r = RULES.get(d.category());
        if (!r.businessTypes().contains(d.businessType())) {
            throw new IllegalArgumentException(r.label() + " policies are " + String.join(" / ", r.businessTypes().stream().map(InsuranceCategoryRules::label).toList())
                    + ", not " + label(d.businessType()));
        }
        if (d.subType() == null) throw new IllegalArgumentException("Choose the " + r.subTypeLabel().toLowerCase());
        if (r.sumRequired() && d.sumAssured() == null) throw new IllegalArgumentException("Enter the " + r.sumLabel().toLowerCase());
        if (d.ppt() == null) throw new IllegalArgumentException("Enter the " + r.pptLabel().toLowerCase());
        if (r.termRequired() && d.policyTerm() == null) throw new IllegalArgumentException("Enter the policy term (years)");
        if (r.premium() == Premium.OD_TP && d.odPremium() == null && d.tpPremium() == null) {
            throw new IllegalArgumentException("Enter the OD premium, the TP premium or both");
        }
    }

    public static String label(BusinessType t) {
        return t == BusinessType.FYCI ? "FYCI" : t.name().charAt(0) + t.name().substring(1).toLowerCase();
    }
}
