package tech.chazwarp923.refinedcreation.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;

import net.neoforged.neoforge.items.IItemHandler;
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

    /**
     * Builds the Packager's stock summary of a Logistics Bridge in one pass. Create's own loop adds each slot by
     * comparing it with every variant of its item already listed; the bridge's slots are all different, so this
     * skips that, then reports no slots left so Create's loop does nothing.
     */
    @WrapOperation(method = "getAvailableItems", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/items/IItemHandler;getSlots()I"))
    private int refinedcreation$summarizeBridge(IItemHandler inventory, Operation<Integer> original,
                                                @Local InventorySummary availableItems) {
        if (inventory instanceof NetworkItemHandler handler) {
            if (availableItems.isEmpty()) {
                handler.summarizeInto(availableItems);
            }
            return 0;
        }
        return original.call(inventory);
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
