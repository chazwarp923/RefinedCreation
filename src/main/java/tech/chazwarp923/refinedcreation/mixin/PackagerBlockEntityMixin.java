package tech.chazwarp923.refinedcreation.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;

import tech.chazwarp923.refinedcreation.bridge.BridgeCrafting;

/**
 * Holds logistics orders sent to a Packager on a Logistics Bridge while RS autocrafts the missing items.
 */
@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class PackagerBlockEntityMixin {
    @Inject(method = "attemptToSend", at = @At("HEAD"), cancellable = true)
    private void refinedcreation$holdForAutocrafting(List<PackagingRequest> queuedRequests, CallbackInfo ci) {
        if (queuedRequests == null || queuedRequests.isEmpty()) {
            return;
        }
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        BridgeCrafting crafting = BridgeCrafting.forPackager(self);
        if (crafting != null && crafting.holdIfCrafting(self, queuedRequests)) {
            // The bridge kept its own copy. Emptying the caller's list ends LogisticsManager's packaging loop.
            queuedRequests.clear();
            ci.cancel();
        }
    }
}
