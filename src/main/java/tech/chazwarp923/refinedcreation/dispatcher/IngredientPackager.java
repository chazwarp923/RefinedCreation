package tech.chazwarp923.refinedcreation.dispatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraft.world.item.ItemStack;

/**
 * Splits a set of RS resources into Create packages.
 * <p>
 * This follows the same rules as a Packager: at most {@link PackageItem#SLOTS} stacks per package, and items that
 * cannot go inside container items (shulker boxes, packages) each travel alone.
 */
final class IngredientPackager {
    private IngredientPackager() {
    }

    /**
     * @param resources item resources only; callers must reject anything else first
     * @return unaddressed packages holding exactly the given resources
     */
    static List<ItemStack> pack(Collection<ResourceAmount> resources) {
        List<ItemStack> boxes = new ArrayList<>();
        List<ItemStack> current = new ArrayList<>();

        for (ResourceAmount resourceAmount : resources) {
            ItemStack prototype = ((ItemResource) resourceAmount.resource()).toItemStack(1);
            int maxStackSize = Math.max(1, prototype.getMaxStackSize());
            boolean bulky = !prototype.getItem().canFitInsideContainerItems();

            long remaining = resourceAmount.amount();
            while (remaining > 0) {
                int count = (int) Math.min(maxStackSize, remaining);
                remaining -= count;
                ItemStack stack = prototype.copyWithCount(count);

                if (bulky) {
                    boxes.add(PackageItem.containing(List.of(stack)));
                    continue;
                }
                current.add(stack);
                if (current.size() == PackageItem.SLOTS) {
                    boxes.add(PackageItem.containing(current));
                    current = new ArrayList<>();
                }
            }
        }
        if (!current.isEmpty()) {
            boxes.add(PackageItem.containing(current));
        }
        return boxes;
    }
}
