package tech.chazwarp923.refinedcreation.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlock;
import tech.chazwarp923.refinedcreation.registry.ModBlocks;

@GameTestHolder(RefinedCreation.MODID)
@PrefixGameTestTemplate(false)
public class LogisticsBridgeTests {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 1);
    private static final BlockPos STORAGE = new BlockPos(1, 1, 1);
    private static final BlockPos BRIDGE = new BlockPos(2, 1, 1);
    private static final BlockPos PACKAGER = new BlockPos(3, 1, 1);

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
