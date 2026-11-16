package tech.chazwarp923.refinedcreation.workbench;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/**
 * A 3x3 crafting grid backed by a list the caller owns. On the server that list belongs to the workbench block
 * entity, so the grid keeps its contents when the GUI closes and every open menu shares it. On the client it's a
 * throwaway list that the menu's slot sync fills.
 * <p>
 * {@code onChange} runs after every modification. The block entity uses it to recompute each open menu's result,
 * synchronously like vanilla's crafting table, so shift-click crafting sees the new result straight away.
 */
public class WorkbenchCraftingContainer implements CraftingContainer {
    public static final int SIZE = 3;

    private final NonNullList<ItemStack> items;
    private final Runnable onChange;

    public WorkbenchCraftingContainer(NonNullList<ItemStack> items, Runnable onChange) {
        this.items = items;
        this.onChange = onChange;
    }

    @Override
    public int getWidth() {
        return SIZE;
    }

    @Override
    public int getHeight() {
        return SIZE;
    }

    @Override
    public List<ItemStack> getItems() {
        return List.copyOf(items);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            onChange.run();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        onChange.run();
    }

    @Override
    public void setChanged() {
        onChange.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
        onChange.run();
    }

    @Override
    public void fillStackedContents(StackedContents contents) {
        for (ItemStack stack : items) {
            contents.accountSimpleStack(stack);
        }
    }
}
