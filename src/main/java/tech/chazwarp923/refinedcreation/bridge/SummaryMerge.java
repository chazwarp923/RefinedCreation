package tech.chazwarp923.refinedcreation.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;

import net.minecraft.world.item.Item;
import tech.chazwarp923.refinedcreation.mixin.InventorySummaryAccessor;

/**
 * Faster versions of Create's {@link InventorySummary} merges, used by the mixins into Create.
 * <p>
 * {@link InventorySummary#add(InventorySummary)} compares every entry with every variant of its item already in
 * the target. When the target has no variants of an item yet, which is always the case for the first link on a
 * network, the source's entries can be copied straight across: they are already one per variant.
 */
public final class SummaryMerge {
    private SummaryMerge() {
    }

    /**
     * Does what {@code target.add(source)} does.
     */
    public static void add(InventorySummary target, InventorySummary source) {
        Map<Item, List<BigItemStack>> items = target.getItemMap();
        int total = target.getTotalCount();
        for (Map.Entry<Item, List<BigItemStack>> entry : source.getItemMap().entrySet()) {
            List<BigItemStack> existing = items.get(entry.getKey());
            if (existing != null && !existing.isEmpty()) {
                // Variants to merge with: Create's own add, which also keeps the total.
                setTotalCount(target, total);
                for (BigItemStack stack : entry.getValue()) {
                    target.add(stack.stack, stack.count);
                }
                total = target.getTotalCount();
                continue;
            }
            List<BigItemStack> copies = new ArrayList<>(entry.getValue().size());
            for (BigItemStack stack : entry.getValue()) {
                if (stack.count == 0 || stack.stack.isEmpty()) {
                    continue;
                }
                // Copied, not shared: later merges add to the target's counts.
                copies.add(new BigItemStack(stack.stack, stack.count));
                if (total < BigItemStack.INF) {
                    total += stack.count;
                }
            }
            if (!copies.isEmpty()) {
                items.put(entry.getKey(), copies);
            }
        }
        setTotalCount(target, total);
        target.contributingLinks += source.contributingLinks;
    }

    static void setTotalCount(InventorySummary summary, int total) {
        ((InventorySummaryAccessor) summary).refinedcreation$setTotalCount(total);
    }
}
