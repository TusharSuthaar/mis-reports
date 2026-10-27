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
 */
public final class InsuranceCategoryRules {

    /** How the net premium (without GST) follows from what is typed: Motor = OD + TP; Life and Health = total (no GST); General = total ÷ 1.18. */
    public enum Premium { OD_TP, TOTAL, TOTAL_LESS_GST }

    public record Rule(Category category, String label, String subTypeLabel, List<String> subTypes, List<BusinessType> businessTypes,
                       String sumLabel, boolean sumRequired, String pptLabel, List<String> pptChoices, boolean termRequired,
                       Premium premium, BigDecimal gstPercent) {
    }

    private static final BigDecimal GST = new BigDecimal("18");

    public static final Map<Category, Rule> RULES = Map.of(
            Category.LIFE, new Rule(Category.LIFE, "Life", "Plan type",
                    List.of("Term", "Savings – Non-PAR", "Savings – PAR", "ULIP", "Endowment", "Money back", "Pension / Annuity", "Child plan", "Whole life"),
                    List.of(BusinessType.NEW, BusinessType.RENEWAL, BusinessType.FYCI),
                    "Sum assured", true, "Premium paying term (years)", List.of(), true, Premium.TOTAL, BigDecimal.ZERO),
            Category.HEALTH, new Rule(Category.HEALTH, "Health", "Cover type",
                    List.of("Individual", "Floater", "Senior citizen", "Top-up / Super top-up", "Critical illness", "Group health", "Personal accident"),
                    List.of(BusinessType.NEW, BusinessType.RENEWAL, BusinessType.PORT, BusinessType.ENDORSEMENT),
                    "Sum insured", true, "Policy period (years)", List.of("1", "2", "3"), false, Premium.TOTAL, BigDecimal.ZERO),
            Category.MOTOR, new Rule(Category.MOTOR, "Motor", "Vehicle",
                    List.of("Private car", "Two-wheeler", "Commercial vehicle", "Taxi", "Tractor / Misc."),
                    List.of(BusinessType.NEW, BusinessType.RENEWAL, BusinessType.ENDORSEMENT),
                    "IDV", false, "Policy period (OD + TP years)", List.of("1", "1+3", "1+5", "3", "5"), false, Premium.OD_TP, GST),
            Category.GENERAL, new Rule(Category.GENERAL, "General", "Policy type",
                    List.of("Fire – Residential", "Fire – Corporate", "Marine", "WC – Corporate", "PA", "Travel", "Shop / Office package", "Liability",
                            "Engineering", "Cyber"),
                    List.of(BusinessType.NEW, BusinessType.RENEWAL, BusinessType.ENDORSEMENT),
                    "Sum insured", true, "Policy period (years)", List.of("1", "2", "3", "5"), false, Premium.TOTAL_LESS_GST, GST));

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
