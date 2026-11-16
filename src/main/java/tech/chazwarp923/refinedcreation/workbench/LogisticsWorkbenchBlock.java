package tech.chazwarp923.refinedcreation.workbench;

import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import tech.chazwarp923.refinedcreation.registry.ModBlockEntities;

/**
 * The Logistics Workbench block. See {@link LogisticsWorkbenchBlockEntity} for what it does.
 * <p>
 * Tune it like a Stock Keeper: right-click a Stock Link with the item, then place it. A placed workbench can
 * be retuned by right-clicking it with any tuned Create logistics item.
 * <p>
 * It implements {@link IBE} because Create's {@code LogisticallyLinkedBlockItem.assignFrequency} casts the
 * block to it to find the block entity type.
 */
public class LogisticsWorkbenchBlock extends Block implements IBE<LogisticsWorkbenchBlockEntity> {
    public LogisticsWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<LogisticsWorkbenchBlockEntity> getBlockEntityClass() {
        return LogisticsWorkbenchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LogisticsWorkbenchBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LOGISTICS_WORKBENCH.get();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.LOGISTICS_WORKBENCH.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((LogisticsWorkbenchBlockEntity) be).serverTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof LogisticallyLinkedBlockItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        UUID frequency = LogisticallyLinkedBlockItem.networkFromStack(stack);
        if (frequency == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            withBlockEntityDo(level, pos, be -> be.setFrequency(frequency));
            player.displayClientMessage(Component.translatable("refinedcreation.workbench.tuned"), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        LogisticsWorkbenchBlockEntity be = getBlockEntity(level, pos);
        if (be != null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(be, be::writeMenuData);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LogisticsWorkbenchBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
