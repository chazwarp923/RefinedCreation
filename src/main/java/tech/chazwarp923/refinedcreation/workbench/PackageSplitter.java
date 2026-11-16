package tech.chazwarp923.refinedcreation.workbench;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraft.world.item.ItemStack;

/**
 * Splits item stacks into Create packages using the same rules as a Packager. A package holds at most
 * {@link PackageItem#SLOTS} stacks, and items that can't go inside container items (shulker boxes, packages)
 * each travel alone.
 */
final class PackageSplitter {
    private PackageSplitter() {
    }

    /**
     * @param stacks the items to pack; they are copied, not modified
     * @return unaddressed packages holding exactly the given items
     */
    static List<ItemStack> pack(List<ItemStack> stacks) {
        List<ItemStack> boxes = new ArrayList<>();
        List<ItemStack> current = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.getItem().canFitInsideContainerItems()) {
                boxes.add(PackageItem.containing(List.of(stack.copy())));
                continue;
            }
            current.add(stack.copy());
            if (current.size() == PackageItem.SLOTS) {
                boxes.add(PackageItem.containing(current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            boxes.add(PackageItem.containing(current));
        }
        return boxes;
    }
}
