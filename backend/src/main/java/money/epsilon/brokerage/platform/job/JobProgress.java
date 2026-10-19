package money.epsilon.brokerage.platform.job;

import java.util.function.Consumer;

/**
 * Lets a running job say how far it is, for the progress bar on the screen that started it. Call
 * {@code JobProgress.current()} from anywhere in the job's code: outside a job it does nothing.
 * <pre>
 *   JobProgress p = JobProgress.current();
 *   p.steps(3);
 *   p.step("Adding new clients", newClients.size());
 *   for (...) { ...; p.advance(); }
 * </pre>
 * Updates are saved at most every {@value #FLUSH_MS} ms (and at each new step), in their own transaction.
 */
public final class JobProgress {

    /** What the screen shows: step {@code step} of {@code steps}, {@code done} of {@code total} items in it. */
    public record Snapshot(int step, int steps, String label, int done, int total) {
    }

    static final long FLUSH_MS = 500;
    private static final JobProgress NONE = new JobProgress(s -> { });
    private static final ThreadLocal<JobProgress> CURRENT = new ThreadLocal<>();

    private final Consumer<Snapshot> sink;
    private int steps = 1, step = 0, done = 0, total = 0;
    private String label;
    private long lastFlush;

    JobProgress(Consumer<Snapshot> sink) {
        this.sink = sink;
    }

    /** The progress of the job running on this thread, or a no-op. */
    public static JobProgress current() {
        JobProgress p = CURRENT.get();
        return p == null ? NONE : p;
    }

    static void bind(JobProgress p) {
        CURRENT.set(p);
    }

    static void unbind() {
        CURRENT.remove();
    }

    /** How many steps the job has (default 1). */
    public void steps(int count) {
        this.steps = Math.max(1, count);
    }

    /** Starts the next step: {@code total} items to do (0 if unknown). */
    public void step(String label, int total) {
        this.step = Math.min(step + 1, steps);
        this.label = label;
        this.total = Math.max(0, total);
        this.done = 0;
        flush(true);
    }

    /** One more item of the current step is done. */
    public void advance() {
        done = total > 0 ? Math.min(done + 1, total) : done + 1;
        flush(false);
    }

    private void flush(boolean always) {
        long now = System.currentTimeMillis();
        if (!always && now - lastFlush < FLUSH_MS && done != total) return;
        lastFlush = now;
        sink.accept(new Snapshot(Math.max(step, 1), steps, label, done, total));
    }
}
