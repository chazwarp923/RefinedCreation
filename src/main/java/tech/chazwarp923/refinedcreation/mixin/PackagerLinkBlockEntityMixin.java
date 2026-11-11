package tech.chazwarp923.refinedcreation.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;

import tech.chazwarp923.refinedcreation.bridge.BridgeCrafting;

/**
 * Makes a Stock Link on a Logistics Bridge's Packager report RS-craftable items as infinitely available, so Stock
 * Keepers list them and orders for them are routed to the bridge.
 * <p>
 * This targets the Stock Link instead of {@link PackagerBlockEntity#getAvailableItems()}, because restock gauges on
 * the Packager read that summary directly and must keep seeing real counts.
 */
@Mixin(value = PackagerLinkBlockEntity.class, remap = false)
public abstract class PackagerLinkBlockEntityMixin {
    @Inject(method = "fetchSummaryFromPackager", at = @At("RETURN"), cancellable = true)
    private void refinedcreation$addCraftables(@Nullable IdentifiedInventory ignoredHandler,
                                               CallbackInfoReturnable<InventorySummary> cir) {
        InventorySummary summary = cir.getReturnValue();
        if (summary == InventorySummary.EMPTY) {
            return;
        }
        BridgeCrafting crafting = BridgeCrafting.forPackager(((PackagerLinkBlockEntity) (Object) this).getPackager());
        if (crafting != null) {
            cir.setReturnValue(crafting.withCraftables(summary));
        }
    }

    @Redirect(method = "processRequest", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;getAvailableItems()"
                    + "Lcom/simibubi/create/content/logistics/packager/InventorySummary;"))
    private InventorySummary refinedcreation$routeCraftables(PackagerBlockEntity packager) {
        InventorySummary summary = packager.getAvailableItems();
        BridgeCrafting crafting = BridgeCrafting.forPackager(packager);
        return crafting == null ? summary : crafting.withCraftables(summary);
    }
}
