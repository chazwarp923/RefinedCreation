package tech.chazwarp923.refinedcreation.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.autocrafting.PatternState;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.content.Items;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Helpers for building Refined Storage setups in game tests.
 */
public final class RsTestSupport {
    private RsTestSupport() {
    }

    public static Block rsBlock(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", path));
    }

    /**
     * @return a processing pattern turning {@code inputAmount} of {@code input} into {@code outputAmount} of
     * {@code output}
     */
    public static ItemStack processingPattern(ItemResource input, long inputAmount, ItemResource output,
                                              long outputAmount) {
        return processingPattern(List.of(new ResourceAmount(input, inputAmount)),
                new ResourceAmount(output, outputAmount));
    }

    /**
     * @return a processing pattern with one ingredient slot per input, making {@code output}
     */
    public static ItemStack processingPattern(List<ResourceAmount> inputs, ResourceAmount output) {
        List<Optional<ProcessingPatternState.ProcessingIngredient>> ingredients = new ArrayList<>();
        for (ResourceAmount input : inputs) {
            ingredients.add(Optional.of(new ProcessingPatternState.ProcessingIngredient(input, List.of())));
        }
        ItemStack pattern = new ItemStack(Items.INSTANCE.getPattern());
        pattern.set(DataComponents.INSTANCE.getPatternState(),
                new PatternState(UUID.randomUUID(), PatternType.PROCESSING));
        pattern.set(DataComponents.INSTANCE.getProcessingPatternState(),
                new ProcessingPatternState(ingredients, List.of(Optional.of(output))));
        return pattern;
    }

    /**
     * The Autocrafter's pattern inventory is package-private; reflection keeps tests free of a GUI round trip.
     */
    public static Container patternContainer(Object autocrafter) {
        try {
            Field field = autocrafter.getClass().getDeclaredField("patternContainer");
            field.setAccessible(true);
            return (Container) field.get(autocrafter);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot reach the Autocrafter pattern inventory", e);
        }
    }

    /**
     * Sets the facing of an RS block, whose property is called "direction".
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState withDirection(BlockState state, Direction direction) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("direction") && property.getPossibleValues().contains(direction)) {
                return state.setValue((Property) property, (Comparable) direction);
            }
        }
        throw new IllegalStateException("No direction property on " + state);
    }
}
