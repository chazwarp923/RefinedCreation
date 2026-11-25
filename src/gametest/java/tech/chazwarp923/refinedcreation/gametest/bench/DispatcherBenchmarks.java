package tech.chazwarp923.refinedcreation.gametest.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.autocrafting.PlatformPatternProviderExternalPatternSink;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import tech.chazwarp923.refinedcreation.dispatcher.DispatcherSinkFactory;

/**
 * Benchmarks of the Logistics Dispatcher: one pattern step, and a whole RS task running through it.
 */
final class DispatcherBenchmarks {
    private static final int CRAFTS = 64;

    private static int dispatched;

    private DispatcherBenchmarks() {
    }

    static void run(LargeNetwork net, Bench bench) {
        GameTestHelper helper = net.helper();
        PackagerBlockEntity packager = net.dispatcherPackager();
        // The Autocrafter faces east into the dispatcher, so the sink is created for its west side.
        PlatformPatternProviderExternalPatternSink sink = DispatcherSinkFactory.INSTANCE.create(net.level(),
                net.dispatcherAbsolute(), Direction.WEST);
        List<ResourceAmount> step = new ArrayList<>();
        for (Item ingredient : LargeNetwork.INGREDIENTS) {
            step.add(new ResourceAmount(new ItemResource(ingredient), 4));
        }

        // D1: what RS calls on the dispatcher for every step of a processing pattern.
        ExternalPatternSink.Result[] results = new ExternalPatternSink.Result[2];
        bench.measure("D1", "Dispatcher step: simulate + execute, 9 ingredients", 20, 200,
                () -> LargeNetwork.clearOutgoing(packager),
                () -> {
                    results[0] = sink.accept(step, Action.SIMULATE);
                    results[1] = sink.accept(step, Action.EXECUTE);
                });
        helper.assertTrue(results[0] == ExternalPatternSink.Result.ACCEPTED
                && results[1] == ExternalPatternSink.Result.ACCEPTED, "Dispatcher did not accept the step");
        helper.assertTrue(packager.queuedExitingPackages.size() == 1
                        && PackageItem.getAddress(packager.queuedExitingPackages.get(0).stack)
                        .equals(LargeNetwork.FACTORY_ADDRESS),
                "Dispatcher queued " + packager.queuedExitingPackages.size() + " packages");
        LargeNetwork.clearOutgoing(packager);

        // D2: RS works out a task over the whole network before the dispatcher sees anything. Mostly RS's cost.
        @SuppressWarnings("unchecked")
        Optional<TaskId>[] task = new Optional[1];
        bench.measure("D2", "Start an RS task for " + CRAFTS + " crafts through the dispatcher", 2, 10,
                () -> net.autocrafting().cancelAll(),
                () -> task[0] = startTask(net));
        helper.assertTrue(task[0].isPresent(), "RS did not start the task");
        net.autocrafting().cancelAll();
    }

    /**
     * Starts the task whose ticks {@link #carryOff} then follows.
     */
    static void startDispatch(LargeNetwork net) {
        LargeNetwork.clearOutgoing(net.dispatcherPackager());
        net.autocrafting().cancelAll();
        dispatched = 0;
        net.helper().assertTrue(startTask(net).isPresent(), "RS did not start the task");
    }

    /**
     * Runs every tick while the task is dispatched: counts the packages sent and carries them off, as a conveyor
     * would, so the dispatcher never fills up.
     */
    static void carryOff(LargeNetwork net) {
        PackagerBlockEntity packager = net.dispatcherPackager();
        if (!packager.heldBox.isEmpty()) {
            dispatched++;
        }
        for (BigItemStack queued : packager.queuedExitingPackages) {
            dispatched += queued.count;
        }
        LargeNetwork.clearOutgoing(packager);
    }

    static void finishDispatch(LargeNetwork net, Bench bench, long[] tickNanos) {
        bench.record("D3", "Server tick while RS dispatches a " + CRAFTS + "-craft task", tickNanos, -1);
        bench.note("Packages dispatched in " + tickNanos.length + " ticks", dispatched);
        net.helper().assertTrue(dispatched > 0, "No packages were dispatched");
        net.autocrafting().cancelAll();
        LargeNetwork.clearOutgoing(net.dispatcherPackager());
    }

    private static Optional<TaskId> startTask(LargeNetwork net) {
        return net.autocrafting().startTask(net.dispatchOutput(), CRAFTS, Actor.EMPTY, false,
                CancellationToken.NONE);
    }
}
