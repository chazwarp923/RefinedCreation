package tech.chazwarp923.refinedcreation.compat.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.simibubi.create.content.logistics.BigItemStack;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import tech.chazwarp923.refinedcreation.registry.ModMenus;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchBlockEntity;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchMenu;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.TransferPayload;

/**
 * JEI's "+" button for crafting recipes in a Logistics Workbench. The server does the actual work, see
 * {@code LogisticsWorkbenchBlockEntity.transferRecipe}. This class decides how the button looks, using the
 * client's view of the menu:
 * <ul>
 * <li>Every ingredient is in the grid, buffer or inventory: normal button.</li>
 * <li>Some ingredients are only on the network: the transfer still goes ahead and orders them. Those slots
 * are highlighted orange as a warning.</li>
 * <li>An ingredient is nowhere to be found: the button is disabled and those slots are highlighted red.</li>
 * </ul>
 * JEI calls this every frame to draw the button, so the network stock is indexed by item once per stock
 * update rather than scanned for each check.
 */
public class WorkbenchRecipeTransferHandler
        implements IRecipeTransferHandler<LogisticsWorkbenchMenu, RecipeHolder<CraftingRecipe>> {
    private static final int NETWORK_HIGHLIGHT = 0x80FFAA00;

    private final IRecipeTransferHandlerHelper helper;

    @Nullable
    private LogisticsWorkbenchMenu indexedMenu;
    private int indexedStockVersion = -1;
    private final Map<Item, List<ItemStack>> stockIndex = new HashMap<>();

    public WorkbenchRecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends LogisticsWorkbenchMenu> getContainerClass() {
        return LogisticsWorkbenchMenu.class;
    }

    @Override
    public Optional<MenuType<LogisticsWorkbenchMenu>> getMenuType() {
        return Optional.of(ModMenus.LOGISTICS_WORKBENCH.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    /**
     * JEI 19.70 marks this for removal in favour of the {@code IRecipeTransferContext} overload. That overload
     * adds an asynchronous completion callback, but this method is still the abstract one every handler must
     * implement, and the new overload's default delegates here.
     */
    @SuppressWarnings("removal")
    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(LogisticsWorkbenchMenu menu, RecipeHolder<CraftingRecipe> recipe,
                                               IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer,
                                               boolean doTransfer) {
        List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        // JEI's crafting category always lays out a full 3x3 grid, empty slots included.
        if (inputs.size() != LogisticsWorkbenchBlockEntity.GRID_SLOTS) {
            return helper.createInternalError();
        }

        List<List<ItemStack>> options = new ArrayList<>();
        List<IRecipeSlotView> networkOnly = new ArrayList<>();
        List<IRecipeSlotView> missing = new ArrayList<>();
        for (IRecipeSlotView view : inputs) {
            List<ItemStack> choices = view.getItemStacks()
                    .filter(stack -> !stack.isEmpty())
                    .limit(TransferPayload.MAX_OPTIONS_PER_SLOT)
                    .map(stack -> stack.copyWithCount(1))
                    .toList();
            options.add(choices);
            if (choices.isEmpty() || anyOnHand(menu, choices)) {
                continue;
            }
            if (anyOnNetwork(menu, choices)) {
                networkOnly.add(view);
            } else {
                missing.add(view);
            }
        }

        if (!missing.isEmpty()) {
            return helper.createUserErrorForMissingSlots(
                    Component.translatable("refinedcreation.workbench.jei.missing"), missing);
        }
        if (doTransfer) {
            PacketDistributor.sendToServer(new TransferPayload(menu.containerId, options, maxTransfer));
        }
        return networkOnly.isEmpty() ? null : new NetworkOrderNotice(networkOnly);
    }

    /**
     * @return whether the grid, buffer or player inventory holds any of the choices
     */
    private static boolean anyOnHand(LogisticsWorkbenchMenu menu, List<ItemStack> choices) {
        for (int index = LogisticsWorkbenchMenu.GRID_START; index < LogisticsWorkbenchMenu.PLAYER_END; index++) {
            boolean isReturnSlot = index >= LogisticsWorkbenchMenu.RETURN_START
                    && index < LogisticsWorkbenchMenu.PLAYER_START;
            if (isReturnSlot) {
                continue;
            }
            ItemStack stack = menu.getSlot(index).getItem();
            if (!stack.isEmpty() && matchesAny(stack, choices)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyOnNetwork(LogisticsWorkbenchMenu menu, List<ItemStack> choices) {
        if (indexedMenu != menu || indexedStockVersion != menu.getStockVersion()) {
            indexedMenu = menu;
            indexedStockVersion = menu.getStockVersion();
            stockIndex.clear();
            for (BigItemStack entry : menu.getStock()) {
                if (entry.count > 0) {
                    stockIndex.computeIfAbsent(entry.stack.getItem(), item -> new ArrayList<>()).add(entry.stack);
                }
            }
        }
        for (ItemStack choice : choices) {
            for (ItemStack stocked : stockIndex.getOrDefault(choice.getItem(), List.of())) {
                if (ItemStack.isSameItemSameComponents(stocked, choice)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAny(ItemStack stack, List<ItemStack> choices) {
        for (ItemStack choice : choices) {
            if (ItemStack.isSameItemSameComponents(stack, choice)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A warning, not a blocker: the transfer still happens, but these slots will be ordered from the network.
     */
    private record NetworkOrderNotice(List<IRecipeSlotView> slots) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            return NETWORK_HIGHLIGHT;
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(Component.translatable("refinedcreation.workbench.jei.order"));
            tooltip.add(Component.translatable("refinedcreation.workbench.jei.order.detail")
                    .withStyle(ChatFormatting.GRAY));
        }

        @Override
        public void showError(GuiGraphics graphics, int mouseX, int mouseY, IRecipeSlotsView recipeSlotsView,
                              int recipeX, int recipeY) {
            graphics.pose().pushPose();
            graphics.pose().translate(recipeX, recipeY, 0);
            for (IRecipeSlotView slot : slots) {
                slot.drawHighlight(graphics, NETWORK_HIGHLIGHT);
            }
            graphics.pose().popPose();
        }
    }
}
