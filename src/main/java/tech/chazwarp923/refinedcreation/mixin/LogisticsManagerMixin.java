package tech.chazwarp923.refinedcreation.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;

import tech.chazwarp923.refinedcreation.bridge.SummaryMerge;

/**
 * Merges each link's summary into the network's without comparing every entry to every variant of its item, which
 * gets slow when one link (a Logistics Bridge) brings thousands of item types.
 */
@Mixin(value = LogisticsManager.class, remap = false)
public abstract class LogisticsManagerMixin {
    @WrapOperation(method = "createSummaryOfNetwork", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/InventorySummary;add(Lcom/simibubi/create/content/logistics/packager/InventorySummary;)V"))
    private static void refinedcreation$mergeFaster(InventorySummary target, InventorySummary source,
                                                    Operation<Void> original) {
        SummaryMerge.add(target, source);
    }
}
