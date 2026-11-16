package tech.chazwarp923.refinedcreation.registry;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlock;
import tech.chazwarp923.refinedcreation.dispatcher.LogisticsDispatcherBlock;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchBlock;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RefinedCreation.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RefinedCreation.MODID);

    public static final DeferredBlock<LogisticsBridgeBlock> LOGISTICS_BRIDGE = BLOCKS.registerBlock(
            "logistics_bridge",
            LogisticsBridgeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.9F, 1.9F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> LOGISTICS_BRIDGE_ITEM =
            ITEMS.registerSimpleBlockItem("logistics_bridge", LOGISTICS_BRIDGE);

    public static final DeferredBlock<LogisticsDispatcherBlock> LOGISTICS_DISPATCHER = BLOCKS.registerBlock(
            "logistics_dispatcher",
            LogisticsDispatcherBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.9F, 1.9F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> LOGISTICS_DISPATCHER_ITEM =
            ITEMS.registerSimpleBlockItem("logistics_dispatcher", LOGISTICS_DISPATCHER);

    public static final DeferredBlock<LogisticsWorkbenchBlock> LOGISTICS_WORKBENCH = BLOCKS.registerBlock(
            "logistics_workbench",
            LogisticsWorkbenchBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.9F, 1.9F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** Create's linked item, so the workbench is tuned by right-clicking a Stock Link, like a Stock Keeper. */
    public static final DeferredItem<LogisticallyLinkedBlockItem> LOGISTICS_WORKBENCH_ITEM = ITEMS.register(
            "logistics_workbench",
            () -> new LogisticallyLinkedBlockItem(LOGISTICS_WORKBENCH.get(), new Item.Properties()));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
