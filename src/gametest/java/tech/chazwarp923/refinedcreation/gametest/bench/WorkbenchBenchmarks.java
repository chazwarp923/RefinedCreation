package tech.chazwarp923.refinedcreation.gametest.bench;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import tech.chazwarp923.refinedcreation.ClientConfig.WorkbenchSort;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchBlockEntity;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchMenu;
import tech.chazwarp923.refinedcreation.workbench.StockView;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.StockPayload;

/**
 * Benchmarks of the Logistics Workbench: its stock sync, its orders, and the client's stock list.
 */
final class WorkbenchBenchmarks {
    private WorkbenchBenchmarks() {
    }

    static void run(LargeNetwork net, Bench bench) {
        GameTestHelper helper = net.helper();
        LogisticsWorkbenchBlockEntity workbench = net.workbench();
        net.nextTick();
        InventorySummary summary = workbench.getNetworkStock(false);
        LogisticsWorkbenchMenu.StockSnapshot sent = LogisticsWorkbenchMenu.snapshotStock(summary);

        // W1: twice a second per open workbench, the menu checks whether the stock changed.
        boolean[] same = new boolean[1];
        bench.measure("W1", "Stock sync check, stock unchanged", 10, 100, null,
                () -> same[0] = LogisticsWorkbenchMenu.isSameStock(summary, sent));
        helper.assertTrue(same[0], "Unchanged stock was reported as changed");
        // The same, after the network summary was rebuilt: the stacks are new objects with the same contents.
        InventorySummary[] rebuilt = new InventorySummary[1];
        bench.measure("W1b", "Stock sync check, summary rebuilt but stock unchanged", 5, 50,
                () -> {
                    net.touchNetwork();
                    net.nextTick();
                    LogisticsManager.SUMMARIES.invalidateAll();
                    rebuilt[0] = workbench.getNetworkStock(false);
                },
                () -> same[0] = LogisticsWorkbenchMenu.isSameStock(rebuilt[0], sent));
        helper.assertTrue(same[0], "Rebuilt but unchanged stock was reported as changed");

        // W2: when it did change, the menu copies the stock and sends it all.
        int[] bytes = new int[1];
        bench.measure("W2", "Stock sync send: copy + encode " + summary.getStacks().size() + " entries", 5, 30,
                null,
                () -> {
                    LogisticsWorkbenchMenu.StockSnapshot snapshot = LogisticsWorkbenchMenu.snapshotStock(summary);
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                            net.level().registryAccess());
                    StockPayload.STREAM_CODEC.encode(buf, new StockPayload(0, true, snapshot.stacks()));
                    bytes[0] = buf.writerIndex();
                    buf.release();
                });
        bench.note("Workbench stock packet size", String.format("%,d bytes", bytes[0]));

        RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.level().registryAccess());
        StockPayload.STREAM_CODEC.encode(encoded, new StockPayload(0, true, sent.stacks()));
        List<BigItemStack> received = new ArrayList<>();
        bench.measure("W2b", "Stock packet decode (client)", 5, 30, null,
                () -> {
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(encoded.copy(),
                            net.level().registryAccess());
                    received.clear();
                    received.addAll(StockPayload.STREAM_CODEC.decode(buf).stock());
                    buf.release();
                });
        helper.assertTrue(received.size() == sent.stacks().size(), "Decoded " + received.size() + " entries");

        // W3: ordering one item from the stock list.
        Component[] status = new Component[1];
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        bench.measure("W3", "Order 64 diamonds from the stock list", 5, 30,
                () -> {
                    LargeNetwork.clearOutgoing(net.bridgePackager());
                    net.nextTick();
                },
                () -> status[0] = workbench.requestItem(diamond, 64));
        assertStatus(helper, status[0], "refinedcreation.workbench.status.ordered");

        // W4: ordering the ingredients of a 9-ingredient recipe for 64 crafts.
        for (int i = 0; i < 9; i++) {
            workbench.getCraftGrid().setItem(i, new ItemStack(LargeNetwork.INGREDIENTS.get(i)));
        }
        bench.measure("W4", "Order ingredients for 64 crafts of a 9-ingredient pattern", 5, 30,
                () -> {
                    LargeNetwork.clearOutgoing(net.bridgePackager());
                    net.nextTick();
                },
                () -> status[0] = workbench.orderPattern(64));
        assertStatus(helper, status[0], "refinedcreation.workbench.status.ordered_crafts");

        // W5: a JEI "+" transfer for 8 crafts where every ingredient comes from the network.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        List<List<ItemStack>> options = new ArrayList<>();
        for (Item ingredient : LargeNetwork.INGREDIENTS) {
            options.add(List.of(new ItemStack(ingredient)));
        }
        bench.measure("W5", "Recipe transfer for 8 crafts, all 9 ingredients from the network", 5, 30,
                () -> {
                    for (int i = 0; i < 9; i++) {
                        workbench.getCraftGrid().setItem(i, ItemStack.EMPTY);
                    }
                    player.getInventory().clearContent();
                    LargeNetwork.clearOutgoing(net.bridgePackager());
                    net.nextTick();
                },
                () -> status[0] = workbench.transferRecipe(player, options, false, 8));
        assertStatus(helper, status[0], "refinedcreation.workbench.status.ordered_crafts");
        LargeNetwork.clearOutgoing(net.bridgePackager());

        // W6: the client's stock list, on every stock update and keystroke.
        StockView view = new StockView();
        List<BigItemStack> stock = sent.stacks();
        List<BigItemStack>[] shown = new List[1];
        // Every update is decoded from a packet, so each one brings new stack objects.
        List<BigItemStack>[] update = new List[1];
        bench.measure("W6a", "Client stock list: stock update (names + sort)", 5, 30,
                () -> {
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(encoded.copy(),
                            net.level().registryAccess());
                    update[0] = StockPayload.STREAM_CODEC.decode(buf).stock();
                    buf.release();
                },
                () -> shown[0] = view.view(update[0], "", WorkbenchSort.NAME, false));
        encoded.release();
        List<BigItemStack> fresh = new StockView().view(update[0], "", WorkbenchSort.NAME, false);
        helper.assertTrue(shown[0].equals(fresh), "An updated stock list differs from a fresh one");
        bench.measure("W6a1", "Client stock list: first stock after opening (names + sort)", 5, 30, null,
                () -> shown[0] = new StockView().view(stock, "", WorkbenchSort.NAME, false));
        helper.assertTrue(shown[0].size() == stock.size(), "Stock list shows " + shown[0].size() + " entries");
        List<BigItemStack> fixed = new ArrayList<>(stock);
        view.view(fixed, "", WorkbenchSort.NAME, false);
        String[] typing = {"b", "ba", "bat", "batc", "batch", "batch ", "batch 1", "batch 12"};
        int[] key = new int[1];
        bench.measure("W6b", "Client stock list: one keystroke in the search box", 16, 160, null,
                () -> shown[0] = view.view(fixed, typing[key[0]++ % typing.length], WorkbenchSort.NAME, false));
        bench.measure("W6c", "Client stock list: change sort", 5, 30, null,
                () -> shown[0] = view.view(fixed, "", key[0]++ % 2 == 0 ? WorkbenchSort.QUANTITY
                        : WorkbenchSort.NAME, false));
    }

    private static void assertStatus(GameTestHelper helper, Component status, String key) {
        helper.assertTrue(status.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key), "Expected " + key + ", got: " + status.getString());
    }
}
