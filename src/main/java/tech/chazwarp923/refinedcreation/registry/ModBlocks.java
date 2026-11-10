package tech.chazwarp923.refinedcreation.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlock;

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

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
