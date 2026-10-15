package money.epsilon.brokerage.platform.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobTest {

    @Test
    void happyPathQueuedRunningDone() {
        Job job = new Job("x", null, "u");
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        job.start();
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        job.complete("{}");
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void cannotCompleteBeforeStarting() {
        assertThatThrownBy(() -> new Job("x", null, "u").complete(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotStartTwice() {
        Job job = new Job("x", null, "u");
        job.start();
        assertThatThrownBy(job::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void canFailFromQueuedOrRunningButNotAfterFinishing() {
        Job queued = new Job("x", null, "u");
        queued.fail("interrupted");
        assertThat(queued.getStatus()).isEqualTo(JobStatus.FAILED);

        Job done = new Job("x", null, "u");
        done.start();
        done.complete(null);
        assertThatThrownBy(() -> done.fail("late")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> queued.fail("again")).isInstanceOf(IllegalStateException.class);
    }
}
