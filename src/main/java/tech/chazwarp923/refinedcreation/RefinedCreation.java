package tech.chazwarp923.refinedcreation;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import tech.chazwarp923.refinedcreation.bridge.NetworkUnpackingHandler;
import tech.chazwarp923.refinedcreation.dispatcher.DispatcherSinkFactory;
import tech.chazwarp923.refinedcreation.registry.ModBlockEntities;
import tech.chazwarp923.refinedcreation.registry.ModBlocks;

/**
 * Refined Creation bridges a Refined Storage 2 network into Create 6's logistics system.
 * <p>
 * The {@link ModBlocks#LOGISTICS_BRIDGE Logistics Bridge} joins an RS network like any other network device and
 * presents the network's entire item storage as an inventory. Put a Create Packager against it and attach a
 * Stock Link to the Packager: every item in the RS network then shows up in Stock Keepers and Factory Gauges on
 * that logistics frequency, requested items are pulled out of the network and packaged, and packages delivered to
 * that Packager are unpacked straight into the network.
 * <p>
 * The {@link ModBlocks#LOGISTICS_DISPATCHER Logistics Dispatcher} goes the other way for autocrafting: an RS
 * Autocrafter facing it ships its external pattern's ingredients as a package to a Create factory, and the
 * factory's result package returns through a bridge to complete the RS task.
 */
@Mod(RefinedCreation.MODID)
public class RefinedCreation {
    public static final String MODID = "refinedcreation";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RefinedCreation(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::addCreative);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Create's default unpacker assumes a real slotted inventory; the bridge inserts into the network directly.
        event.enqueueWork(() -> {
            UnpackingHandler.REGISTRY.register(ModBlocks.LOGISTICS_BRIDGE.get(), NetworkUnpackingHandler.INSTANCE);
            // Autocrafters facing a Logistics Dispatcher send their ingredients out as Create packages.
            RefinedStorageApi.INSTANCE.addPatternProviderExternalPatternSinkFactory(DispatcherSinkFactory.INSTANCE);
        });
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Lets RS cables/devices discover the bridge as a network node.
        event.registerBlockEntity(
                RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
                ModBlockEntities.LOGISTICS_BRIDGE.get(),
                (be, side) -> be.getContainerProvider());

        // Lets Create packagers (and anything else using item handlers) see the network's items.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LOGISTICS_BRIDGE.get(),
                (be, side) -> be.getItemHandler());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<?> rsTab = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                RefinedStorageApi.INSTANCE.getCreativeModeTabId());
        if (event.getTabKey().equals(rsTab)) {
            event.accept(ModBlocks.LOGISTICS_BRIDGE_ITEM);
            event.accept(ModBlocks.LOGISTICS_DISPATCHER_ITEM);
        }
    }
}
