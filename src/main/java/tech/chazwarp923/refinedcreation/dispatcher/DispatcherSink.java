package tech.chazwarp923.refinedcreation.dispatcher;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.autocrafting.PlatformPatternProviderExternalPatternSink;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import tech.chazwarp923.refinedcreation.Config;
import tech.chazwarp923.refinedcreation.registry.ModBlocks;

/**
 * The sink an RS Autocrafter uses when it faces a {@link LogisticsDispatcherBlock}. It turns one pattern iteration's
 * ingredients into packages queued on the dispatcher's Packager.
 * <p>
 * The Autocrafter creates its sink once, so this looks up the dispatcher and Packager on every call. When RS gets
 * {@link ExternalPatternSink.Result#ACCEPTED} from a simulated call, it extracts the ingredients and then executes;
 * anything the execute call does not take is voided. So every check is made in both modes, and the execute call
 * cannot fail once the simulated call has passed in the same tick.
 */
final class DispatcherSink implements PlatformPatternProviderExternalPatternSink {
    private final ServerLevel level;
    private final BlockPos pos;

    DispatcherSink(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Override
    public ExternalPatternSink.Result accept(Collection<ResourceAmount> resources, Action action) {
        if (!isDispatcher()) {
            return ExternalPatternSink.Result.SKIPPED;
        }
        // Packages only carry items. Reject rather than silently void fluids or other resource types.
        for (ResourceAmount resourceAmount : resources) {
            if (!(resourceAmount.resource() instanceof ItemResource)) {
                return ExternalPatternSink.Result.REJECTED;
            }
        }
        PackagerBlockEntity packager = LogisticsDispatcherBlock.findPackager(level, pos);
        if (packager == null) {
            return ExternalPatternSink.Result.REJECTED;
        }
        packager.updateSignAddress();
        String address = packager.signBasedAddress;
        if (address == null || address.isBlank()) {
            return ExternalPatternSink.Result.REJECTED;
        }
        if (packager.queuedExitingPackages.size() >= Config.DISPATCHER_MAX_QUEUED_PACKAGES.getAsInt()) {
            return ExternalPatternSink.Result.REJECTED;
        }
        if (action == Action.SIMULATE) {
            return ExternalPatternSink.Result.ACCEPTED;
        }

        List<ItemStack> boxes = IngredientPackager.pack(resources);
        for (ItemStack box : boxes) {
            PackageItem.addAddress(box, address);
            packager.queuedExitingPackages.add(new BigItemStack(box, 1));
        }
        packager.setChanged();
        return ExternalPatternSink.Result.ACCEPTED;
    }

    /**
     * Claims every resource type while a dispatcher is present, so patterns with fluids reach {@link #accept} and get
     * rejected there instead of being partly handled by other sinks.
     */
    @Override
    public boolean applies(ResourceKey resource) {
        return isDispatcher();
    }

    /**
     * Used by the Autocrafter's "lock until connected machine is empty" mode. The dispatcher counts as empty once its
     * Packager has sent out everything it was given.
     */
    @Override
    public boolean isEmpty() {
        PackagerBlockEntity packager = findPackagerIfDispatcher();
        return packager == null || (packager.queuedExitingPackages.isEmpty() && packager.heldBox.isEmpty());
    }

    @Nullable
    private PackagerBlockEntity findPackagerIfDispatcher() {
        return isDispatcher() ? LogisticsDispatcherBlock.findPackager(level, pos) : null;
    }

    private boolean isDispatcher() {
        return level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.LOGISTICS_DISPATCHER.get());
    }
}
