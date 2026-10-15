package money.epsilon.brokerage.sharing.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import money.epsilon.brokerage.client.service.ClientService.ClientKey;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.sharing.load.MasterShareReader.MasterRow;
import money.epsilon.brokerage.sharing.load.MasterShareReader.PartnerCell;
import money.epsilon.brokerage.sharing.load.SharePlan.Action;
import money.epsilon.brokerage.sharing.load.SharePlan.Change;
import money.epsilon.brokerage.sharing.load.SharePlan.Current;
import money.epsilon.brokerage.sharing.load.SharePlan.Existing;
import org.junit.jupiter.api.Test;

class SharePlanTest {

    private static final Function<String, String> HASH = pan -> "h-" + pan;
    private static final Function<String, Long> NO_ALIAS = name -> null;

    private static ClientKey client(long id, String name, String pan) {
        return new ClientKey(id, name.toUpperCase(), pan == null ? null : HASH.apply(pan));
    }

    private static PartnerCell partner(String name, String type, String pct) {
        return new PartnerCell(name, type == null ? "" : type, pct == null ? null : new BigDecimal(pct), null);
    }

    private static MasterRow row(int n, String name, String pan, PartnerCell... partners) {
        return new MasterRow(n, name, pan == null ? "" : pan, new ArrayList<>(List.of(partners)));
    }

    private static SharePlan plan(List<MasterRow> rows, List<ClientKey> clients) {
        return SharePlan.build(rows, clients, HASH, List.of(), Map.of(), NO_ALIAS);
    }

    @Test
    void matchesByPanAndNameIgnoringCaseAndSpaces() {
        SharePlan plan = plan(List.of(row(2, "anita rao ", "pqrst6789k", partner("ALPHA WEALTH ", "Sub broker", "0.8"))),
                List.of(client(1, "ANITA RAO", "PQRST6789K")));

        assertThat(plan.matchedClients).isEqualTo(1);
        assertThat(plan.changes).singleElement().satisfies(c -> {
            assertThat(c.clientId()).isEqualTo(1);
            assertThat(c.action()).isEqualTo(Action.SET);
            assertThat(c.role()).isEqualTo(PartnerType.SUB_BROKER);
            assertThat(c.desired().fraction()).isEqualByComparingTo("0.8");
        });
        assertThat(plan.newPartners.values()).singleElement().satisfies(p -> assertThat(p.name()).isEqualTo("ALPHA WEALTH "));
    }

    @Test
    void aMergedSpellingGoesToThePartnerItWasMergedInto() {
        List<SharePlan.Existing> partners = List.of(new SharePlan.Existing(19, PartnerType.REFERRAL, "TEST PARTNER AGARWAL"),
                new SharePlan.Existing(59, PartnerType.REFERRAL, "TEST PARTNER AGRAWAL")); // merged into 19, still on file
        SharePlan plan = SharePlan.build(List.of(row(2, "A", "AAAAA1111A", partner("Test Partner Agrawal", "Referral Partner", "0.2"))),
                List.of(client(1, "A", "AAAAA1111A")), HASH, partners, java.util.Map.of(),
                name -> name.trim().replaceAll("\s+", " ").equalsIgnoreCase("TEST PARTNER AGRAWAL") ? 19L : null);
        assertThat(plan.changes).singleElement().satisfies(c -> assertThat(c.partnerId()).isEqualTo(19L));
        assertThat(plan.newPartners).isEmpty();
    }

    @Test
    void aClientCanHaveASubBrokerAndAReferralPartner() {
        SharePlan plan = plan(List.of(
                        row(2, "USUAL", "AAAAA1111A", partner("SAMPLE LLP", "Sub broker", "0"), partner("PARTNER ONE", "Referral Partner", "0.5")),
                        row(3, "BOTH PAID", "BBBBB2222B", partner("KEFI", "Sub broker", "0.5"), partner("PARTNER TWO", "Referral Partner", "0.2"))),
                List.of(client(1, "USUAL", "AAAAA1111A"), client(2, "BOTH PAID", "BBBBB2222B")));

        assertThat(plan.bothRoles).isEqualTo(2);
        assertThat(plan.zeroPct).isEqualTo(1);
        assertThat(plan.changes).extracting(Change::clientId, Change::role, c -> c.desired().fraction().stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder(
                        tuple(1L, PartnerType.SUB_BROKER, "0"), tuple(1L, PartnerType.REFERRAL, "0.5"),
                        tuple(2L, PartnerType.SUB_BROKER, "0.5"), tuple(2L, PartnerType.REFERRAL, "0.2"));
    }

    @Test
    void twoPartnersOfTheSameTypeOnOneRowAreRefused() {
        SharePlan plan = plan(List.of(row(2, "A", "AAAAA1111A", partner("X", "Referral Partner", "0.5"), partner("Y", "Referral Partner", "0.2"))),
                List.of(client(1, "A", "AAAAA1111A")));
        assertThat(plan.changes).isEmpty();
        assertThat(plan.problems).anyMatch(p -> p.contains("same type"));
    }

    @Test
    void aPanMatchesEvenWhenTheNameIsSpeltDifferently() {
        SharePlan plan = plan(List.of(row(2, "AAHNA SINGH (MINOR)", "pqrst6789k", partner("P", "Referral Partner", "0.5"))),
                List.of(client(1, "ANITA RAO", "PQRST6789K")));
        assertThat(plan.unmatched).isEmpty();
        assertThat(plan.matchedByPanOnly).isEqualTo(1);
        assertThat(plan.changes).extracting(Change::clientId).containsExactly(1L);
    }

    @Test
    void explainsWhyARowDidNotMatch() {
        SharePlan plan = plan(List.of(
                        row(3, "NEW CLIENT", "ABCDE1234F", partner("P", "Referral Partner", "0.5")),
                        row(4, "TWINS", null, partner("P", "Referral Partner", "0.5")),
                        row(5, "NO PARTNER, NOT REPORTED", "ZZZZZ9999Z")),
                List.of(client(1, "ANITA RAO", "PQRST6789K"), client(2, "TWINS", null), client(3, "TWINS", null)));

        assertThat(plan.unmatched).extracting(SharePlan.Unmatched::reason).containsExactly(
                "No client with this PAN yet", "No PAN, and the name is not unique");
        assertThat(plan.unmatched.get(0).maskedPan()).isEqualTo("ABCDE****F");
        assertThat(plan.changes).isEmpty();
    }

    @Test
    void clientWithoutPanMatchesByUniqueName() {
        SharePlan plan = plan(List.of(row(2, "solo", null, partner("P", "Sub broker", "0.7"))), List.of(client(9, "SOLO", null)));
        assertThat(plan.changes).extracting(Change::clientId).containsExactly(9L);
    }

    @Test
    void missingPercentMeansZeroAndBlankTypeIsTakenFromOtherRows() {
        SharePlan plan = plan(List.of(
                        row(2, "A", "AAAAA1111A", partner("NDACFO", "Referral Partner", null)),
                        row(3, "B", "BBBBB2222B", partner("ndacfo", "", "0.5"))),
                List.of(client(1, "A", "AAAAA1111A"), client(2, "B", "BBBBB2222B")));

        assertThat(plan.zeroPct).isEqualTo(1);
        assertThat(plan.changes).allSatisfy(c -> assertThat(c.role()).isEqualTo(PartnerType.REFERRAL));
        assertThat(plan.newPartners).hasSize(1);
        assertThat(plan.newPartners.values().iterator().next().clients()).isEqualTo(2);
    }

    @Test
    void comparesWithTodaysSharesRoleByRole() {
        List<ClientKey> clients = List.of(client(1, "SAME", "AAAAA1111A"), client(2, "NEW PCT", "BBBBB2222B"), client(3, "DROPPED", "CCCCC3333C"));
        List<Existing> partners = List.of(new Existing(50, PartnerType.SUB_BROKER, "Alpha Wealth"), new Existing(60, PartnerType.REFERRAL, "Partner Two"));
        Map<Long, Map<PartnerType, Current>> now = Map.of(
                1L, Map.of(PartnerType.SUB_BROKER, new Current(50, new BigDecimal("0.800000"))),
                2L, Map.of(PartnerType.SUB_BROKER, new Current(50, new BigDecimal("0.500000"))),
                3L, Map.of(PartnerType.SUB_BROKER, new Current(50, new BigDecimal("0.800000")),
                        PartnerType.REFERRAL, new Current(60, new BigDecimal("0.200000"))));
        SharePlan plan = SharePlan.build(List.of(
                        row(2, "SAME", "AAAAA1111A", partner("ALPHA WEALTH", "Sub broker", "0.8")),
                        row(3, "NEW PCT", "BBBBB2222B", partner("ALPHA WEALTH", "Sub broker", "0.75")),
                        row(4, "DROPPED", "CCCCC3333C", partner("PARTNER TWO", "Referral Partner", "0.2"))),
                clients, HASH, partners, now, NO_ALIAS);

        assertThat(plan.unchanged).isEqualTo(1);
        assertThat(plan.newPartners).isEmpty();
        assertThat(plan.changes).extracting(Change::clientId, Change::action, Change::role, Change::partnerId)
                .containsExactlyInAnyOrder(
                        tuple(2L, Action.SET, PartnerType.SUB_BROKER, 50L),
                        tuple(3L, Action.END, PartnerType.SUB_BROKER, null));
    }

    @Test
    void aSpellingConfirmedBeforeUsesTheExistingPartner() {
        List<Existing> partners = List.of(new Existing(77, PartnerType.SUB_BROKER, "ZEDD XAVIER"));
        SharePlan plan = SharePlan.build(List.of(row(2, "A", "AAAAA1111A", partner("Zedd Quillon Xavier", "Sub broker", "0.5"))),
                List.of(client(1, "A", "AAAAA1111A")), HASH, partners, Map.of(),
                name -> name.equalsIgnoreCase("zedd quillon xavier") ? 77L : null);
        assertThat(plan.newPartners).isEmpty();
        assertThat(plan.changes).singleElement().satisfies(c -> assertThat(c.partnerId()).isEqualTo(77L));
    }

    @Test
    void rowsThatDisagreeAboutOneClientAreLeftAlone() {
        SharePlan plan = plan(List.of(
                        row(2, "A", "AAAAA1111A", partner("X", "Sub broker", "0.5")),
                        row(3, "A", "AAAAA1111A", partner("X", "Sub broker", "0.75"))),
                List.of(client(1, "A", "AAAAA1111A")));
        assertThat(plan.conflicts).isEqualTo(1);
        assertThat(plan.changes).isEmpty();
        assertThat(plan.problems).anyMatch(p -> p.contains("disagree"));
    }
}
