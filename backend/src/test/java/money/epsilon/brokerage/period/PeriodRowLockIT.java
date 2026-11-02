package money.epsilon.brokerage.period;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** H-05: a month being processed holds its row, so a lock or an entry change of that month waits instead of overwriting it. */
class PeriodRowLockIT extends IntegrationTestBase {

    @Autowired
    PeriodService periods;
    @Autowired
    ChangeWindow months;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    TransactionTemplate tx;

    @Test
    void aLockWaitsForTheProcessOfTheSameMonthAndThenSeesItsResult() throws Exception {
        LocalDate month = LocalDate.of(2300 + (int) (System.nanoTime() % 600), 2, 1);   // a month no other test uses
        long id = jdbc.sql("""
                INSERT INTO period (product_type, month, from_date, to_date, status) VALUES ('MF', ?, ?, ?, 'PROCESSED') RETURNING id""")
                .params(month, month, month.plusMonths(1).minusDays(1)).query(Long.class).single();
        try {
            CountDownLatch held = new CountDownLatch(1);
            // "Process": holds the month's row for 1.5 s, as AttributionService.process does from start to finish.
            CompletableFuture<Void> process = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
                periods.getForChange(id);
                held.countDown();
                sleep(1500);
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();

            long start = System.nanoTime();
            periods.lock(id, "finance");                                              // waits for the process to finish
            long waitedMs = (System.nanoTime() - start) / 1_000_000;
            process.get(10, TimeUnit.SECONDS);
            assertThat(waitedMs).as("lock waited for the running process").isGreaterThanOrEqualTo(1000);
            assertThat(periods.get(id).status().name()).isEqualTo("LOCKED");

            // An entry change of a locked month is refused (R-98).
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.executeWithoutResult(s -> months.beforeEntryChange(month.plusDays(9))))
                    .hasMessageContaining("is locked");
        } finally {
            // Leave no locked month behind: it would refuse other tests' dated changes before it.
            jdbc.sql("UPDATE period SET status = 'OPEN' WHERE id = ?").param(id).update();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
