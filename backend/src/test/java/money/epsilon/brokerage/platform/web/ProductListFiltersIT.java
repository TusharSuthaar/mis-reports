package money.epsilon.brokerage.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** L-07: product lists take the shared filters and their own, and refuse a filter they do not know. */
class ProductListFiltersIT extends IntegrationTestBase {

    @Test
    void aMistakenFilterIsRefusedWhileSharedAndOwnFiltersPass() throws Exception {
        MockHttpSession admin = loginAdmin();
        // ?month= is not a filter: it used to return every month without a word.
        String detail = getJson("/api/fd?month=2026-08", admin, 400).get("detail").asText();
        assertThat(detail).contains("Unknown filter 'month'").contains("from").contains("to");
        assertThat(getJson("/api/insurance/export.xlsx?monthh=2026-08", admin, 400).get("detail").asText()).contains("'monthh'");

        // The All products screen sends every shared filter to every list: one a product has no use for is fine.
        getJson("/api/fd?from=2026-08&to=2026-08&lender=TATA&amc=X", admin, 200);
        getJson("/api/equity?from=2026-08&to=2026-08&payment=PENDING&platform=SMC", admin, 200);
        // A list's own parameter counts as known.
        getJson("/api/preipo?from=2026-08&kind=BUY", admin, 200);
    }
}
