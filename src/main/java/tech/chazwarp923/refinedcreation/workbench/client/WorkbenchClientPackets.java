package tech.chazwarp923.refinedcreation.workbench.client;

import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchMenu;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.StatusPayload;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.StockCountsPayload;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.StockPayload;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.StockRequestPayload;

/**
 * Client-side handlers for the Logistics Workbench payloads. This is kept apart from {@code WorkbenchPackets}
 * so dedicated servers never load it.
 */
public final class WorkbenchClientPackets {
    /**
     * The last stock list received, and the workbench it came from. Reopening that workbench shows it straight
     * away while the server sends a fresh one, as Create's Stock Keeper does.
     */
    @Nullable
    private static GlobalPos lastStockSource;
    private static List<BigItemStack> lastStock = List.of();
    private static boolean lastTuned;
    /** The menu the server last sent a whole stock list to, which count updates must be for. */
    private static int lastStockContainer = -1;

    private WorkbenchClientPackets() {
    }

    public static void handleStock(StockPayload payload) {
        LogisticsWorkbenchMenu menu = openMenu(payload.containerId());
        if (menu != null) {
            menu.receiveStock(payload.tuned(), payload.stock());
            lastStockSource = source(menu);
            lastStock = payload.stock();
            lastTuned = payload.tuned();
            lastStockContainer = payload.containerId();
        }
    }

    /**
     * Applies new counts to the stock this menu was last sent. The stacks stay the same objects, which the
     * screen's stock list recognises cheaply. If the counts don't fit, asks for the whole stock again.
     */
    public static void handleStockCounts(StockCountsPayload payload) {
        LogisticsWorkbenchMenu menu = openMenu(payload.containerId());
        if (menu == null) {
            return;
        }
        List<BigItemStack> updated = lastStockContainer == payload.containerId()
                ? LogisticsWorkbenchMenu.applyCounts(menu.getStock(), payload.counts())
                : null;
        if (updated == null) {
            PacketDistributor.sendToServer(new StockRequestPayload(menu.containerId));
            return;
        }
        menu.receiveStock(menu.isTuned(), updated);
        lastStock = updated;
    }

    /**
     * Called when the screen opens: shows the remembered stock if it's from this workbench, and asks the server
     * for the current stock.
     */
    public static void onScreenOpened(LogisticsWorkbenchMenu menu) {
        GlobalPos source = source(menu);
        if (menu.getStockVersion() == 0 && source != null && source.equals(lastStockSource)) {
            menu.receiveStock(lastTuned, lastStock);
        }
        PacketDistributor.sendToServer(new StockRequestPayload(menu.containerId));
    }

    @Nullable
    private static GlobalPos source(LogisticsWorkbenchMenu menu) {
        Player player = Minecraft.getInstance().player;
        return player == null ? null : GlobalPos.of(player.level().dimension(), menu.getPos());
    }

    public static void handleStatus(StatusPayload payload) {
        LogisticsWorkbenchMenu menu = openMenu(payload.containerId());
        if (menu != null) {
            menu.receiveStatus(payload.message());
        }
    }

    private static LogisticsWorkbenchMenu openMenu(int containerId) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof LogisticsWorkbenchMenu menu
                && menu.containerId == containerId) {
            return menu;
        }
        return null;
    }
}
