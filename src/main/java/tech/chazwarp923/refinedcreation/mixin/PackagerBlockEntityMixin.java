package tech.chazwarp923.refinedcreation.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;

import tech.chazwarp923.refinedcreation.bridge.BridgeCrafting;
import tech.chazwarp923.refinedcreation.bridge.NetworkItemHandler;

/**
 * Hooks a Packager's packaging into a Logistics Bridge: orders are held while RS autocrafts missing items, and the
 * bridge only offers the ordered items while an order is packaged.
 */
@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class PackagerBlockEntityMixin {
    @WrapMethod(method = "attemptToSend")
    private void refinedcreation$limitBridgeToOrder(List<PackagingRequest> queuedRequests, Operation<Void> original) {
        NetworkItemHandler handler = queuedRequests == null || queuedRequests.isEmpty()
                ? null
                : NetworkItemHandler.forPackager((PackagerBlockEntity) (Object) this);
        if (handler == null) {
            original.call(queuedRequests);
        } else {
            handler.packageOrder(queuedRequests, () -> original.call(queuedRequests));
        }
    }

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
