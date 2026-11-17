package tech.chazwarp923.refinedcreation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import tech.chazwarp923.refinedcreation.ponder.RefinedCreationPonderPlugin;
import tech.chazwarp923.refinedcreation.registry.ModMenus;
import tech.chazwarp923.refinedcreation.workbench.client.LogisticsWorkbenchScreen;

@Mod(value = RefinedCreation.MODID, dist = Dist.CLIENT)
public class RefinedCreationClient {
    public RefinedCreationClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        PonderIndex.addPlugin(new RefinedCreationPonderPlugin());
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(RefinedCreationClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.LOGISTICS_WORKBENCH.get(), LogisticsWorkbenchScreen::new);
    }
}
