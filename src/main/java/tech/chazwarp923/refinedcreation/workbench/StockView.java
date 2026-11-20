package tech.chazwarp923.refinedcreation.workbench;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.core.registries.BuiltInRegistries;
import tech.chazwarp923.refinedcreation.ClientConfig.WorkbenchSort;

/**
 * The Logistics Workbench's stock list as the screen shows it: searched and sorted.
 * <p>
 * Each item's display name and ID are looked up once per stock update rather than on every comparison, and the
 * list is only re-sorted when the stock or the sort changes. Typing in the search box then just filters the
 * sorted list, which keeps its order.
 */
public final class StockView {
    private record Entry(BigItemStack stack, String name, String searchName, String id) {
    }

    private List<BigItemStack> source = List.of();
    private List<Entry> sorted = List.of();
    private boolean hasSource;
    private WorkbenchSort sortedBy;
    private boolean sortedDescending;

    /**
     * @param stock the stock as received from the server; a different list counts as a stock update
     * @return the entries of {@code stock} whose name contains {@code query}, in sort order
     */
    public List<BigItemStack> view(List<BigItemStack> stock, String query, WorkbenchSort sort, boolean descending) {
        if (!hasSource || stock != source) {
            hasSource = true;
            source = stock;
            List<Entry> entries = new ArrayList<>(stock.size());
            for (BigItemStack stack : stock) {
                String name = stack.stack.getHoverName().getString();
                entries.add(new Entry(stack, name, name.toLowerCase(Locale.ROOT),
                        BuiltInRegistries.ITEM.getKey(stack.stack.getItem()).toString()));
            }
            sorted = entries;
            sortedBy = null;
        }
        if (sort != sortedBy || descending != sortedDescending) {
            sorted.sort(comparator(sort, descending));
            sortedBy = sort;
            sortedDescending = descending;
        }

        String needle = query.toLowerCase(Locale.ROOT).strip();
        List<BigItemStack> result = new ArrayList<>(needle.isEmpty() ? sorted.size() : 64);
        for (Entry entry : sorted) {
            if (needle.isEmpty() || entry.searchName().contains(needle)) {
                result.add(entry.stack());
            }
        }
        return result;
    }

    /**
     * The order for the stock list, as in a Refined Storage grid. Ties fall back to the name and then the item
     * ID, so the order is stable from one sync to the next.
     */
    private static Comparator<Entry> comparator(WorkbenchSort sort, boolean descending) {
        Comparator<Entry> byName = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<Entry> byId = Comparator.comparing(Entry::id);
        Comparator<Entry> primary = switch (sort) {
            case QUANTITY -> Comparator.comparingInt((Entry entry) -> entry.stack().count);
            case NAME -> byName;
            case ID -> byId;
        };
        if (descending) {
            primary = primary.reversed();
        }
        return primary.thenComparing(byName).thenComparing(byId);
    }
}
