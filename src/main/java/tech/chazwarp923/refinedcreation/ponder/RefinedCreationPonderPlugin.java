package tech.chazwarp923.refinedcreation.ponder;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.registry.ModBlocks;

/**
 * Registers a ponder scene for each of Refined Creation's blocks, and lists them under Create's logistics
 * category.
 */
public class RefinedCreationPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return RefinedCreation.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ModBlocks.LOGISTICS_BRIDGE.getId())
                .addStoryBoard("logistics_bridge", RefinedCreationScenes::logisticsBridge);
        helper.forComponents(ModBlocks.LOGISTICS_DISPATCHER.getId())
                .addStoryBoard("logistics_dispatcher", RefinedCreationScenes::logisticsDispatcher);
        helper.forComponents(ModBlocks.LOGISTICS_WORKBENCH.getId())
                .addStoryBoard("logistics_workbench", RefinedCreationScenes::logisticsWorkbench);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.addToTag(AllCreatePonderTags.HIGH_LOGISTICS)
                .add(ModBlocks.LOGISTICS_BRIDGE.getId())
                .add(ModBlocks.LOGISTICS_DISPATCHER.getId())
                .add(ModBlocks.LOGISTICS_WORKBENCH.getId());
    }
}
