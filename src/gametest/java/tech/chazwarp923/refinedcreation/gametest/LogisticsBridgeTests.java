package tech.chazwarp923.refinedcreation.gametest;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.foundation.item.ItemHelper;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlock;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlockEntity;
import tech.chazwarp923.refinedcreation.bridge.SummaryMerge;
import tech.chazwarp923.refinedcreation.registry.ModBlocks;

@GameTestHolder(RefinedCreation.MODID)
@PrefixGameTestTemplate(false)
public class LogisticsBridgeTests {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 1);
    private static final BlockPos STORAGE = new BlockPos(1, 1, 1);
    private static final BlockPos BRIDGE = new BlockPos(2, 1, 1);
    private static final BlockPos PACKAGER = new BlockPos(3, 1, 1);
    private static final int ORDER_RUNS = 5;

    /**
     * RS creative controller + creative storage + bridge, with a Create packager facing into the bridge.
     * Exercises insert/extract through the bridge, the packager's stock summary, packaging out of the network and
     * unpacking back into it.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 400)
    public static void packagerRoundTripThroughNetwork(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, rsBlock("creative_controller"));
        helper.setBlock(STORAGE, rsBlock("creative_storage_block"));
        helper.setBlock(BRIDGE, ModBlocks.LOGISTICS_BRIDGE.get());
        // A packager's target inventory is the block opposite its facing.
        helper.setBlock(PACKAGER, AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));

        ItemStack[] box = new ItemStack[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(BRIDGE, LogisticsBridgeBlock.ACTIVE, true))
                .thenExecute(() -> {
                    IItemHandler handler = bridgeHandler(helper);
                    for (int count : new int[] {64, 64, 64, 8}) {
                        ItemStack remainder = handler.insertItem(0, new ItemStack(Items.DIAMOND, count), false);
                        helper.assertTrue(remainder.isEmpty(), "Network rejected diamonds: " + remainder);
                    }
                    assertDiamonds(helper, 200);

                    ItemStack extracted = handler.extractItem(diamondSlot(helper), 100, false);
                    helper.assertTrue(extracted.is(Items.DIAMOND) && extracted.getCount() == 64,
                            "Expected to extract one stack of diamonds, got " + extracted);
                    assertDiamonds(helper, 136);
                })
                // Give the packager time to discover the bridge's item handler.
                .thenWaitUntil(() -> helper.assertTrue(packager(helper).targetInventory.hasInventory(),
                        "Packager has not found the bridge inventory"))
                .thenExecute(() -> {
                    PackagerBlockEntity packager = packager(helper);
                    int summarised = packager.getAvailableItems().getCountOf(new ItemStack(Items.DIAMOND));
                    helper.assertTrue(summarised == 136, "Packager stock summary saw " + summarised + " diamonds");

                    packager.attemptToSend(null);
                    helper.assertFalse(packager.heldBox.isEmpty(), "Packager did not create a package");
                    assertDiamonds(helper, 0);

                    box[0] = packager.heldBox.copy();
                    packager.heldBox = ItemStack.EMPTY;
                })
                .thenWaitUntil(() -> helper.assertTrue(packager(helper).animationTicks == 0,
                        "Packager still animating"))
                .thenExecute(() -> {
                    helper.assertTrue(packager(helper).unwrapBox(box[0], false), "Package was not unpacked");
                    assertDiamonds(helper, 136);
                })
                .thenSucceed();
    }

    /**
     * Packages an order for one item out of a network that holds one of every item in the game, as a Stock Keeper
     * order would. Checks that only the requested item is packaged, in the right amount, and logs how long it took.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 400)
    public static void packagesOrderFromLargeNetwork(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, rsBlock("creative_controller"));
        helper.setBlock(STORAGE, rsBlock("creative_storage_block"));
        helper.setBlock(BRIDGE, ModBlocks.LOGISTICS_BRIDGE.get());
        helper.setBlock(PACKAGER, AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));

        int requested = 2000;

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(BRIDGE, LogisticsBridgeBlock.ACTIVE, true))
                .thenExecute(() -> {
                    IItemHandler handler = bridgeHandler(helper);
                    for (Item item : BuiltInRegistries.ITEM) {
                        if (item != Items.AIR && item != Items.DIAMOND) {
                            handler.insertItem(0, new ItemStack(item), false);
                        }
                    }
                    for (int i = 0; i < 40 * ORDER_RUNS; i++) {
                        handler.insertItem(0, new ItemStack(Items.DIAMOND, 64), false);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(packager(helper).targetInventory.hasInventory(),
                        "Packager has not found the bridge inventory"))
                .thenIdle(1)
                .thenExecute(() -> {
                    // Repeat the order so the logged times include warmed-up runs, not only the first.
                    for (int run = 0; run < ORDER_RUNS; run++) {
                        packageDiamondOrder(helper, requested);
                        assertDiamonds(helper, 40 * 64 * ORDER_RUNS - requested * (run + 1));
                    }
                })
                .thenSucceed();
    }

    /**
     * The bridge caches each slot's stack, so every change made straight to the network, bypassing the bridge, must
     * still show up: amount changes at once, an item that runs out as an empty slot, and a new item type by the
     * next tick. The Packager's stock summary must follow too.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 200)
    public static void bridgeViewFollowsNetworkChanges(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, rsBlock("creative_controller"));
        helper.setBlock(STORAGE, rsBlock("creative_storage_block"));
        helper.setBlock(BRIDGE, ModBlocks.LOGISTICS_BRIDGE.get());
        helper.setBlock(PACKAGER, AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));
        ItemResource iron = new ItemResource(Items.IRON_INGOT);
        ItemResource gold = new ItemResource(Items.GOLD_INGOT);
        ItemResource diamond = new ItemResource(Items.DIAMOND);
        ItemStack ironStack = new ItemStack(Items.IRON_INGOT);
        ItemStack goldStack = new ItemStack(Items.GOLD_INGOT);
        int[] slots = new int[3];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(BRIDGE, LogisticsBridgeBlock.ACTIVE, true))
                .thenWaitUntil(() -> helper.assertTrue(packager(helper).targetInventory.hasInventory(),
                        "Packager has not found the bridge inventory"))
                .thenExecute(() -> {
                    storage(helper).insert(iron, 10, Action.EXECUTE, Actor.EMPTY);
                    storage(helper).insert(gold, 5, Action.EXECUTE, Actor.EMPTY);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    IItemHandler handler = bridgeHandler(helper);
                    slots[0] = slotOf(handler, ironStack);
                    slots[1] = slotOf(handler, goldStack);
                    slots[2] = handler.getSlots();
                    helper.assertTrue(handler.getStackInSlot(slots[0]).getCount() == 10, "Bridge shows "
                            + handler.getStackInSlot(slots[0]) + " instead of 10 iron");
                    helper.assertTrue(packager(helper).getAvailableItems().getCountOf(ironStack) == 10,
                            "Packager summary should list 10 iron");

                    // Straight into the network: the cached slot must update in the same tick.
                    storage(helper).insert(iron, 3, Action.EXECUTE, Actor.EMPTY);
                    helper.assertTrue(handler.getStackInSlot(slots[0]).getCount() == 13, "Bridge shows "
                            + handler.getStackInSlot(slots[0]) + " instead of 13 iron");
                    helper.assertTrue(packager(helper).getAvailableItems().getCountOf(ironStack) == 13,
                            "Packager summary should list 13 iron");

                    // Gold runs out: its slot empties but stays, so the other slots keep their indices.
                    storage(helper).extract(gold, 5, Action.EXECUTE, Actor.EMPTY);
                    helper.assertTrue(handler.getStackInSlot(slots[1]).isEmpty(), "Gold slot should be empty");
                    helper.assertTrue(packager(helper).getAvailableItems().getCountOf(goldStack) == 0,
                            "Packager summary should list no gold");
                    storage(helper).insert(gold, 2, Action.EXECUTE, Actor.EMPTY);
                    helper.assertTrue(handler.getStackInSlot(slots[1]).getCount() == 2, "Bridge shows "
                            + handler.getStackInSlot(slots[1]) + " instead of 2 gold");

                    storage(helper).insert(diamond, 7, Action.EXECUTE, Actor.EMPTY);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    IItemHandler handler = bridgeHandler(helper);
                    helper.assertTrue(handler.getSlots() == slots[2] + 1, "Bridge should have one more slot for"
                            + " diamonds, has " + handler.getSlots() + " instead of " + slots[2]);
                    assertDiamonds(helper, 7);
                    helper.assertTrue(packager(helper).getAvailableItems().getCountOf(new ItemStack(Items.DIAMOND))
                            == 7, "Packager summary should list 7 diamonds");
                    helper.assertTrue(packager(helper).getAvailableItems().getCountOf(ironStack) == 13,
                            "Packager summary should still list 13 iron");
                })
                .thenSucceed();
    }

    /**
     * The fast paths that the mixins add for a bridge give what Create's own code would: the Packager's summary,
     * the network summary merge, and filtered extraction.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 200)
    public static void fastPathsMatchCreate(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, rsBlock("creative_controller"));
        helper.setBlock(STORAGE, rsBlock("creative_storage_block"));
        helper.setBlock(BRIDGE, ModBlocks.LOGISTICS_BRIDGE.get());
        helper.setBlock(PACKAGER, AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("Special"));
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(BRIDGE, LogisticsBridgeBlock.ACTIVE, true))
                .thenWaitUntil(() -> helper.assertTrue(packager(helper).targetInventory.hasInventory(),
                        "Packager has not found the bridge inventory"))
                .thenExecute(() -> {
                    storage(helper).insert(ItemResource.ofItemStack(iron), 10, Action.EXECUTE, Actor.EMPTY);
                    storage(helper).insert(ItemResource.ofItemStack(namedIron), 7, Action.EXECUTE, Actor.EMPTY);
                    storage(helper).insert(ItemResource.ofItemStack(gold), 500, Action.EXECUTE, Actor.EMPTY);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    IItemHandler handler = bridgeHandler(helper);
                    InventorySummary expected = new InventorySummary();
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        expected.add(handler.getStackInSlot(slot));
                    }
                    InventorySummary actual = packager(helper).getAvailableItems();
                    assertSameSummary(helper, expected, actual, "Packager summary");

                    // A merge into a summary that already has some of the items, and one that has none.
                    InventorySummary other = new InventorySummary();
                    other.add(iron, 3);
                    other.add(new ItemStack(Items.DIAMOND), 4);
                    other.contributingLinks = 1;
                    actual.contributingLinks = 1;
                    InventorySummary slow = new InventorySummary();
                    slow.add(other);
                    slow.add(actual);
                    InventorySummary fast = new InventorySummary();
                    SummaryMerge.add(fast, other);
                    SummaryMerge.add(fast, actual);
                    assertSameSummary(helper, slow, fast, "Merged summary");
                    helper.assertTrue(fast.contributingLinks == 2, "Merged summary counts "
                            + fast.contributingLinks + " links");
                    helper.assertTrue(actual.getCountOf(iron) == 10, "Merging changed the source summary");

                    // Filtered extraction still finds the one matching variant, and nothing for no match.
                    ItemStack found = ItemHelper.extract(handler,
                            stack -> ItemStack.isSameItemSameComponents(stack, namedIron), true);
                    helper.assertTrue(ItemStack.isSameItemSameComponents(found, namedIron) && found.getCount() == 7,
                            "Filtered extract found " + found);
                    helper.assertTrue(ItemHelper.extract(handler, stack -> stack.is(Items.DIAMOND), true).isEmpty(),
                            "Filtered extract found something for no match");
                    ItemStack taken = ItemHelper.extract(handler, stack -> stack.is(Items.GOLD_INGOT), 64, false);
                    helper.assertTrue(taken.getCount() == 64, "Exact extract took " + taken);
                    helper.assertTrue(storage(helper).get(ItemResource.ofItemStack(gold)) == 436,
                            "Network should hold 436 gold after the extract");
                })
                .thenSucceed();
    }

    private static void assertSameSummary(GameTestHelper helper, InventorySummary expected, InventorySummary actual,
                                          String what) {
        helper.assertTrue(expected.getStacks().size() == actual.getStacks().size(), what + " lists "
                + actual.getStacks().size() + " entries instead of " + expected.getStacks().size());
        for (BigItemStack entry : expected.getStacks()) {
            helper.assertTrue(actual.getCountOf(entry.stack) == entry.count, what + " lists "
                    + actual.getCountOf(entry.stack) + " of " + entry.stack + " instead of " + entry.count);
        }
        helper.assertTrue(expected.getTotalCount() == actual.getTotalCount(), what + " totals "
                + actual.getTotalCount() + " instead of " + expected.getTotalCount());
    }

    private static StorageNetworkComponent storage(GameTestHelper helper) {
        LogisticsBridgeBlockEntity bridge = helper.getBlockEntity(BRIDGE);
        helper.assertTrue(bridge.getNetwork() != null, "Bridge has no network");
        return bridge.getNetwork().getComponent(StorageNetworkComponent.class);
    }

    private static int slotOf(IItemHandler handler, ItemStack item) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), item)) {
                return slot;
            }
        }
        throw new IllegalStateException("No slot holds " + item);
    }

    private static void packageDiamondOrder(GameTestHelper helper, int requested) {
        PackagerBlockEntity packager = packager(helper);
        packager.heldBox = ItemStack.EMPTY;
        packager.queuedExitingPackages.clear();
        int slots = bridgeHandler(helper).getSlots();
        List<PackagingRequest> requests = new ArrayList<>(List.of(PackagingRequest.create(
                new ItemStack(Items.DIAMOND), requested, "test", 0, new MutableBoolean(true), 0, 1, null)));

        long start = System.nanoTime();
        // Mirrors LogisticsManager.performPackageRequests.
        for (int i = 0; i < 100 && !requests.isEmpty(); i++) {
            packager.attemptToSend(requests);
        }
        long packaging = System.nanoTime() - start;
        start = System.nanoTime();
        packager.getAvailableItems();
        long summary = System.nanoTime() - start;
        RefinedCreation.LOGGER.info("Packaged {} diamonds from a {}-slot bridge in {} us; summary rebuild {} us",
                requested, slots, packaging / 1000, summary / 1000);

        helper.assertTrue(requests.isEmpty(), "Order was not fully packaged");
        List<ItemStack> boxes = new ArrayList<>();
        boxes.add(packager.heldBox);
        packager.queuedExitingPackages.forEach(entry -> {
            for (int i = 0; i < entry.count; i++) {
                boxes.add(entry.stack);
            }
        });
        int packaged = 0;
        for (ItemStack box : boxes) {
            ItemStackHandler contents = PackageItem.getContents(box);
            for (int slot = 0; slot < contents.getSlots(); slot++) {
                ItemStack stack = contents.getStackInSlot(slot);
                helper.assertTrue(stack.isEmpty() || stack.is(Items.DIAMOND), "Package contained " + stack);
                packaged += stack.getCount();
            }
        }
        helper.assertTrue(packaged == requested, "Packaged " + packaged + " diamonds");
    }

    private static Block rsBlock(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", path));
    }

    private static IItemHandler bridgeHandler(GameTestHelper helper) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(BRIDGE), Direction.EAST);
        helper.assertTrue(handler != null, "Bridge exposes no item handler");
        return handler;
    }

    private static PackagerBlockEntity packager(GameTestHelper helper) {
        return helper.getBlockEntity(PACKAGER);
    }

    private static int diamondSlot(GameTestHelper helper) {
        IItemHandler handler = bridgeHandler(helper);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(Items.DIAMOND)) {
                return slot;
            }
        }
        return -1;
    }

    private static void assertDiamonds(GameTestHelper helper, int expected) {
        IItemHandler handler = bridgeHandler(helper);
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(Items.DIAMOND)) {
                total += stack.getCount();
            }
        }
        helper.assertTrue(total == expected, "Expected " + expected + " diamonds in network, found " + total);
    }
}
