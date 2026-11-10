package tech.chazwarp923.refinedcreation.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlockEntity;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RefinedCreation.MODID);

    @SuppressWarnings("DataFlowIssue") // the data fixer type may be null
    public static final Supplier<BlockEntityType<LogisticsBridgeBlockEntity>> LOGISTICS_BRIDGE =
            BLOCK_ENTITIES.register("logistics_bridge", () -> BlockEntityType.Builder
                    .of(LogisticsBridgeBlockEntity::new, ModBlocks.LOGISTICS_BRIDGE.get())
                    .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
