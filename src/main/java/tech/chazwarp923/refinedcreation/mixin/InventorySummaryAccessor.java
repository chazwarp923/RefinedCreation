package tech.chazwarp923.refinedcreation.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.logistics.packager.InventorySummary;

/**
 * Lets {@code SummaryMerge} keep a summary's total count right when it fills the summary directly.
 */
@Mixin(value = InventorySummary.class, remap = false)
public interface InventorySummaryAccessor {
    @Accessor("totalCount")
    void refinedcreation$setTotalCount(int totalCount);
}
