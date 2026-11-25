package tech.chazwarp923.refinedcreation.workbench;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import tech.chazwarp923.refinedcreation.ClientConfig.WorkbenchSort;

/**
 * The Logistics Workbench's stock list as the screen shows it: searched and sorted.
 * <p>
 * Each item's display name and ID are looked up once, when it first appears in the stock, rather than on every
 * comparison, and the list is only re-sorted when the stock or the sort changes. Typing in the search box then just
 * filters the sorted list, which keeps its order.
 * <p>
 * The server lists the stock in the same order on every update, so each new entry is matched against the next few
 * entries of the previous update. A match reuses that entry's names and starts from its previous place in the sorted
 * list, which leaves the sort little to do.
 */
public final class StockView {
    /** How far ahead in the previous update to look for an entry, past items that have since run out. */
    private static final int LOOKAHEAD = 8;

    /**
     * @param index where the stack is in the stock list it came from
     */
    private record Entry(BigItemStack stack, String name, String searchName, String id, int index) {
    }

    private List<BigItemStack> source = List.of();
    /** The entries of {@link #source}, by index in it. */
    private Entry[] bySource = new Entry[0];
    /** Where each entry of {@link #source} is in {@link #sorted}, by index in {@link #source}. */
    private int[] sortedPosition = new int[0];
    private List<Entry> sorted = List.of();
    private boolean hasSource;
    private WorkbenchSort sortedBy;
    private boolean sortedDescending;

    /**
     * @param stock the stock as received from the server; a different list counts as a stock update
     * @return the entries of {@code stock} whose name contains {@code query}, in sort order
     */
    public List<BigItemStack> view(List<BigItemStack> stock, String query, WorkbenchSort sort, boolean descending) {
        boolean newStock = !hasSource || stock != source;
        if (newStock) {
            sorted = entriesFor(stock);
            hasSource = true;
            source = stock;
        }
        if (newStock || sort != sortedBy || descending != sortedDescending) {
            sorted.sort(comparator(sort, descending));
            sortedBy = sort;
            sortedDescending = descending;
            bySource = new Entry[source.size()];
            sortedPosition = new int[source.size()];
            for (int i = 0; i < sorted.size(); i++) {
                Entry entry = sorted.get(i);
                bySource[entry.index()] = entry;
                sortedPosition[entry.index()] = i;
            }
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
     * @return an entry per stack: those matched to the previous update first, in their previous sorted order, then
     * the new ones
     */
    private List<Entry> entriesFor(List<BigItemStack> stock) {
        Entry[] placed = new Entry[sorted.size()];
        List<Entry> added = new ArrayList<>();
        int next = 0;
        for (int i = 0; i < stock.size(); i++) {
            BigItemStack stack = stock.get(i);
            int match = -1;
            for (int j = next; j < Math.min(source.size(), next + LOOKAHEAD); j++) {
                if (ItemStack.isSameItemSameComponents(stack.stack, source.get(j).stack)) {
                    match = j;
                    break;
                }
            }
            if (match >= 0) {
                next = match + 1;
                Entry previous = bySource[match];
                placed[sortedPosition[match]] = new Entry(stack, previous.name(), previous.searchName(),
                        previous.id(), i);
                continue;
            }
            String name = stack.stack.getHoverName().getString();
            added.add(new Entry(stack, name, name.toLowerCase(Locale.ROOT),
                    BuiltInRegistries.ITEM.getKey(stack.stack.getItem()).toString(), i));
        }
        List<Entry> entries = new ArrayList<>(stock.size());
        for (Entry entry : placed) {
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.addAll(added);
        return entries;
    }

    /**
     * The order for the stock list, as in a Refined Storage grid. Ties fall back to the name and then the item
     * ID, so the order is stable from one sync to the next. Names compare by their lowercase form, computed once
     * per item, which orders the same as a case-insensitive comparison but costs far less per comparison.
     */
    private static Comparator<Entry> comparator(WorkbenchSort sort, boolean descending) {
        Comparator<Entry> primary = switch (sort) {
            case QUANTITY -> (a, b) -> Integer.compare(a.stack().count, b.stack().count);
            case NAME -> StockView::compareNames;
            case ID -> (a, b) -> a.id().compareTo(b.id());
        };
        Comparator<Entry> ordered = descending ? primary.reversed() : primary;
        return (a, b) -> {
            int result = ordered.compare(a, b);
            if (result == 0) {
                result = compareNames(a, b);
            }
            return result != 0 ? result : a.id().compareTo(b.id());
        };
    }

    private static int compareNames(Entry a, Entry b) {
        int result = a.searchName().compareTo(b.searchName());
        return result != 0 ? result : a.name().compareTo(b.name());
    }
}
