package tech.chazwarp923.refinedcreation.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import tech.chazwarp923.refinedcreation.bridge.NetworkItemHandler;

/**
 * Funnels, chutes and other filtered extractors walk every slot of an inventory, simulating an extraction from each
 * before testing it against their filter. On a Logistics Bridge each simulation goes through the whole RS storage,
 * so this tests the filter first, on the stack the simulation would return, and skips slots it rejects.
 */
@Mixin(value = ItemHelper.class, remap = false)
public abstract class ItemHelperMixin {
    @WrapOperation(method = "extract(Lnet/neoforged/neoforge/items/IItemHandler;Ljava/util/function/Predicate;Lcom/simibubi/create/foundation/item/ItemHelper$ExtractionCountMode;IZ)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/items/IItemHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack refinedcreation$filterBeforeSimulating(IItemHandler inv, int slot, int amount,
                                                                    boolean simulate, Operation<ItemStack> original,
                                                                    @Local(argsOnly = true) Predicate<ItemStack> test) {
        if (simulate && inv instanceof NetworkItemHandler) {
            ItemStack inSlot = inv.getStackInSlot(slot);
            if (inSlot.isEmpty() || !test.test(inSlot.copyWithCount(Math.min(amount, inSlot.getCount())))) {
                return ItemStack.EMPTY;
            }
        }
        return original.call(inv, slot, amount, simulate);
    }
}
