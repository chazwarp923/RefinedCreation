package tech.chazwarp923.refinedcreation.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import tech.chazwarp923.refinedcreation.dispatcher.LogisticsDispatcherBlock;
import tech.chazwarp923.refinedcreation.registry.ModBlockEntities;

/**
 * The Logistics Workbench: a crafting station that works from a Create logistics network the way an RS Crafting
 * Grid works from an RS network, but with Create's physical deliveries.
 * <ul>
 * <li>The <b>stock list</b> shows the network's items. Ordering one has the network package it and send it to
 * the workbench's delivery address.</li>
 * <li>Packages arriving at a Packager facing the workbench are unpacked into the <b>buffer</b>, see
 * {@link WorkbenchUnpackingHandler}.</li>
 * <li>The <b>crafting grid</b> remembers the last pattern that was crafted or ordered. When a craft empties a
 * slot, or new items arrive in the buffer, the empty pattern slots are refilled from the buffer. Shift-clicking
 * the result therefore keeps crafting while the buffer lasts.</li>
 * <li>Items in the <b>return</b> slots are packaged and sent through the same Packager to the return address,
 * which defaults to the address on the Packager's sign.</li>
 * </ul>
 * Actions return a status message for the GUI rather than messaging the player, because the action bar is hidden
 * behind the screen.
 */
public class LogisticsWorkbenchBlockEntity extends BlockEntity implements MenuProvider {
    public static final int GRID_SLOTS = 9;
    public static final int BUFFER_SLOTS = 18;
    public static final int RETURN_SLOTS = 9;
    public static final int MAX_ADDRESS_LENGTH = 64;

    @Nullable
    private UUID frequency;
    private String deliveryAddress = "";
    private String returnAddress = "";

    private final NonNullList<ItemStack> grid = NonNullList.withSize(GRID_SLOTS, ItemStack.EMPTY);
    /** One of each ingredient of the last pattern crafted or ordered. Used to refill the grid. */
    private final NonNullList<ItemStack> pattern = NonNullList.withSize(GRID_SLOTS, ItemStack.EMPTY);
    private final WorkbenchCraftingContainer craftGrid = new WorkbenchCraftingContainer(grid, this::onGridChanged);
    private final ItemStackHandler buffer = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // Deferred to the tick so the refill never runs in the middle of someone else's slot operation.
            refillPending = true;
        }
    };
    private final ItemStackHandler returns = new ItemStackHandler(RETURN_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final List<LogisticsWorkbenchMenu> openMenus = new CopyOnWriteArrayList<>();
    private boolean refillPending;
    /** Suppresses per-slot change callbacks while the grid is refilled as one change. */
    private boolean batchingGridChanges;

    public LogisticsWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_WORKBENCH.get(), pos, state);
    }

    // --- Accessors -------------------------------------------------------------------------------------------

    public WorkbenchCraftingContainer getCraftGrid() {
        return craftGrid;
    }

    public ItemStackHandler getBuffer() {
        return buffer;
    }

    public ItemStackHandler getReturns() {
        return returns;
    }

    @Nullable
    public UUID getFrequency() {
        return frequency;
    }

    public void setFrequency(@Nullable UUID frequency) {
        this.frequency = frequency;
        setChanged();
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getReturnAddress() {
        return returnAddress;
    }

    public void setDeliveryAddress(String address) {
        deliveryAddress = clampAddress(address);
        setChanged();
    }

    public void setReturnAddress(String address) {
        returnAddress = clampAddress(address);
        setChanged();
    }

    private static String clampAddress(String address) {
        String trimmed = address.strip();
        return trimmed.length() > MAX_ADDRESS_LENGTH ? trimmed.substring(0, MAX_ADDRESS_LENGTH) : trimmed;
    }

    /**
     * @return one of each ingredient of the remembered pattern, by grid slot
     */
    public List<ItemStack> getPattern() {
        return List.copyOf(pattern);
    }

    void addMenu(LogisticsWorkbenchMenu menu) {
        openMenus.add(menu);
    }

    void removeMenu(LogisticsWorkbenchMenu menu) {
        openMenus.remove(menu);
    }

    // --- Crafting grid ---------------------------------------------------------------------------------------

    private void onGridChanged() {
        if (batchingGridChanges) {
            return;
        }
        setChanged();
        for (LogisticsWorkbenchMenu menu : openMenus) {
            menu.slotsChanged(craftGrid);
        }
    }

    /**
     * Records the grid's current layout (one of each item) as the pattern to refill and order.
     */
    void rememberPattern() {
        if (craftGrid.isEmpty()) {
            return;
        }
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack stack = grid.get(i);
            pattern.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        setChanged();
    }

    /**
     * Fills every empty grid slot that the pattern uses with one matching item from the buffer.
     */
    void refillGrid() {
        boolean changed = false;
        batchingGridChanges = true;
        try {
            for (int i = 0; i < GRID_SLOTS; i++) {
                ItemStack wanted = pattern.get(i);
                if (wanted.isEmpty() || !grid.get(i).isEmpty()) {
                    continue;
                }
                ItemStack taken = extractFromBuffer(wanted, 1);
                if (!taken.isEmpty()) {
                    grid.set(i, taken);
                    changed = true;
                }
            }
        } finally {
            batchingGridChanges = false;
        }
        if (changed) {
            onGridChanged();
        }
    }

    private ItemStack extractFromBuffer(ItemStack wanted, int amount) {
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            if (ItemStack.isSameItemSameComponents(buffer.getStackInSlot(slot), wanted)) {
                ItemStack extracted = buffer.extractItem(slot, amount, false);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Moves the grid's contents to the buffer (or the player, if the buffer is full) and forgets the pattern.
     */
    public Component clearGrid(Player player) {
        batchingGridChanges = true;
        try {
            moveGridToStorage(player);
            for (int i = 0; i < GRID_SLOTS; i++) {
                pattern.set(i, ItemStack.EMPTY);
            }
        } finally {
            batchingGridChanges = false;
        }
        onGridChanged();
        return Component.translatable("refinedcreation.workbench.status.cleared");
    }

    /**
     * Empties the grid into the buffer, or into the player's inventory where the buffer is full. The caller
     * batches the grid change.
     */
    private void moveGridToStorage(Player player) {
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack stack = grid.get(i);
            grid.set(i, ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(buffer, stack, false);
            if (!remainder.isEmpty()) {
                player.getInventory().placeItemBackInInventory(remainder);
            }
        }
    }

    // --- Recipe transfer (JEI) -------------------------------------------------------------------------------

    /**
     * Lays a recipe out in the grid, as requested by JEI's recipe transfer.
     * <p>
     * The grid is emptied first. Then each slot gets one of its acceptable items, taken from the buffer first
     * and then from the player's inventory. With {@code max}, the slots are then topped up evenly towards a
     * full stack. The layout becomes the remembered pattern.
     * <p>
     * A slot whose ingredient is only on the network is left empty in the grid but kept in the pattern, and
     * its ingredient is ordered for one craft, so the slot refills when the delivery arrives.
     *
     * @param options the acceptable items for each grid slot, in grid order; an empty list leaves the slot empty
     */
    public Component transferRecipe(Player player, List<List<ItemStack>> options, boolean max) {
        InventorySummary stock = getNetworkStock(true);
        List<BigItemStack> toOrder = new ArrayList<>();
        MutableComponent unavailable = null;

        batchingGridChanges = true;
        try {
            moveGridToStorage(player);
            for (int i = 0; i < GRID_SLOTS; i++) {
                pattern.set(i, ItemStack.EMPTY);
                List<ItemStack> choices = i < options.size() ? options.get(i) : List.of();
                if (choices.isEmpty()) {
                    continue;
                }
                ItemStack taken = takeFromBufferOrPlayer(choices, player);
                if (!taken.isEmpty()) {
                    grid.set(i, taken);
                    pattern.set(i, taken.copyWithCount(1));
                    continue;
                }
                ItemStack fromNetwork = null;
                for (ItemStack choice : choices) {
                    if (stock.getCountOf(choice) > 0) {
                        fromNetwork = choice;
                        break;
                    }
                }
                if (fromNetwork != null) {
                    pattern.set(i, fromNetwork.copyWithCount(1));
                    addTo(toOrder, fromNetwork, 1);
                } else {
                    pattern.set(i, choices.get(0).copyWithCount(1));
                    Component name = choices.get(0).getHoverName();
                    unavailable = unavailable == null ? name.copy() : unavailable.append(", ").append(name);
                }
            }
            if (max) {
                topUpGrid(player);
            }
        } finally {
            batchingGridChanges = false;
        }
        onGridChanged();

        if (unavailable != null) {
            return error(Component.translatable("refinedcreation.workbench.status.missing", unavailable));
        }
        if (toOrder.isEmpty()) {
            return Component.empty();
        }
        Component problem = checkCanOrder();
        if (problem != null) {
            return problem;
        }
        if (!sendOrder(toOrder)) {
            return busy();
        }
        return Component.translatable("refinedcreation.workbench.status.transfer_ordered", toOrder.size());
    }

    /**
     * Adds one item at a time to each occupied grid slot, round-robin, until every slot is a full stack or
     * nothing more matches. Going round-robin shares an ingredient used in several slots evenly.
     */
    private void topUpGrid(Player player) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int i = 0; i < GRID_SLOTS; i++) {
                ItemStack stack = grid.get(i);
                if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) {
                    continue;
                }
                if (!takeFromBufferOrPlayer(List.of(stack), player).isEmpty()) {
                    stack.grow(1);
                    progress = true;
                }
            }
        }
    }

    /**
     * Takes one item matching any of the choices: from the buffer if it has one, otherwise from the player's
     * main inventory.
     */
    private ItemStack takeFromBufferOrPlayer(List<ItemStack> choices, Player player) {
        for (ItemStack choice : choices) {
            ItemStack taken = extractFromBuffer(choice, 1);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        List<ItemStack> inventory = player.getInventory().items;
        for (ItemStack choice : choices) {
            for (ItemStack stack : inventory) {
                if (ItemStack.isSameItemSameComponents(stack, choice)) {
                    return stack.split(1);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // --- Buffer (incoming deliveries) ------------------------------------------------------------------------

    /**
     * Inserts all of the given items into the buffer, or none of them.
     *
     * @return whether everything fit
     */
    public boolean insertAllIntoBuffer(List<ItemStack> items, boolean simulate) {
        ItemStackHandler trial = new ItemStackHandler(BUFFER_SLOTS);
        for (int slot = 0; slot < BUFFER_SLOTS; slot++) {
            trial.setStackInSlot(slot, buffer.getStackInSlot(slot).copy());
        }
        for (ItemStack stack : items) {
            if (!ItemHandlerHelper.insertItemStacked(trial, stack.copy(), false).isEmpty()) {
                return false;
            }
        }
        if (simulate) {
            return true;
        }
        for (ItemStack stack : items) {
            ItemHandlerHelper.insertItemStacked(buffer, stack.copy(), false);
        }
        return true;
    }

    // --- Ordering from the network ---------------------------------------------------------------------------

    /**
     * @return the network's stock, or an empty summary while untuned
     */
    public InventorySummary getNetworkStock(boolean accurate) {
        if (frequency == null) {
            return InventorySummary.EMPTY;
        }
        return LogisticsManager.getSummaryOfNetwork(frequency, accurate);
    }

    /**
     * Orders up to {@code count} of an item from the network, delivered to the delivery address.
     */
    public Component requestItem(ItemStack item, int count) {
        if (item.isEmpty() || count <= 0) {
            return Component.empty();
        }
        Component problem = checkCanOrder();
        if (problem != null) {
            return problem;
        }
        int available = getNetworkStock(true).getCountOf(item);
        if (available <= 0) {
            return error(Component.translatable("refinedcreation.workbench.status.none_available",
                    item.getHoverName()));
        }
        int amount = Math.min(count, available);
        if (!sendOrder(List.of(new BigItemStack(item.copyWithCount(1), amount)))) {
            return busy();
        }
        return Component.translatable("refinedcreation.workbench.status.ordered", amount, item.getHoverName());
    }

    /**
     * Orders what the buffer and grid are missing for {@code crafts} more crafts of the current pattern.
     * <p>
     * The pattern is the grid's layout if the grid holds anything, otherwise the remembered pattern. Ordering
     * also remembers it, so the grid refills as the ingredients arrive.
     */
    public Component orderPattern(int crafts) {
        if (crafts <= 0) {
            return Component.empty();
        }
        rememberPattern();
        List<BigItemStack> perCraft = new ArrayList<>();
        for (ItemStack stack : pattern) {
            if (!stack.isEmpty()) {
                addTo(perCraft, stack, 1);
            }
        }
        if (perCraft.isEmpty()) {
            return error(Component.translatable("refinedcreation.workbench.status.no_pattern"));
        }
        Component problem = checkCanOrder();
        if (problem != null) {
            return problem;
        }

        InventorySummary stock = getNetworkStock(true);
        List<BigItemStack> order = new ArrayList<>();
        MutableComponent missing = null;
        for (BigItemStack ingredient : perCraft) {
            int needed = ingredient.count * crafts - countOnHand(ingredient.stack);
            if (needed <= 0) {
                continue;
            }
            int available = stock.getCountOf(ingredient.stack);
            if (available < needed) {
                Component name = ingredient.stack.getHoverName();
                missing = missing == null ? name.copy() : missing.append(", ").append(name);
            }
            if (available > 0) {
                order.add(new BigItemStack(ingredient.stack.copyWithCount(1), Math.min(needed, available)));
            }
        }
        if (order.isEmpty()) {
            return missing == null
                    ? Component.translatable("refinedcreation.workbench.status.already_stocked")
                    : error(Component.translatable("refinedcreation.workbench.status.missing", missing));
        }
        if (!sendOrder(order)) {
            return busy();
        }
        return missing == null
                ? Component.translatable("refinedcreation.workbench.status.ordered_crafts", crafts)
                : Component.translatable("refinedcreation.workbench.status.ordered_partial", missing)
                        .withStyle(ChatFormatting.GOLD);
    }

    private int countOnHand(ItemStack item) {
        int total = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, item)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : grid) {
            if (ItemStack.isSameItemSameComponents(stack, item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void addTo(List<BigItemStack> list, ItemStack stack, int count) {
        for (BigItemStack entry : list) {
            if (ItemStack.isSameItemSameComponents(entry.stack, stack)) {
                entry.count += count;
                return;
            }
        }
        list.add(new BigItemStack(stack.copyWithCount(1), count));
    }

    @Nullable
    private Component checkCanOrder() {
        if (frequency == null) {
            return error(Component.translatable("refinedcreation.workbench.status.untuned"));
        }
        if (deliveryAddress.isBlank()) {
            return error(Component.translatable("refinedcreation.workbench.status.no_delivery_address"));
        }
        return null;
    }

    private boolean sendOrder(List<BigItemStack> stacks) {
        return LogisticsManager.broadcastPackageRequest(frequency, RequestType.PLAYER,
                PackageOrderWithCrafts.simple(stacks), null, deliveryAddress);
    }

    // --- Returns ---------------------------------------------------------------------------------------------

    /**
     * Packages the return slots and queues them on the adjacent Packager, addressed to the return address or,
     * if that's blank, the address on the Packager's sign.
     */
    public Component sendReturns() {
        if (level == null) {
            return Component.empty();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < returns.getSlots(); slot++) {
            if (!returns.getStackInSlot(slot).isEmpty()) {
                stacks.add(returns.getStackInSlot(slot));
            }
        }
        if (stacks.isEmpty()) {
            return error(Component.translatable("refinedcreation.workbench.status.nothing_to_return"));
        }
        PackagerBlockEntity packager = findPackager();
        if (packager == null) {
            return error(Component.translatable("refinedcreation.workbench.status.no_packager"));
        }
        String address = returnAddress;
        if (address.isBlank()) {
            packager.updateSignAddress();
            address = packager.signBasedAddress == null ? "" : packager.signBasedAddress;
        }
        if (address.isBlank()) {
            return error(Component.translatable("refinedcreation.workbench.status.no_return_address"));
        }

        int items = 0;
        for (ItemStack box : PackageSplitter.pack(stacks)) {
            PackageItem.addAddress(box, address);
            packager.queuedExitingPackages.add(new BigItemStack(box, 1));
        }
        for (int slot = 0; slot < returns.getSlots(); slot++) {
            items += returns.getStackInSlot(slot).getCount();
            returns.setStackInSlot(slot, ItemStack.EMPTY);
        }
        packager.setChanged();
        return Component.translatable("refinedcreation.workbench.status.returned", items, address);
    }

    @Nullable
    PackagerBlockEntity findPackager() {
        return level == null ? null : LogisticsDispatcherBlock.findPackager(level, worldPosition);
    }

    private static Component error(Component message) {
        return message.copy().withStyle(ChatFormatting.RED);
    }

    private static Component busy() {
        return error(Component.translatable("refinedcreation.workbench.status.busy"));
    }

    // --- Lifecycle -------------------------------------------------------------------------------------------

    void serverTick() {
        if (refillPending) {
            refillPending = false;
            refillGrid();
        }
    }

    void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, grid);
        for (ItemStackHandler handler : new ItemStackHandler[] {buffer, returns}) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(slot));
                handler.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        grid.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.refinedcreation.logistics_workbench");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LogisticsWorkbenchMenu(containerId, inventory, this);
    }

    /**
     * Writes what the client menu needs at open time; read by {@link LogisticsWorkbenchMenu#fromNetwork}.
     */
    void writeMenuData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeBoolean(frequency != null);
        buf.writeUtf(deliveryAddress, MAX_ADDRESS_LENGTH);
        buf.writeUtf(returnAddress, MAX_ADDRESS_LENGTH);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // "Freq" is the key Create's LogisticallyLinkedBlockItem writes, so a tuned item places a tuned block.
        frequency = tag.hasUUID("Freq") ? tag.getUUID("Freq") : null;
        deliveryAddress = tag.getString("DeliveryAddress");
        returnAddress = tag.getString("ReturnAddress");
        grid.clear();
        pattern.clear();
        ContainerHelper.loadAllItems(tag.getCompound("Grid"), grid, registries);
        ContainerHelper.loadAllItems(tag.getCompound("Pattern"), pattern, registries);
        loadHandler(buffer, tag.getCompound("Buffer"), registries);
        loadHandler(returns, tag.getCompound("Returns"), registries);
    }

    /**
     * Loads saved contents without letting the saved size replace the handler's own. {@code deserializeNBT}
     * would resize the handler to whatever was saved. A workbench saved before the return area grew from 6 to
     * 9 slots would then have fewer slots than the menu expects. Any items past the current size are dropped
     * into the first free slots instead.
     */
    private static void loadHandler(ItemStackHandler target, CompoundTag tag, HolderLookup.Provider registries) {
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(registries, tag);
        for (int slot = 0; slot < target.getSlots(); slot++) {
            target.setStackInSlot(slot, ItemStack.EMPTY);
        }
        for (int slot = 0; slot < saved.getSlots(); slot++) {
            ItemStack stack = saved.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (slot < target.getSlots() && target.getStackInSlot(slot).isEmpty()) {
                target.setStackInSlot(slot, stack);
            } else {
                ItemHandlerHelper.insertItemStacked(target, stack, false);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (frequency != null) {
            tag.putUUID("Freq", frequency);
        }
        tag.putString("DeliveryAddress", deliveryAddress);
        tag.putString("ReturnAddress", returnAddress);
        tag.put("Grid", ContainerHelper.saveAllItems(new CompoundTag(), grid, registries));
        tag.put("Pattern", ContainerHelper.saveAllItems(new CompoundTag(), pattern, registries));
        tag.put("Buffer", buffer.serializeNBT(registries));
        tag.put("Returns", returns.serializeNBT(registries));
    }
}
