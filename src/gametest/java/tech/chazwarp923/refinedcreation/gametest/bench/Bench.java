package tech.chazwarp923.refinedcreation.gametest.bench;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tech.chazwarp923.refinedcreation.RefinedCreation;

/**
 * Times operations for the benchmark suite and writes the results as a Markdown table.
 * <p>
 * Each measurement runs some warm-up iterations first, so the JIT has compiled the code, then times each measured
 * iteration on its own. It reports the median, 95th percentile and maximum, plus the bytes the server thread
 * allocated per iteration. A setup step can run before each iteration without being timed.
 * <p>
 * The warm-up and iteration counts a benchmark asks for are minimums. Operations that take microseconds would be
 * done before the JIT finished compiling them, so each phase keeps going until the operation itself has run for
 * {@link #MIN_PHASE_NANOS}, unless that would take longer than {@link #MAX_PHASE_NANOS} in all, setup included.
 */
public final class Bench {
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    /** How long the timed operation should run in each phase, warm-up and measurement, at least. */
    static final long MIN_PHASE_NANOS = 200_000_000L;
    /** The most a phase may take, setup included, when it runs past its minimum iterations. */
    static final long MAX_PHASE_NANOS = 3_000_000_000L;

    private final List<Result> results = new ArrayList<>();
    private final Map<String, String> notes = new LinkedHashMap<>();

    /**
     * One measured operation.
     *
     * @param id          short ID, e.g. "B1"
     * @param description what was measured
     * @param medianMicros median time per iteration
     * @param p95Micros   95th percentile time per iteration
     * @param maxMicros   slowest iteration
     * @param bytesPerOp  bytes allocated per iteration, or -1 if the JVM can't tell
     */
    public record Result(String id, String description, double medianMicros, double p95Micros, double maxMicros,
                         long bytesPerOp, int iterations) {
    }

    /**
     * Times {@code op}.
     *
     * @param warmup     untimed iterations to run first, at least
     * @param iterations timed iterations, at least
     * @param setup      run before every iteration, untimed; may be {@code null}
     */
    public Result measure(String id, String description, int warmup, int iterations, Runnable setup, Runnable op) {
        long windowStart = System.currentTimeMillis();
        long phaseStart = System.nanoTime();
        long opNanos = 0;
        for (int i = 0; i < warmup || keepGoing(opNanos, phaseStart); i++) {
            if (setup != null) {
                setup.run();
            }
            long start = System.nanoTime();
            op.run();
            opNanos += System.nanoTime() - start;
        }
        long[] nanos = new long[Math.max(iterations, 16)];
        int count = 0;
        long allocated = 0;
        boolean allocationKnown = THREADS.isThreadAllocatedMemorySupported()
                && THREADS.isThreadAllocatedMemoryEnabled();
        long thread = Thread.currentThread().threadId();
        phaseStart = System.nanoTime();
        opNanos = 0;
        while (count < iterations || keepGoing(opNanos, phaseStart)) {
            if (setup != null) {
                setup.run();
            }
            long bytesBefore = allocationKnown ? THREADS.getThreadAllocatedBytes(thread) : 0;
            long start = System.nanoTime();
            op.run();
            long elapsed = System.nanoTime() - start;
            if (allocationKnown) {
                allocated += THREADS.getThreadAllocatedBytes(thread) - bytesBefore;
            }
            if (count == nanos.length) {
                nanos = Arrays.copyOf(nanos, nanos.length * 2);
            }
            nanos[count++] = elapsed;
            opNanos += elapsed;
        }
        // Lets a profile taken with -Pprofile be split by benchmark.
        RefinedCreation.LOGGER.debug("BENCH window {} {} {}", id, windowStart, System.currentTimeMillis());
        return record(id, description, Arrays.copyOf(nanos, count), allocationKnown ? allocated / count : -1);
    }

    /**
     * @return whether a phase past its minimum iterations should run another: the operation hasn't yet run for
     * {@link #MIN_PHASE_NANOS}, and the phase is still within {@link #MAX_PHASE_NANOS}
     */
    private static boolean keepGoing(long opNanos, long phaseStart) {
        return opNanos < MIN_PHASE_NANOS && System.nanoTime() - phaseStart < MAX_PHASE_NANOS;
    }

    /**
     * Records times measured elsewhere, e.g. one per game tick.
     */
    public Result record(String id, String description, long[] nanos, long bytesPerOp) {
        int iterations = nanos.length;
        nanos = nanos.clone();
        Arrays.sort(nanos);
        Result result = new Result(id, description, nanos[iterations / 2] / 1000.0,
                nanos[Math.min(iterations - 1, (int) Math.ceil(iterations * 0.95) - 1)] / 1000.0,
                nanos[iterations - 1] / 1000.0, bytesPerOp, iterations);
        results.add(result);
        RefinedCreation.LOGGER.info("BENCH {} {}: median {} us, p95 {} us, max {} us, {} bytes/op", id, description,
                format(result.medianMicros()), format(result.p95Micros()), format(result.maxMicros()),
                result.bytesPerOp());
        return result;
    }

    /**
     * Records a fact about the run, such as the network's size, for the report.
     */
    public void note(String key, Object value) {
        notes.put(key, String.valueOf(value));
        RefinedCreation.LOGGER.info("BENCH note {}: {}", key, value);
    }

    /**
     * Writes the results to {@code results-<time>.md} and {@code latest.md} in the directory given by the
     * {@code refinedcreation.benchmarkOutput} system property, if it is set.
     */
    public void write() {
        String output = System.getProperty("refinedcreation.benchmarkOutput");
        if (output == null) {
            return;
        }
        StringBuilder md = new StringBuilder("# Refined Creation benchmarks\n\n");
        md.append("Run: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
        for (Map.Entry<String, String> note : notes.entrySet()) {
            md.append("- ").append(note.getKey()).append(": ").append(note.getValue()).append('\n');
        }
        md.append("\n| ID | Operation | Median (µs) | p95 (µs) | Max (µs) | Allocated/op | Iterations |\n");
        md.append("|---|---|---:|---:|---:|---:|---:|\n");
        for (Result r : results) {
            md.append("| ").append(r.id()).append(" | ").append(r.description()).append(" | ")
                    .append(format(r.medianMicros())).append(" | ").append(format(r.p95Micros())).append(" | ")
                    .append(format(r.maxMicros())).append(" | ").append(formatBytes(r.bytesPerOp())).append(" | ")
                    .append(r.iterations()).append(" |\n");
        }
        try {
            Path dir = Path.of(output);
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.writeString(dir.resolve("results-" + stamp + ".md"), md);
            Files.writeString(dir.resolve("latest.md"), md);
            RefinedCreation.LOGGER.info("BENCH results written to {}", dir.resolve("latest.md"));
        } catch (IOException e) {
            RefinedCreation.LOGGER.error("Could not write benchmark results", e);
        }
    }

    private static String format(double micros) {
        return micros >= 100 ? String.format("%,.0f", micros) : String.format("%.1f", micros);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "n/a";
        }
        if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return bytes >= 1024 ? String.format("%.1f KB", bytes / 1024.0) : bytes + " B";
    }
}
