package tech.chazwarp923.refinedcreation.workbench;

import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Unpacks packages arriving at a Packager that faces a Logistics Workbench into the workbench's buffer. A
 * package is accepted only if all of it fits.
 * <p>
 * The workbench deliberately has no item capability. A Stock Link on that Packager would otherwise advertise
 * the buffer as network stock, and the network could pull ingredients back out mid-craft.
 */
public enum WorkbenchUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side, List<ItemStack> items,
                          @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        if (!(level.getBlockEntity(pos) instanceof LogisticsWorkbenchBlockEntity workbench)) {
            return false;
        }
        return workbench.insertAllIntoBuffer(items, simulate);
    }
}
