package tech.chazwarp923.refinedcreation.dispatcher;

import javax.annotation.Nullable;

import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Logistics Dispatcher: the target block for a Refined Storage Autocrafter running external patterns.
 * <p>
 * The Autocrafter faces the dispatcher, and a Create Packager sits against it with its back to the dispatcher.
 * When RS runs a pattern, {@link DispatcherSink} packs the pattern's ingredients and has that Packager send the
 * package(s) to the address on the Packager's sign. The dispatcher has no inventory on purpose: if it had one, RS's
 * built-in item-handler sink would push the ingredients into it as well.
 */
public class LogisticsDispatcherBlock extends Block {
    public LogisticsDispatcherBlock(Properties properties) {
        super(properties);
    }

    /**
     * @return the Packager whose back (its target side) touches the dispatcher at {@code pos}, or {@code null}
     */
    @Nullable
    public static PackagerBlockEntity findPackager(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.isLoaded(neighbor)) {
                continue;
            }
            BlockState state = level.getBlockState(neighbor);
            // A packager targets the block opposite its facing, so it must face away from the dispatcher.
            if (state.getOptionalValue(PackagerBlock.FACING).orElse(null) != direction) {
                continue;
            }
            if (level.getBlockEntity(neighbor) instanceof PackagerBlockEntity packager) {
                return packager;
            }
        }
        return null;
    }
}
