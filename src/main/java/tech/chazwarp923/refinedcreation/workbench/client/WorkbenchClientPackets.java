package tech.chazwarp923.refinedcreation.workbench.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchMenu;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.StatusPayload;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.StockPayload;

/**
 * Client-side handlers for the Logistics Workbench payloads. This is kept apart from {@code WorkbenchPackets}
 * so dedicated servers never load it.
 */
public final class WorkbenchClientPackets {
    private WorkbenchClientPackets() {
    }

    public static void handleStock(StockPayload payload) {
        LogisticsWorkbenchMenu menu = openMenu(payload.containerId());
        if (menu != null) {
            menu.receiveStock(payload.tuned(), payload.stock());
        }
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
