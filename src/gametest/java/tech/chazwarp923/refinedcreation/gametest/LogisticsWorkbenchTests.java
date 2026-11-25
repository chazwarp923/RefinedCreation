package tech.chazwarp923.refinedcreation.gametest;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.registry.ModBlocks;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchBlockEntity;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchMenu;

@GameTestHolder(RefinedCreation.MODID)
@PrefixGameTestTemplate(false)
public class LogisticsWorkbenchTests {
    private static final BlockPos CHEST = new BlockPos(0, 1, 2);
    private static final BlockPos CHEST_PACKAGER = new BlockPos(1, 1, 2);
    private static final BlockPos CHEST_LINK = new BlockPos(1, 2, 2);

    private static final BlockPos WORKBENCH = new BlockPos(1, 1, 0);
    /** Faces east, so its target (the block opposite its facing) is the workbench. */
    private static final BlockPos WORKBENCH_PACKAGER = new BlockPos(2, 1, 0);

    /**
     * Ordering an item from the stock list makes the network package it, addressed to the delivery address.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 200)
    public static void requestPackagesItemsToDeliveryAddress(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.setBlock(CHEST_PACKAGER, packagerFacingEast());
        helper.setBlock(CHEST_LINK, AllBlocks.STOCK_LINK.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
        helper.setBlock(WORKBENCH, ModBlocks.LOGISTICS_WORKBENCH.get());

        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 40));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        packagerAt(helper, CHEST_PACKAGER).targetInventory.hasInventory(),
                        "Chest packager has not found the chest"))
                .thenExecute(() -> {
                    LogisticsWorkbenchBlockEntity workbench = workbench(helper);
                    PackagerLinkBlockEntity link = helper.getBlockEntity(CHEST_LINK);
                    workbench.setFrequency(link.behaviour.freqId);
                    workbench.setDeliveryAddress("Workbench");

                    int stock = workbench.getNetworkStock(true).getCountOf(new ItemStack(Items.IRON_INGOT));
                    helper.assertTrue(stock == 40, "Workbench should see 40 iron on the network, saw " + stock);

                    workbench.requestItem(new ItemStack(Items.IRON_INGOT), 10);
                    helper.assertTrue(chest.getItem(0).getCount() == 30,
                            "Network should have packaged 10 iron, chest holds " + chest.getItem(0).getCount());

                    List<ItemStack> boxes = outgoing(packagerAt(helper, CHEST_PACKAGER));
                    helper.assertTrue(boxes.size() == 1, "Expected one package, found " + boxes.size());
                    ItemStack box = boxes.get(0);
                    helper.assertTrue(PackageItem.matchAddress(box, "Workbench"),
                            "Package addressed to '" + PackageItem.getAddress(box) + "'");
                    helper.assertTrue(count(PackageItem.getContents(box), Items.IRON_INGOT) == 10,
                            "Package should hold 10 iron");
                })
                .thenSucceed();
    }

    /**
     * Deliveries unpack into the buffer. Shift-clicking the result crafts until the buffer runs out, refilling
     * the grid each time, and a later delivery refills the remembered pattern on its own.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 200)
    public static void deliveriesRefillGridAndShiftCraftingDrainsBuffer(GameTestHelper helper) {
        helper.setBlock(WORKBENCH, ModBlocks.LOGISTICS_WORKBENCH.get());
        helper.setBlock(WORKBENCH_PACKAGER, packagerFacingEast());

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        LogisticsWorkbenchBlockEntity workbench = workbench(helper);
        LogisticsWorkbenchMenu menu = new LogisticsWorkbenchMenu(1, player.getInventory(), workbench);
        player.containerMenu = menu;

        // Two planks stacked vertically: four sticks.
        workbench.getCraftGrid().setItem(0, new ItemStack(Items.OAK_PLANKS));
        workbench.getCraftGrid().setItem(3, new ItemStack(Items.OAK_PLANKS));
        helper.assertTrue(menu.getSlot(LogisticsWorkbenchMenu.RESULT_SLOT).getItem().is(Items.STICK),
                "Result should be sticks, was " + menu.getSlot(LogisticsWorkbenchMenu.RESULT_SLOT).getItem());

        PackagerBlockEntity packager = packagerAt(helper, WORKBENCH_PACKAGER);
        ItemStack delivery = PackageItem.containing(List.of(new ItemStack(Items.OAK_PLANKS, 10)));
        helper.assertTrue(packager.unwrapBox(delivery, false), "Packager refused to unpack into the workbench");
        helper.assertTrue(count(workbench.getBuffer(), Items.OAK_PLANKS) == 10, "Buffer should hold 10 planks");

        // 2 planks in the grid + 10 in the buffer = 6 crafts.
        menu.clicked(LogisticsWorkbenchMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE, player);
        int sticks = player.getInventory().countItem(Items.STICK);
        helper.assertTrue(sticks == 24, "Expected 24 sticks from shift-crafting, got " + sticks);
        helper.assertTrue(count(workbench.getBuffer(), Items.OAK_PLANKS) == 0, "Buffer should be used up");
        helper.assertTrue(workbench.getCraftGrid().isEmpty(), "Grid should be empty once the buffer ran out");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(packager.animationTicks == 0, "Packager still animating"))
                .thenExecute(() -> helper.assertTrue(packager.unwrapBox(
                        PackageItem.containing(List.of(new ItemStack(Items.OAK_PLANKS, 5))), false),
                        "Second delivery refused"))
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(workbench.getCraftGrid().getItem(0).is(Items.OAK_PLANKS)
                                    && workbench.getCraftGrid().getItem(3).is(Items.OAK_PLANKS),
                            "Grid should refill the remembered pattern from the new delivery");
                    helper.assertTrue(count(workbench.getBuffer(), Items.OAK_PLANKS) == 3,
                            "Buffer should keep the 3 planks the pattern didn't need");
                })
                .thenSucceed();
    }

    /**
     * Send packages the return slots onto the adjacent Packager, addressed to the return address.
     */
    @GameTest(template = "empty_6x3x3")
    public static void sendReturnsQueuesAddressedPackages(GameTestHelper helper) {
        helper.setBlock(WORKBENCH, ModBlocks.LOGISTICS_WORKBENCH.get());
        helper.setBlock(WORKBENCH_PACKAGER, packagerFacingEast());

        LogisticsWorkbenchBlockEntity workbench = workbench(helper);
        workbench.setReturnAddress("Storage");
        workbench.getReturns().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        workbench.getReturns().setStackInSlot(1, new ItemStack(Items.DIRT, 12));
        workbench.sendReturns();

        for (int slot = 0; slot < workbench.getReturns().getSlots(); slot++) {
            helper.assertTrue(workbench.getReturns().getStackInSlot(slot).isEmpty(), "Return slots should be empty");
        }
        List<ItemStack> boxes = outgoing(packagerAt(helper, WORKBENCH_PACKAGER));
        helper.assertTrue(boxes.size() == 1, "Expected one package, found " + boxes.size());
        ItemStack box = boxes.get(0);
        helper.assertTrue(PackageItem.matchAddress(box, "Storage"), "Package not addressed to Storage");
        ItemStackHandler contents = PackageItem.getContents(box);
        helper.assertTrue(count(contents, Items.COBBLESTONE) == 64 && count(contents, Items.DIRT) == 12,
                "Package contents are wrong");
        helper.succeed();
    }

    /**
     * JEI recipe transfer: the old grid contents go back to the buffer, slots are filled from the buffer
     * first and then the player's inventory, and a max transfer tops the slots up evenly.
     */
    @GameTest(template = "empty_6x3x3")
    public static void recipeTransferFillsGridFromBufferThenInventory(GameTestHelper helper) {
        helper.setBlock(WORKBENCH, ModBlocks.LOGISTICS_WORKBENCH.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        LogisticsWorkbenchBlockEntity workbench = workbench(helper);
        LogisticsWorkbenchMenu menu = new LogisticsWorkbenchMenu(1, player.getInventory(), workbench);
        player.containerMenu = menu;

        workbench.getCraftGrid().setItem(4, new ItemStack(Items.COBBLESTONE, 3));
        workbench.getBuffer().setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 1));
        player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 9));

        // Sticks: planks in slots 0 and 3. JEI offers any plank; only oak is on hand.
        List<ItemStack> planks = List.of(new ItemStack(Items.SPRUCE_PLANKS), new ItemStack(Items.OAK_PLANKS));
        List<List<ItemStack>> options = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            options.add(i == 0 || i == 3 ? planks : List.of());
        }

        workbench.transferRecipe(player, options, false);
        helper.assertTrue(workbench.getCraftGrid().getItem(0).is(Items.OAK_PLANKS)
                        && workbench.getCraftGrid().getItem(3).is(Items.OAK_PLANKS),
                "Planks should be placed in slots 0 and 3");
        helper.assertTrue(workbench.getCraftGrid().getItem(4).isEmpty(), "Old grid contents should be cleared");
        helper.assertTrue(count(workbench.getBuffer(), Items.COBBLESTONE) == 3, "Cobblestone should go to the buffer");
        helper.assertTrue(count(workbench.getBuffer(), Items.OAK_PLANKS) == 0, "The buffer's plank is used first");
        helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == 8,
                "One plank should come from the inventory");
        helper.assertTrue(menu.getSlot(LogisticsWorkbenchMenu.RESULT_SLOT).getItem().is(Items.STICK),
                "Result should be sticks");
        helper.assertTrue(workbench.getPattern().get(0).is(Items.OAK_PLANKS), "Transfer should set the pattern");

        // Max: the 10 planks on hand are split evenly between the two slots.
        workbench.transferRecipe(player, options, true);
        int top = workbench.getCraftGrid().getItem(0).getCount();
        int bottom = workbench.getCraftGrid().getItem(3).getCount();
        helper.assertTrue(top == 5 && bottom == 5, "Max transfer should split 5/5, got " + top + "/" + bottom);
        helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == 0, "All planks should be used");
        helper.succeed();
    }

    /**
     * An exact amount from the amount popup is ordered as asked, and an amount larger than the stock orders
     * what there is.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 200)
    public static void requestOrdersExactAmountUpToStock(GameTestHelper helper) {
        ChestBlockEntity chest = chestNetwork(helper, new ItemStack(Items.IRON_INGOT, 40));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        packagerAt(helper, CHEST_PACKAGER).targetInventory.hasInventory(),
                        "Chest packager has not found the chest"))
                .thenExecute(() -> {
                    tuneToChest(helper);
                    workbench(helper).requestItem(new ItemStack(Items.IRON_INGOT), 25);
                    helper.assertTrue(chest.getItem(0).getCount() == 15,
                            "Network should have packaged 25 iron, chest holds " + chest.getItem(0).getCount());
                    helper.assertTrue(packaged(packagerAt(helper, CHEST_PACKAGER), Items.IRON_INGOT) == 25,
                            "Packages should hold 25 iron");
                })
                // A new tick, so the network's stock summary sees what's left.
                .thenIdle(2)
                .thenExecute(() -> {
                    workbench(helper).requestItem(new ItemStack(Items.IRON_INGOT), 100);
                    helper.assertTrue(chest.getItem(0).isEmpty(),
                            "Asking for more than the stock should order all of it, chest holds "
                                    + chest.getItem(0).getCount());
                    helper.assertTrue(packaged(packagerAt(helper, CHEST_PACKAGER), Items.IRON_INGOT) == 40,
                            "Packages should hold all 40 iron");
                })
                .thenSucceed();
    }

    /**
     * A recipe transfer for several crafts places the recipe, keeps network-only slots in the pattern, and
     * orders those ingredients for every craft.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 200)
    public static void recipeTransferForSeveralCraftsOrdersEachCraft(GameTestHelper helper) {
        ChestBlockEntity chest = chestNetwork(helper, new ItemStack(Items.OAK_PLANKS, 32));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // Sticks: planks in slots 0 and 3, none of them on hand.
        List<List<ItemStack>> options = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            options.add(i == 0 || i == 3 ? List.of(new ItemStack(Items.OAK_PLANKS)) : List.of());
        }

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        packagerAt(helper, CHEST_PACKAGER).targetInventory.hasInventory(),
                        "Chest packager has not found the chest"))
                .thenExecute(() -> {
                    tuneToChest(helper);
                    LogisticsWorkbenchBlockEntity workbench = workbench(helper);
                    Component status = workbench.transferRecipe(player, options, false, 8);

                    helper.assertTrue(status.getContents() instanceof TranslatableContents contents
                                    && contents.getKey().equals("refinedcreation.workbench.status.ordered_crafts"),
                            "Unexpected status: " + status.getString());
                    helper.assertTrue(workbench.getPattern().get(0).is(Items.OAK_PLANKS)
                                    && workbench.getPattern().get(3).is(Items.OAK_PLANKS),
                            "The pattern should keep both network-only plank slots");
                    helper.assertTrue(chest.getItem(0).getCount() == 16,
                            "8 crafts of 2 planks should order 16, chest holds " + chest.getItem(0).getCount());
                    helper.assertTrue(packaged(packagerAt(helper, CHEST_PACKAGER), Items.OAK_PLANKS) == 16,
                            "Packages should hold 16 planks");
                })
                .thenSucceed();
    }

    /**
     * A chest on a Create network through a Packager and Stock Link, plus an untuned workbench.
     */
    private static ChestBlockEntity chestNetwork(GameTestHelper helper, ItemStack contents) {
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.setBlock(CHEST_PACKAGER, packagerFacingEast());
        helper.setBlock(CHEST_LINK, AllBlocks.STOCK_LINK.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
        helper.setBlock(WORKBENCH, ModBlocks.LOGISTICS_WORKBENCH.get());
        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        chest.setItem(0, contents);
        return chest;
    }

    private static void tuneToChest(GameTestHelper helper) {
        PackagerLinkBlockEntity link = helper.getBlockEntity(CHEST_LINK);
        workbench(helper).setFrequency(link.behaviour.freqId);
        workbench(helper).setDeliveryAddress("Workbench");
    }

    /**
     * @return how many of {@code item} the Packager's outgoing packages hold
     */
    private static int packaged(PackagerBlockEntity packager, Item item) {
        int total = 0;
        for (ItemStack box : outgoing(packager)) {
            total += count(PackageItem.getContents(box), item);
        }
        return total;
    }

    private static net.minecraft.world.level.block.state.BlockState packagerFacingEast() {
        return AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST);
    }

    private static LogisticsWorkbenchBlockEntity workbench(GameTestHelper helper) {
        return helper.getBlockEntity(WORKBENCH);
    }

    private static PackagerBlockEntity packagerAt(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    /**
     * @return every package the Packager is holding or has queued to send
     */
    private static List<ItemStack> outgoing(PackagerBlockEntity packager) {
        List<ItemStack> boxes = new ArrayList<>();
        if (!packager.heldBox.isEmpty()) {
            boxes.add(packager.heldBox);
        }
        for (BigItemStack queued : packager.queuedExitingPackages) {
            boxes.add(queued.stack);
        }
        return boxes;
    }

    private static int count(ItemStackHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(item)) {
                total += handler.getStackInSlot(slot).getCount();
            }
        }
        return total;
    }
}
