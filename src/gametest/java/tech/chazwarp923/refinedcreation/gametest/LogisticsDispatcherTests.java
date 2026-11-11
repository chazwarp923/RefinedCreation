package tech.chazwarp923.refinedcreation.gametest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.autocrafting.PatternState;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.content.Items;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlock;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlockEntity;
import tech.chazwarp923.refinedcreation.registry.ModBlocks;

@GameTestHolder(RefinedCreation.MODID)
@PrefixGameTestTemplate(false)
public class LogisticsDispatcherTests {
    // Row z=1: the network and its bridge, with a packager receiving results.
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 1);
    private static final BlockPos STORAGE = new BlockPos(1, 1, 1);
    private static final BlockPos BRIDGE = new BlockPos(2, 1, 1);
    private static final BlockPos BRIDGE_PACKAGER = new BlockPos(3, 1, 1);
    // Row z=0: autocrafter (touching the controller) -> dispatcher -> packager with an address sign.
    private static final BlockPos AUTOCRAFTER = new BlockPos(0, 1, 0);
    private static final BlockPos DISPATCHER = new BlockPos(1, 1, 0);
    private static final BlockPos DISPATCHER_PACKAGER = new BlockPos(2, 1, 0);
    private static final BlockPos SIGN = new BlockPos(2, 2, 0);
    // On top of the bridge packager, connecting downwards into it.
    private static final BlockPos STOCK_LINK = new BlockPos(3, 2, 1);

    private static final String FACTORY_ADDRESS = "factory";
    private static final String HOME_ADDRESS = "home";

    /**
     * Runs a real RS autocrafting task for an external pattern (2 iron ingots -> 1 iron block). The ingredients must
     * leave the network as a package addressed to the dispatcher packager's sign. A result package unpacked through
     * the bridge must then complete the task.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 600)
    public static void autocraftingTaskRoundTripThroughPackages(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, rsBlock("creative_controller"));
        helper.setBlock(STORAGE, rsBlock("creative_storage_block"));
        helper.setBlock(BRIDGE, ModBlocks.LOGISTICS_BRIDGE.get());
        helper.setBlock(BRIDGE_PACKAGER,
                AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));

        helper.setBlock(AUTOCRAFTER, withDirection(rsBlock("autocrafter").defaultBlockState(), Direction.EAST));
        helper.setBlock(DISPATCHER, ModBlocks.LOGISTICS_DISPATCHER.get());
        helper.setBlock(DISPATCHER_PACKAGER,
                AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));
        helper.setBlock(SIGN, Blocks.OAK_SIGN);
        SignBlockEntity sign = helper.getBlockEntity(SIGN);
        sign.setText(new SignText().setMessage(0, Component.literal(FACTORY_ADDRESS)), true);

        ItemResource ironIngot = ItemResource.ofItemStack(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        ItemResource ironBlock = ItemResource.ofItemStack(new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(BRIDGE, LogisticsBridgeBlock.ACTIVE, true))
                .thenExecute(() -> {
                    patternContainer(helper).setItem(0, processingPattern(ironIngot, 2, ironBlock, 1));
                    IItemHandler handler = bridgeHandler(helper);
                    ItemStack remainder = handler.insertItem(0,
                            new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2), false);
                    helper.assertTrue(remainder.isEmpty(), "Network rejected iron ingots: " + remainder);
                })
                .thenWaitUntil(() -> helper.assertTrue(autocrafting(helper).getOutputs().contains(ironBlock),
                        "Network does not know the iron block pattern"))
                .thenExecute(() -> helper.assertTrue(autocrafting(helper)
                        .startTask(ironBlock, 1, Actor.EMPTY, false, CancellationToken.NONE).isPresent(),
                        "Autocrafting task was not started"))
                .thenWaitUntil(() -> helper.assertFalse(dispatcherPackager(helper).heldBox.isEmpty(),
                        "Dispatcher packager has not sent a package"))
                .thenExecute(() -> {
                    ItemStack box = dispatcherPackager(helper).heldBox;
                    helper.assertTrue(FACTORY_ADDRESS.equals(PackageItem.getAddress(box)),
                            "Package addressed to '" + PackageItem.getAddress(box) + "'");
                    ItemStackHandler contents = PackageItem.getContents(box);
                    ItemStack first = contents.getStackInSlot(0);
                    helper.assertTrue(first.is(net.minecraft.world.item.Items.IRON_INGOT) && first.getCount() == 2,
                            "Package holds " + first + " instead of 2 iron ingots");
                    assertCount(helper, net.minecraft.world.item.Items.IRON_INGOT.getDefaultInstance(), 0);
                    helper.assertFalse(autocrafting(helper).getStatuses().isEmpty(), "Task finished too early");

                    // Stand in for the Create factory: send back the result package.
                    ItemStack result = PackageItem.containing(
                            List.of(new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK)));
                    helper.assertTrue(bridgePackager(helper).unwrapBox(result, false),
                            "Result package was not unpacked");
                })
                .thenWaitUntil(() -> helper.assertTrue(autocrafting(helper).getStatuses().isEmpty(),
                        "Autocrafting task did not complete"))
                .thenExecute(() -> assertCount(helper,
                        net.minecraft.world.item.Items.IRON_BLOCK.getDefaultInstance(), 1))
                .thenSucceed();
    }

    /**
     * The reverse direction: a Create logistics order for an item that the RS network can craft but doesn't have.
     * The bridge must list the item as infinite, hold the order while RS crafts it (here through the dispatcher), and
     * then package the crafted item to the order's address.
     */
    @GameTest(template = "empty_6x3x3", timeoutTicks = 600)
    public static void createOrderTriggersRsAutocrafting(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, rsBlock("creative_controller"));
        helper.setBlock(STORAGE, rsBlock("creative_storage_block"));
        helper.setBlock(BRIDGE, ModBlocks.LOGISTICS_BRIDGE.get());
        helper.setBlock(BRIDGE_PACKAGER,
                AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));
        helper.setBlock(STOCK_LINK, AllBlocks.STOCK_LINK.getDefaultState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));

        helper.setBlock(AUTOCRAFTER, withDirection(rsBlock("autocrafter").defaultBlockState(), Direction.EAST));
        helper.setBlock(DISPATCHER, ModBlocks.LOGISTICS_DISPATCHER.get());
        helper.setBlock(DISPATCHER_PACKAGER,
                AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));
        helper.setBlock(SIGN, Blocks.OAK_SIGN);
        SignBlockEntity sign = helper.getBlockEntity(SIGN);
        sign.setText(new SignText().setMessage(0, Component.literal(FACTORY_ADDRESS)), true);

        ItemResource ironIngot = ItemResource.ofItemStack(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        ItemResource ironBlock = ItemResource.ofItemStack(new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK));
        ItemStack ironBlockStack = new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(BRIDGE, LogisticsBridgeBlock.ACTIVE, true))
                .thenExecute(() -> {
                    patternContainer(helper).setItem(0, processingPattern(ironIngot, 2, ironBlock, 1));
                    ItemStack remainder = bridgeHandler(helper).insertItem(0,
                            new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2), false);
                    helper.assertTrue(remainder.isEmpty(), "Network rejected iron ingots: " + remainder);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        LogisticsManager.getSummaryOfNetwork(freq(helper), true).getCountOf(ironBlockStack)
                                >= BigItemStack.INF,
                        "Logistics network does not list the craftable iron block as infinite"))
                .thenExecute(() -> {
                    // Restock gauges read the packager's own summary, which must keep real counts.
                    int local = bridgePackager(helper).getAvailableItems().getCountOf(ironBlockStack);
                    helper.assertTrue(local == 0, "Packager's own summary shows " + local + " iron blocks");

                    boolean sent = LogisticsManager.broadcastPackageRequest(freq(helper), RequestType.PLAYER,
                            PackageOrderWithCrafts.simple(List.of(new BigItemStack(ironBlockStack.copy(), 1))),
                            null, HOME_ADDRESS);
                    helper.assertTrue(sent, "Order was not accepted by the logistics network");
                    helper.assertTrue(bridge(helper).getCrafting().getHeldOrderCount() == 1,
                            "Bridge is not holding the order for autocrafting");
                    helper.assertTrue(bridgePackager(helper).heldBox.isEmpty()
                                    && bridgePackager(helper).queuedExitingPackages.isEmpty(),
                            "Bridge packager sent a package before the item was crafted");
                    helper.assertFalse(autocrafting(helper).getStatuses().isEmpty(), "No RS task was started");
                })
                .thenWaitUntil(() -> helper.assertFalse(dispatcherPackager(helper).heldBox.isEmpty(),
                        "Dispatcher packager has not sent the ingredients"))
                .thenExecute(() -> helper.assertTrue(bridgePackager(helper).unwrapBox(
                                PackageItem.containing(List.of(ironBlockStack.copy())), false),
                        "Result package was not unpacked"))
                .thenWaitUntil(() -> helper.assertFalse(bridgePackager(helper).heldBox.isEmpty(),
                        "Bridge packager has not sent the crafted item"))
                .thenExecute(() -> {
                    ItemStack box = bridgePackager(helper).heldBox;
                    helper.assertTrue(HOME_ADDRESS.equals(PackageItem.getAddress(box)),
                            "Order package addressed to '" + PackageItem.getAddress(box) + "'");
                    ItemStack first = PackageItem.getContents(box).getStackInSlot(0);
                    helper.assertTrue(first.is(net.minecraft.world.item.Items.IRON_BLOCK) && first.getCount() == 1,
                            "Order package holds " + first + " instead of 1 iron block");
                    helper.assertTrue(bridge(helper).getCrafting().getHeldOrderCount() == 0,
                            "Bridge is still holding the order");
                    assertCount(helper, ironBlockStack, 0);
                })
                .thenSucceed();
    }

    private static UUID freq(GameTestHelper helper) {
        PackagerLinkBlockEntity link = helper.getBlockEntity(STOCK_LINK);
        return link.behaviour.freqId;
    }

    private static LogisticsBridgeBlockEntity bridge(GameTestHelper helper) {
        return helper.getBlockEntity(BRIDGE);
    }

    private static ItemStack processingPattern(ItemResource input, long inputAmount, ItemResource output,
                                               long outputAmount) {
        ItemStack pattern = new ItemStack(Items.INSTANCE.getPattern());
        pattern.set(DataComponents.INSTANCE.getPatternState(),
                new PatternState(UUID.randomUUID(), PatternType.PROCESSING));
        pattern.set(DataComponents.INSTANCE.getProcessingPatternState(), new ProcessingPatternState(
                List.of(Optional.of(new ProcessingPatternState.ProcessingIngredient(
                        new ResourceAmount(input, inputAmount), List.of()))),
                List.of(Optional.of(new ResourceAmount(output, outputAmount)))));
        return pattern;
    }

    /**
     * The Autocrafter's pattern inventory is package-private; reflection keeps the test free of a GUI round trip.
     */
    private static Container patternContainer(GameTestHelper helper) {
        Object autocrafter = helper.getBlockEntity(AUTOCRAFTER);
        try {
            Field field = autocrafter.getClass().getDeclaredField("patternContainer");
            field.setAccessible(true);
            return (Container) field.get(autocrafter);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot reach the Autocrafter pattern inventory", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withDirection(BlockState state, Direction direction) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("direction") && property.getPossibleValues().contains(direction)) {
                return state.setValue((Property) property, (Comparable) direction);
            }
        }
        throw new IllegalStateException("No direction property on " + state);
    }

    private static AutocraftingNetworkComponent autocrafting(GameTestHelper helper) {
        LogisticsBridgeBlockEntity bridge = helper.getBlockEntity(BRIDGE);
        Network network = bridge.getNetwork();
        helper.assertTrue(network != null, "Bridge has no network");
        return network.getComponent(AutocraftingNetworkComponent.class);
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

    private static PackagerBlockEntity dispatcherPackager(GameTestHelper helper) {
        return helper.getBlockEntity(DISPATCHER_PACKAGER);
    }

    private static PackagerBlockEntity bridgePackager(GameTestHelper helper) {
        return helper.getBlockEntity(BRIDGE_PACKAGER);
    }

    private static void assertCount(GameTestHelper helper, ItemStack item, int expected) {
        IItemHandler handler = bridgeHandler(helper);
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, item)) {
                total += stack.getCount();
            }
        }
        helper.assertTrue(total == expected, "Expected " + expected + " " + item + " in network, found " + total);
    }
}
