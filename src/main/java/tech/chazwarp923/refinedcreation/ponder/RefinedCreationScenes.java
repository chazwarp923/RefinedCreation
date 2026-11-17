package tech.chazwarp923.refinedcreation.ponder;

import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlock;

/**
 * Ponder scenes for Refined Creation's blocks. The structures are in {@code assets/refinedcreation/ponder}, and
 * the text is in the lang file under {@code refinedcreation.ponder.<scene>}: keep the two in step, as Ponder
 * numbers each scene's text in the order it's shown.
 */
public final class RefinedCreationScenes {
    private RefinedCreationScenes() {
    }

    public static void logisticsBridge(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("logistics_bridge", "Connecting Refined Storage to Create");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos bridge = util.grid().at(2, 1, 2);
        BlockPos packager = util.grid().at(1, 1, 2);
        BlockPos link = util.grid().at(1, 2, 2);
        Selection network = util.select().fromTo(3, 1, 2, 4, 1, 3);

        scene.world().modifyBlock(bridge, state -> state.setValue(LogisticsBridgeBlock.ACTIVE, false), false);
        scene.world().showSection(network, Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(bridge), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("Place a Logistics Bridge so that it touches a Refined Storage network, directly or through cables")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(bridge));
        scene.idle(80);

        scene.world().modifyBlock(bridge, state -> state.setValue(LogisticsBridgeBlock.ACTIVE, true), false);
        scene.overlay().showText(50)
                .text("Its sides light up once the network is powered")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(bridge, Direction.NORTH));
        scene.idle(60);

        scene.world().showSection(util.select().position(packager), Direction.EAST);
        scene.idle(15);
        scene.overlay().showText(60)
                .text("Place a Packager against the bridge, facing away from it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(packager, Direction.WEST));
        scene.idle(70);

        scene.world().showSection(util.select().position(link), Direction.DOWN);
        scene.idle(10);
        PonderHilo.linkEffect(scene, link);
        scene.overlay().showText(70)
                .text("Attach a Stock Link to the Packager, tuned to your logistics network")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(link));
        scene.idle(80);

        scene.overlay().showText(70)
                .text("Stock Keepers on that network can now see and order every item stored in Refined Storage")
                .placeNearTarget()
                .pointAt(util.vector().topOf(bridge));
        scene.idle(80);

        ItemStack order = PackageItem.containing(List.of(new ItemStack(Items.IRON_INGOT, 32)));
        PackageItem.addAddress(order, "Workshop");
        PonderHilo.linkEffect(scene, link);
        PonderHilo.packagerCreate(scene, packager, order);
        scene.overlay().showText(60)
                .text("Requests are packaged straight out of Refined Storage")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(packager, Direction.WEST));
        scene.idle(70);
        PonderHilo.packagerClear(scene, packager);
        scene.idle(10);

        ItemStack delivery = PackageItem.containing(List.of(new ItemStack(Items.COPPER_INGOT, 16)));
        PonderHilo.packagerUnpack(scene, packager, delivery);
        scene.overlay().showText(70)
                .text("Packages delivered to this Packager are unpacked directly into the network")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(packager, Direction.WEST));
        scene.idle(80);

        scene.overlay().showText(90)
                .text("Items Refined Storage can craft are listed as ∞. Ordering more than is stored starts an autocrafting task")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(bridge));
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void logisticsDispatcher(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("logistics_dispatcher", "Autocrafting with Create factories");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos autocrafter = util.grid().at(3, 1, 2);
        BlockPos dispatcher = util.grid().at(2, 1, 2);
        BlockPos packager = util.grid().at(1, 1, 2);
        Selection network = util.select().fromTo(4, 1, 2, 4, 1, 3);

        scene.world().showSection(network, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(dispatcher), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("The Logistics Dispatcher lets Refined Storage autocrafting use a Create factory")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(dispatcher));
        scene.idle(80);

        scene.world().showSection(util.select().position(autocrafter), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Place an Autocrafter facing the dispatcher, and put processing patterns in it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(autocrafter));
        scene.idle(80);

        scene.world().showSection(util.select().position(packager), Direction.EAST);
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.SOUTH);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("Place a Packager against the dispatcher, facing away, with a sign giving your factory's address")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(packager, Direction.NORTH));
        scene.idle(90);

        scene.world().modifyBlock(autocrafter, state -> withBoolean(state, "active", true), false);
        ItemStack ingredients = PackageItem.containing(List.of(new ItemStack(Items.IRON_INGOT, 8),
                new ItemStack(Items.REDSTONE, 4)));
        PackageItem.addAddress(ingredients, "Factory");
        PonderHilo.packagerCreate(scene, packager, ingredients);
        scene.overlay().showText(80)
                .text("When Refined Storage runs one of those patterns, the ingredients are packed and sent to the factory")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(packager, Direction.WEST));
        scene.idle(90);
        PonderHilo.packagerClear(scene, packager);
        scene.idle(10);

        scene.overlay().showText(90)
                .text("Have the factory send its output to the Logistics Bridge's Packager. Refined Storage counts it toward the task")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(autocrafter));
        scene.idle(100);

        scene.overlay().showText(70)
                .text("Patterns may only use items, since packages can't carry fluids")
                .placeNearTarget()
                .pointAt(util.vector().topOf(dispatcher));
        scene.idle(80);
        scene.world().modifyBlock(autocrafter, state -> withBoolean(state, "active", false), false);
        scene.markAsFinished();
    }

    public static void logisticsWorkbench(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("logistics_workbench", "Crafting from a Create network");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos workbench = util.grid().at(2, 1, 2);
        BlockPos packager = util.grid().at(2, 1, 1);
        BlockPos frogport = util.grid().at(2, 2, 1);
        BlockPos sourcePackager = util.grid().at(4, 1, 3);
        BlockPos sourceLink = util.grid().at(4, 2, 3);
        Selection source = util.select().fromTo(4, 1, 3, 4, 2, 4);

        scene.world().showSection(util.select().position(workbench), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("The Logistics Workbench is a crafting table that orders its ingredients from a Create logistics network")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(workbench));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(workbench), Pointing.DOWN, 50)
                .withItem(AllBlocks.STOCK_LINK.asStack())
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Right-click it with a tuned Stock Link, or a Stock Link with it before placing, to join that network")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(workbench));
        scene.idle(90);

        scene.world().showSection(util.select().position(packager), Direction.SOUTH);
        scene.idle(10);
        scene.world().showSection(util.select().position(frogport), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("Place a Packager against it, facing away, and give the Packager a way to receive packages")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(packager, Direction.WEST));
        scene.idle(90);

        scene.world().showSection(source, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("Its screen lists everything on the network. Click an item to order it to the workbench")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(sourceLink));
        scene.idle(60);

        ItemStack order = PackageItem.containing(List.of(new ItemStack(Items.OAK_PLANKS, 16)));
        PackageItem.addAddress(order, "Workbench");
        PonderHilo.linkEffect(scene, sourceLink);
        PonderHilo.packagerCreate(scene, sourcePackager, order);
        scene.idle(40);
        PonderHilo.packagerClear(scene, sourcePackager);
        scene.idle(10);
        PonderHilo.packagerUnpack(scene, packager, order);
        scene.overlay().showText(80)
                .text("Deliveries are unpacked into the workbench's buffer, ready for the crafting grid")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(workbench));
        scene.idle(90);

        ItemStack returns = PackageItem.containing(List.of(new ItemStack(Items.STICK, 8)));
        PonderHilo.packagerCreate(scene, packager, returns);
        scene.overlay().showText(80)
                .text("Items on the return shelf are packaged and sent back through the same Packager")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(packager, Direction.WEST));
        scene.idle(90);
        PonderHilo.packagerClear(scene, packager);
        scene.markAsFinished();
    }

    /**
     * Sets a boolean property by name, for other mods' blocks whose property fields we don't depend on.
     */
    private static BlockState withBoolean(BlockState state, String name, boolean value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property instanceof BooleanProperty booleanProperty ? state.setValue(booleanProperty, value) : state;
    }
}
