package tech.chazwarp923.refinedcreation.compat.jei;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.registry.ModBlocks;
import tech.chazwarp923.refinedcreation.workbench.client.LogisticsWorkbenchScreen;

/**
 * JEI integration. JEI finds this class through {@link JeiPlugin}, and it's only loaded when JEI is installed,
 * so JEI stays an optional dependency.
 * <p>
 * For the Logistics Workbench it registers:
 * <ul>
 * <li>a recipe transfer handler for crafting recipes, see {@link WorkbenchRecipeTransferHandler};</li>
 * <li>the workbench as a crafting catalyst;</li>
 * <li>the parts of the GUI outside the screen's normal bounds (the header, the stock list and the buffer
 * panel) as extra areas, so JEI's overlays don't draw over them.</li>
 * </ul>
 * It also hands JEI's runtime to {@link JeiSearchSync}, for syncing the workbench's search with JEI's.
 */
@JeiPlugin
public class RefinedCreationJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(RefinedCreation.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiSearchSync.setRuntime(runtime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiSearchSync.setRuntime(null);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new WorkbenchRecipeTransferHandler(registration.getTransferHelper()), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Lists the workbench on JEI's crafting recipe pages, next to the crafting table.
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.LOGISTICS_WORKBENCH_ITEM.get()), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(LogisticsWorkbenchScreen.class,
                new IGuiContainerHandler<LogisticsWorkbenchScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(LogisticsWorkbenchScreen screen) {
                        return screen.getExtraAreas();
                    }
                });
    }
}
