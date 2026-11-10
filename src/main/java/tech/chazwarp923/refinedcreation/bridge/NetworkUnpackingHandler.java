package tech.chazwarp923.refinedcreation.bridge;

import java.util.ArrayList;
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
 * Unpacks Create packages arriving at a Packager that faces a Logistics Bridge directly into the RS network.
 * <p>
 * Create's default handler probes slot-by-slot, which doesn't map well onto a virtual network inventory. This
 * handler instead asks the network whether the whole package fits and only accepts it if it does, so packages are
 * never partially unpacked.
 */
public enum NetworkUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side, List<ItemStack> items,
                          @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        if (!(level.getBlockEntity(pos) instanceof LogisticsBridgeBlockEntity bridge)) {
            return false;
        }
        NetworkItemHandler handler = bridge.getItemHandler();

        List<ItemStack> merged = merge(items);
        for (ItemStack stack : merged) {
            if (handler.insertDirect(stack, true) < stack.getCount()) {
                return false;
            }
        }
        if (simulate) {
            return true;
        }
        for (ItemStack stack : merged) {
            handler.insertDirect(stack, false);
        }
        return true;
    }

    /**
     * Combines identical stacks so the "does it fit" check accounts for the full amount of each item.
     */
    private static List<ItemStack> merge(List<ItemStack> items) {
        List<ItemStack> merged = new ArrayList<>();
        outer:
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(stack.getCount());
                    continue outer;
                }
            }
            merged.add(stack.copy());
        }
        return merged;
    }
}
