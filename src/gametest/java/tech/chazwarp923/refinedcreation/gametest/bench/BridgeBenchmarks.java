package tech.chazwarp923.refinedcreation.gametest.bench;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import tech.chazwarp923.refinedcreation.bridge.BridgeTestAccess;
import tech.chazwarp923.refinedcreation.bridge.NetworkItemHandler;

/**
 * Benchmarks of the Logistics Bridge: what Create's Packager, Stock Link, funnels and the bridge's own tick cost
 * on the large network.
 */
final class BridgeBenchmarks {
    private BridgeBenchmarks() {
    }

    static void run(LargeNetwork net, Bench bench) {
        GameTestHelper helper = net.helper();
        PackagerBlockEntity packager = net.bridgePackager();
        NetworkItemHandler handler = net.itemHandler();
        ItemStack diamond = new ItemStack(Items.DIAMOND);

        // B1: every network change makes the Packager rebuild its summary the next time anyone asks for stock.
        bench.measure("B1", "Packager stock summary rebuild after a network change", 5, 30,
                () -> {
                    net.touchNetwork();
                    net.nextTick();
                },
                packager::getAvailableItems);
        long storedDiamonds = net.storage().get(new ItemResource(Items.DIAMOND));
        int summarised = packager.getAvailableItems().getCountOf(diamond);
        helper.assertTrue(summarised == storedDiamonds, "Summary lists " + summarised + " diamonds, network holds "
                + storedDiamonds);

        // B2: what a Stock Keeper, Stock Link request or Workbench pays for the whole logistics network's stock.
        bench.measure("B2a", "Network summary (accurate), Packager summary cached", 10, 50,
                LogisticsManager.ACCURATE_SUMMARIES::invalidateAll,
                () -> LogisticsManager.getSummaryOfNetwork(net.frequency(), true));
        bench.measure("B2b", "Network summary (accurate) after a network change", 5, 30,
                () -> {
                    net.touchNetwork();
                    net.nextTick();
                    LogisticsManager.ACCURATE_SUMMARIES.invalidateAll();
                },
                () -> LogisticsManager.getSummaryOfNetwork(net.frequency(), true));
        InventorySummary network = LogisticsManager.getSummaryOfNetwork(net.frequency(), true);
        for (ItemResource output : net.patternOutputs()) {
            helper.assertTrue(network.getCountOf(output.toItemStack()) >= BigItemStack.INF,
                    "Network summary lost craftable " + output);
        }

        // B3: a big single-item order, e.g. 2,000 diamonds from a Stock Keeper.
        int[] packaged = new int[1];
        long diamondsBefore = net.storage().get(new ItemResource(Items.DIAMOND));
        int b3Runs = 3 + 20;
        bench.measure("B3", "Package a 2,000-item order of one item", 3, 20,
                () -> {
                    LargeNetwork.clearOutgoing(packager);
                    net.nextTick();
                },
                () -> packaged[0] = packageOrder(packager, List.of(request(diamond, 2000))));
        helper.assertTrue(packaged[0] == 2000, "Packaged " + packaged[0] + " of 2,000 diamonds");
        long diamondsTaken = diamondsBefore - net.storage().get(new ItemResource(Items.DIAMOND));
        helper.assertTrue(diamondsTaken == 2000L * b3Runs, "Network lost " + diamondsTaken + " diamonds");

        // B4: an order of many different items, e.g. a factory restock.
        List<ItemStack> mixed = new ArrayList<>();
        for (ItemResource type : net.stockedTypes()) {
            if (mixed.size() < 20 && type.item().getDefaultMaxStackSize() == 64
                    && net.storage().get(type) >= 100L * 30) {
                mixed.add(type.toItemStack(1));
            }
        }
        helper.assertTrue(mixed.size() == 20, "Found only " + mixed.size() + " well-stocked item types");
        bench.measure("B4", "Package a 20-item-type order (100 of each)", 3, 20,
                () -> {
                    LargeNetwork.clearOutgoing(packager);
                    net.nextTick();
                },
                () -> {
                    List<PackagingRequest> requests = new ArrayList<>();
                    for (ItemStack stack : mixed) {
                        requests.add(request(stack, 100));
                    }
                    packaged[0] = packageOrder(packager, requests);
                });
        helper.assertTrue(packaged[0] == 2000, "Packaged " + packaged[0] + " of 2,000 mixed items");

        // B5: a delivery arriving at the bridge's Packager.
        List<ItemStack> delivery = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            delivery.add(mixed.get(i).copyWithCount(16));
        }
        ItemStack box = PackageItem.containing(delivery);
        boolean[] unpacked = new boolean[1];
        bench.measure("B5", "Unpack a 9-stack package into the network", 10, 50,
                () -> {
                    LargeNetwork.clearOutgoing(packager);
                    net.nextTick();
                },
                () -> unpacked[0] = packager.unwrapBox(box.copy(), false));
        helper.assertTrue(unpacked[0], "Package was not unpacked");

        // B6: the bridge re-reads RS's craftable outputs once a second.
        bench.measure("B6", "20 bridge crafting ticks (one craftables refresh)", 5, 30, null,
                () -> {
                    for (int i = 0; i < 20; i++) {
                        BridgeTestAccess.craftingTick(net.bridge().getCrafting());
                    }
                });

        // B7: the bridge's own tick, which runs whether or not anything uses it.
        bench.measure("B7a", "Bridge server tick, network unchanged", 20, 200, net::nextTick,
                () -> BridgeTestAccess.serverTick(net.bridge()));
        bench.measure("B7b", "Bridge server tick, network changed", 20, 200,
                () -> {
                    net.touchNetwork();
                    net.nextTick();
                },
                () -> BridgeTestAccess.serverTick(net.bridge()));

        // B8: a funnel or hopper with a filter pulling from the bridge: Create walks the slots until it finds one.
        net.nextTick();
        int slots = handler.getSlots();
        ItemStack target = handler.getStackInSlot(slots / 2).copyWithCount(1);
        helper.assertFalse(target.isEmpty(), "No item in the middle slot");
        ItemStack[] found = new ItemStack[1];
        bench.measure("B8", "Filtered funnel extract (simulated), item in the middle of " + slots + " slots", 3, 20,
                null,
                () -> found[0] = ItemHelper.extract(handler, stack -> ItemStack.isSameItemSameComponents(stack, target),
                        true));
        helper.assertTrue(ItemStack.isSameItemSameComponents(found[0], target),
                "Funnel extract found " + found[0] + " instead of " + target);
        bench.measure("B8b", "Filtered funnel extract (simulated) after a network change", 3, 20,
                () -> {
                    net.touchNetwork();
                    net.nextTick();
                },
                () -> found[0] = ItemHelper.extract(handler, stack -> ItemStack.isSameItemSameComponents(stack, target),
                        true));
        helper.assertTrue(ItemStack.isSameItemSameComponents(found[0], target),
                "Funnel extract found " + found[0] + " instead of " + target);
        LargeNetwork.clearOutgoing(packager);
    }

    static PackagingRequest request(ItemStack item, int count) {
        return PackagingRequest.create(item.copyWithCount(1), count, "bench", 0, new MutableBoolean(true), 0, 1,
                null);
    }

    /**
     * Packages {@code requests} the way {@code LogisticsManager.performPackageRequests} does.
     *
     * @return how many items the Packager's outgoing packages hold afterwards
     */
    static int packageOrder(PackagerBlockEntity packager, List<PackagingRequest> requests) {
        List<PackagingRequest> queue = new ArrayList<>(requests);
        for (int i = 0; i < 100 && !queue.isEmpty(); i++) {
            packager.attemptToSend(queue);
        }
        int total = 0;
        List<ItemStack> boxes = new ArrayList<>();
        if (!packager.heldBox.isEmpty()) {
            boxes.add(packager.heldBox);
        }
        for (BigItemStack queued : packager.queuedExitingPackages) {
            for (int i = 0; i < queued.count; i++) {
                boxes.add(queued.stack);
            }
        }
        for (ItemStack packed : boxes) {
            ItemStackHandler contents = PackageItem.getContents(packed);
            for (int slot = 0; slot < contents.getSlots(); slot++) {
                total += contents.getStackInSlot(slot).getCount();
            }
        }
        return total;
    }
}
