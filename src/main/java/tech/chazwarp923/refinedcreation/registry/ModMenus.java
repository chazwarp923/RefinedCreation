package tech.chazwarp923.refinedcreation.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchMenu;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, RefinedCreation.MODID);

    public static final Supplier<MenuType<LogisticsWorkbenchMenu>> LOGISTICS_WORKBENCH =
            MENUS.register("logistics_workbench", () -> IMenuTypeExtension.create(LogisticsWorkbenchMenu::fromNetwork));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
