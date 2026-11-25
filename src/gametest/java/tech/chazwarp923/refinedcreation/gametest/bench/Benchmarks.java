package tech.chazwarp923.refinedcreation.gametest.bench;

import java.util.Collection;
import java.util.List;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlock;

/**
 * The performance benchmarks, run on a {@link LargeNetwork}. They only register when the
 * {@code refinedcreation.benchmarks} system property is set, which {@code ./gradlew runBenchmarks} does, and run
 * in their own batch so the normal tests don't slow them down.
 * <p>
 * Every benchmark also checks its result, but none has a time limit: timings vary too much between machines.
 */
@GameTestHolder(RefinedCreation.MODID)
public class Benchmarks {
    private static final int IDLE_TICKS = 100;
    private static final int DISPATCH_TICKS = 60;

    @GameTestGenerator
    public static Collection<TestFunction> benchmarks() {
        if (!Boolean.getBoolean("refinedcreation.benchmarks")) {
            return List.of();
        }
        return List.of(new TestFunction("refinedcreation_benchmarks", "refinedcreation.benchmarks.large_network",
                RefinedCreation.MODID + ":empty_16x8x16", 200_000, 0, true, Benchmarks::largeNetwork));
    }

    private static void largeNetwork(GameTestHelper helper) {
        LargeNetwork net = new LargeNetwork(helper);
        Bench bench = new Bench();
        MinecraftServer server = helper.getLevel().getServer();
        int[] dispatchStart = new int[1];
        net.place();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(LargeNetwork.BRIDGE, LogisticsBridgeBlock.ACTIVE,
                        true))
                .thenWaitUntil(() -> helper.assertTrue(net.storageSources() == LargeNetwork.STORAGE_BLOCKS,
                        "Network has " + net.storageSources() + " storage sources"))
                .thenExecute(() -> {
                    long start = System.nanoTime();
                    net.fill();
                    bench.note("Network fill time", (System.nanoTime() - start) / 1_000_000 + " ms");
                })
                .thenWaitUntil(() -> {
                    String waiting = net.readiness();
                    helper.assertTrue(waiting == null, waiting);
                })
                .thenExecute(() -> net.describe(bench))
                .thenIdle(IDLE_TICKS)
                .thenExecute(() -> bench.record("N0", "Server tick, large network idle", lastTicks(server,
                        IDLE_TICKS), -1))
                .thenExecute(() -> net.rewinding(() -> BridgeBenchmarks.run(net, bench)))
                .thenExecute(() -> net.rewinding(() -> WorkbenchBenchmarks.run(net, bench)))
                .thenExecute(() -> net.rewinding(() -> DispatcherBenchmarks.run(net, bench)))
                .thenExecute(() -> {
                    DispatcherBenchmarks.startDispatch(net);
                    dispatchStart[0] = server.getTickCount();
                })
                .thenExecuteFor(DISPATCH_TICKS, () -> DispatcherBenchmarks.carryOff(net))
                // Only the ticks after the one that started the task, which the benchmark tick before it may slow.
                .thenExecute(() -> DispatcherBenchmarks.finishDispatch(net, bench, lastTicks(server,
                        server.getTickCount() - dispatchStart[0] - 1)))
                .thenExecute(bench::write)
                .thenSucceed();
    }

    /**
     * @return how long each of the last {@code count} finished server ticks took
     */
    private static long[] lastTicks(MinecraftServer server, int count) {
        long[] all = server.getTickTimesNanos();
        long[] ticks = new long[count];
        StringBuilder slow = new StringBuilder();
        for (int i = 0; i < count; i++) {
            ticks[i] = all[Math.floorMod(server.getTickCount() - 1 - i, all.length)];
            if (ticks[i] > 5_000_000) {
                slow.append(" -").append(i + 1).append(':').append(ticks[i] / 1_000_000).append("ms");
            }
        }
        if (!slow.isEmpty()) {
            RefinedCreation.LOGGER.info("BENCH slow ticks (ticks ago:time):{}", slow);
        }
        return ticks;
    }
}
