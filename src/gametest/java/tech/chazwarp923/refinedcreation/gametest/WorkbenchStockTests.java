package tech.chazwarp923.refinedcreation.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tech.chazwarp923.refinedcreation.ClientConfig.WorkbenchSort;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchMenu;
import tech.chazwarp923.refinedcreation.workbench.StockView;

/**
 * The workbench's stock list: searching and sorting a large stock, and skipping stock syncs that change nothing.
 */
@GameTestHolder(RefinedCreation.MODID)
@PrefixGameTestTemplate(false)
public class WorkbenchStockTests {
    /**
     * Searches and sorts a stock of every item in the game, as the screen does while someone types, and logs how
     * long each step took.
     */
    @GameTest(template = "empty_6x3x3")
    public static void stockViewSearchesAndSortsLargeStock(GameTestHelper helper) {
        List<BigItemStack> stock = everyItem();
        StockView view = new StockView();

        long start = System.nanoTime();
        List<BigItemStack> all = view.view(stock, "", WorkbenchSort.NAME, false);
        long firstMicros = (System.nanoTime() - start) / 1000;
        helper.assertTrue(all.size() == stock.size(), "An empty search should show everything");
        for (int i = 1; i < all.size(); i++) {
            helper.assertTrue(String.CASE_INSENSITIVE_ORDER.compare(name(all.get(i - 1)), name(all.get(i))) <= 0,
                    "Not sorted by name at " + i);
        }

        long typingStart = System.nanoTime();
        for (String query : new String[] {"i", "ir", "iro", "iron"}) {
            for (BigItemStack entry : view.view(stock, query, WorkbenchSort.NAME, false)) {
                helper.assertTrue(name(entry).toLowerCase(Locale.ROOT).contains(query),
                        name(entry) + " does not match '" + query + "'");
            }
        }
        long typingMicros = (System.nanoTime() - typingStart) / 1000;

        List<BigItemStack> byCount = view.view(stock, "", WorkbenchSort.QUANTITY, true);
        for (int i = 1; i < byCount.size(); i++) {
            helper.assertTrue(byCount.get(i - 1).count >= byCount.get(i).count, "Not sorted by count at " + i);
        }

        // A stock sync delivers a new list, so the names are looked up again.
        start = System.nanoTime();
        view.view(new ArrayList<>(stock), "", WorkbenchSort.NAME, false);
        long resyncMicros = (System.nanoTime() - start) / 1000;

        RefinedCreation.LOGGER.info("Stock view of {} items: first {} us, typing 4 characters {} us, "
                + "stock update {} us", stock.size(), firstMicros, typingMicros, resyncMicros);
        helper.succeed();
    }

    /**
     * A periodic stock sync is skipped only if every item and count matches what was last sent.
     */
    @GameTest(template = "empty_6x3x3")
    public static void unchangedStockIsNotResent(GameTestHelper helper) {
        LogisticsWorkbenchMenu.StockSnapshot sent = LogisticsWorkbenchMenu.snapshotStock(
                summary(new ItemStack(Items.DIAMOND), 10, new ItemStack(Items.IRON_INGOT), 5));

        helper.assertFalse(LogisticsWorkbenchMenu.isSameStock(
                summary(new ItemStack(Items.DIAMOND), 10, new ItemStack(Items.IRON_INGOT), 5), null),
                "Nothing sent yet");
        helper.assertTrue(LogisticsWorkbenchMenu.isSameStock(
                summary(new ItemStack(Items.DIAMOND), 10, new ItemStack(Items.IRON_INGOT), 5), sent),
                "Same items and counts in the same order");
        helper.assertTrue(LogisticsWorkbenchMenu.isSameStock(
                summary(new ItemStack(Items.IRON_INGOT), 5, new ItemStack(Items.DIAMOND), 10), sent),
                "Same items and counts in another order");
        helper.assertFalse(LogisticsWorkbenchMenu.isSameStock(
                summary(new ItemStack(Items.DIAMOND), 11, new ItemStack(Items.IRON_INGOT), 5), sent),
                "A count changed");
        helper.assertFalse(LogisticsWorkbenchMenu.isSameStock(
                summary(new ItemStack(Items.DIAMOND), 10, new ItemStack(Items.GOLD_INGOT), 5), sent),
                "An item was swapped");
        helper.assertFalse(LogisticsWorkbenchMenu.isSameStock(
                summary(new ItemStack(Items.DIAMOND), 10, ItemStack.EMPTY, 0), sent),
                "An item ran out");
        helper.succeed();
    }

    /**
     * The view reuses the previous update's names and order, so after every kind of update it must show exactly
     * what a fresh view of the same stock shows: counts that change the order, items that run out or arrive, and
     * a change of sort.
     */
    @GameTest(template = "empty_6x3x3")
    public static void stockViewUpdatesMatchFreshView(GameTestHelper helper) {
        StockView view = new StockView();
        List<BigItemStack> stock = everyItem();
        assertSameView(helper, view, stock, WorkbenchSort.QUANTITY, true, "first stock");

        // Counts shift, a few items run out, some new named variants arrive, all as new stack objects.
        List<BigItemStack> update = new ArrayList<>();
        for (int i = 0; i < stock.size(); i++) {
            if (i % 50 != 7) {
                BigItemStack entry = stock.get(i);
                update.add(new BigItemStack(entry.stack.copy(), (entry.count * 7 + i) % 6000 + 1));
            }
        }
        for (int i = 0; i < 40; i++) {
            ItemStack named = new ItemStack(Items.PAPER);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Note " + i));
            update.add(i * 31 % update.size(), new BigItemStack(named, 1 + i));
        }
        assertSameView(helper, view, update, WorkbenchSort.QUANTITY, true, "updated stock");
        assertSameView(helper, view, update, WorkbenchSort.NAME, false, "updated stock by name");
        assertSameView(helper, view, new ArrayList<>(stock), WorkbenchSort.ID, false, "original stock by ID");
        helper.succeed();
    }

    private static void assertSameView(GameTestHelper helper, StockView view, List<BigItemStack> stock,
                                       WorkbenchSort sort, boolean descending, String what) {
        List<BigItemStack> shown = view.view(stock, "", sort, descending);
        List<BigItemStack> fresh = new StockView().view(stock, "", sort, descending);
        helper.assertTrue(shown.size() == fresh.size(), what + ": shows " + shown.size() + " of " + fresh.size());
        for (int i = 0; i < shown.size(); i++) {
            helper.assertTrue(shown.get(i) == fresh.get(i), what + ": entry " + i + " is " + shown.get(i).stack
                    + " x" + shown.get(i).count + ", a fresh view has " + fresh.get(i).stack + " x"
                    + fresh.get(i).count);
        }
    }

    private static InventorySummary summary(ItemStack first, int firstCount, ItemStack second, int secondCount) {
        InventorySummary summary = new InventorySummary();
        summary.add(first, firstCount);
        summary.add(second, secondCount);
        return summary;
    }

    private static List<BigItemStack> everyItem() {
        List<BigItemStack> stock = new ArrayList<>();
        int n = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                stock.add(new BigItemStack(new ItemStack(item), 1 + (n++ * 37) % 5000));
            }
        }
        return stock;
    }

    private static String name(BigItemStack entry) {
        return entry.stack.getHoverName().getString();
    }
}
