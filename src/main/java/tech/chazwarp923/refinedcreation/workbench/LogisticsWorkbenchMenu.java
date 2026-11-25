package tech.chazwarp923.refinedcreation.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import tech.chazwarp923.refinedcreation.registry.ModMenus;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.StatusPayload;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.StockPayload;

/**
 * The Logistics Workbench's container. On the server its slots are backed by the block entity. On the client
 * they're backed by throwaway containers that vanilla's slot sync fills.
 * <p>
 * The network stock list isn't made of slots. The server sends it to the client as a {@link StockPayload} when
 * the screen asks for it on opening, then every {@link #STOCK_SYNC_INTERVAL} ticks if it has changed.
 */
public class LogisticsWorkbenchMenu extends AbstractContainerMenu {
    public static final int STOCK_SYNC_INTERVAL = 20;

    // Slot layout. Positions are item positions, i.e. 1px inside the 18px slot background the screen draws.
    public static final int RESULT_SLOT = 0;
    public static final int GRID_START = 1;
    public static final int BUFFER_START = GRID_START + LogisticsWorkbenchBlockEntity.GRID_SLOTS;
    public static final int RETURN_START = BUFFER_START + LogisticsWorkbenchBlockEntity.BUFFER_SLOTS;
    public static final int PLAYER_START = RETURN_START + LogisticsWorkbenchBlockEntity.RETURN_SLOTS;
    public static final int PLAYER_END = PLAYER_START + 36;

    /*
     * Coordinates are relative to the screen's origin, which is the top-left of the crafting section, not of the
     * whole window. The header and the stock list sit above it at negative y, and their height depends on the
     * screen size. Keeping the origin there means every real slot has a fixed position however many stock rows
     * the screen shows.
     */
    public static final int GRID_X = 21;
    public static final int GRID_Y = 4;
    public static final int RESULT_X = 107;
    public static final int RESULT_Y = 22;
    /** Return slots sit on the shelf in the footer, Create's Stock Keeper order shelf. */
    public static final int RETURN_X = 25;
    public static final int RETURN_Y = 69;
    public static final int RETURN_SPACING = 20;
    /**
     * The buffer sits in a side panel left of the main window (negative x). It's on the left so it doesn't
     * cover JEI's ingredient list, and the JEI plugin reports it so JEI's bookmarks keep clear.
     */
    public static final int BUFFER_X = -59;
    public static final int BUFFER_Y = -10;
    public static final int BUFFER_COLS = 3;
    public static final int PLAYER_X = 33;
    public static final int PLAYER_Y = 130;
    public static final int HOTBAR_Y = 188;

    @Nullable
    private final LogisticsWorkbenchBlockEntity workbench;
    private final Player player;
    private final BlockPos pos;
    private final WorkbenchCraftingContainer craftGrid;
    private final ResultContainer result = new ResultContainer();

    // Client-side state.
    private List<BigItemStack> stock = List.of();
    private int stockVersion;
    private boolean tuned;
    private String deliveryAddress;
    private String returnAddress;
    @Nullable
    private Component status;
    private long statusTime;
    /** A JEI transfer waiting for the player to choose how many crafts to order. Client only. */
    @Nullable
    private PendingTransfer pendingTransfer;

    private long lastStockSync = Long.MIN_VALUE / 2;
    /** What the client was last sent, to skip periodic syncs that would change nothing. Server only. */
    @Nullable
    private InventorySummary lastSentStock;
    private boolean lastSentTuned;

    /**
     * Server-side constructor.
     */
    public LogisticsWorkbenchMenu(int containerId, Inventory inventory, LogisticsWorkbenchBlockEntity workbench) {
        this(containerId, inventory, workbench, workbench.getBlockPos(), workbench.getCraftGrid(),
                workbench.getBuffer(), workbench.getReturns(), workbench.getFrequency() != null,
                workbench.getDeliveryAddress(), workbench.getReturnAddress());
        workbench.addMenu(this);
        slotsChanged(craftGrid);
    }

    /**
     * Client-side factory, reading what {@link LogisticsWorkbenchBlockEntity#writeMenuData} wrote.
     */
    public static LogisticsWorkbenchMenu fromNetwork(int containerId, Inventory inventory,
                                                     RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean tuned = buf.readBoolean();
        String delivery = buf.readUtf(LogisticsWorkbenchBlockEntity.MAX_ADDRESS_LENGTH);
        String returns = buf.readUtf(LogisticsWorkbenchBlockEntity.MAX_ADDRESS_LENGTH);
        return new LogisticsWorkbenchMenu(containerId, inventory, null, pos,
                new WorkbenchCraftingContainer(
                        NonNullList.withSize(LogisticsWorkbenchBlockEntity.GRID_SLOTS, ItemStack.EMPTY), () -> {
                        }),
                new ItemStackHandler(LogisticsWorkbenchBlockEntity.BUFFER_SLOTS),
                new ItemStackHandler(LogisticsWorkbenchBlockEntity.RETURN_SLOTS),
                tuned, delivery, returns);
    }

    private LogisticsWorkbenchMenu(int containerId, Inventory inventory,
                                   @Nullable LogisticsWorkbenchBlockEntity workbench, BlockPos pos,
                                   WorkbenchCraftingContainer craftGrid, ItemStackHandler buffer,
                                   ItemStackHandler returns, boolean tuned, String deliveryAddress,
                                   String returnAddress) {
        super(ModMenus.LOGISTICS_WORKBENCH.get(), containerId);
        this.workbench = workbench;
        this.player = inventory.player;
        this.pos = pos;
        this.craftGrid = craftGrid;
        this.tuned = tuned;
        this.deliveryAddress = deliveryAddress;
        this.returnAddress = returnAddress;

        addSlot(new WorkbenchResultSlot(player, craftGrid, result, RESULT_X, RESULT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(craftGrid, col + row * 3, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }
        for (int row = 0; row < LogisticsWorkbenchBlockEntity.BUFFER_SLOTS / BUFFER_COLS; row++) {
            for (int col = 0; col < BUFFER_COLS; col++) {
                addSlot(new SlotItemHandler(buffer, col + row * BUFFER_COLS,
                        BUFFER_X + col * 18, BUFFER_Y + row * 18));
            }
        }
        for (int i = 0; i < LogisticsWorkbenchBlockEntity.RETURN_SLOTS; i++) {
            addSlot(new SlotItemHandler(returns, i, RETURN_X + i * RETURN_SPACING, RETURN_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    /**
     * The result slot. Before a craft it records the grid as the pattern; after it, it refills the grid from
     * the buffer. Shift-clicking therefore crafts until the buffer runs out of an ingredient.
     */
    private class WorkbenchResultSlot extends ResultSlot {
        WorkbenchResultSlot(Player player, WorkbenchCraftingContainer grid, ResultContainer result, int x, int y) {
            super(player, grid, result, 0, x, y);
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            if (workbench != null) {
                workbench.rememberPattern();
            }
            super.onTake(taker, stack);
            if (workbench != null) {
                workbench.refillGrid();
            }
        }
    }

    // --- Crafting --------------------------------------------------------------------------------------------

    @Override
    public void slotsChanged(Container container) {
        if (container == craftGrid) {
            updateResult();
        }
        super.slotsChanged(container);
    }

    private void updateResult() {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        CraftingInput input = craftGrid.asCraftInput();
        ItemStack output = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level);
        // The recipe-unlock check (doLimitedCrafting) only applies to real server players.
        boolean allowed = recipe.isPresent() && (!(player instanceof ServerPlayer serverPlayer)
                || result.setRecipeUsed(level, serverPlayer, recipe.get()));
        if (allowed) {
            ItemStack assembled = recipe.get().value().assemble(input, level.registryAccess());
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                output = assembled;
            }
        }
        result.setItem(0, output);
    }

    // --- Shift-clicking --------------------------------------------------------------------------------------

    /**
     * Result → player. Grid → buffer, then player. Buffer and returns → player. Player → returns, then buffer.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index < BUFFER_START) {
            if (!moveItemStackTo(stack, BUFFER_START, RETURN_START, false)
                    && !moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, RETURN_START, PLAYER_START, false)
                    && !moveItemStackTo(stack, BUFFER_START, RETURN_START, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        if (index == RESULT_SLOT) {
            player.drop(stack, false);
        }
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    // --- Server: stock sync and actions ----------------------------------------------------------------------

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // Counted in game time: broadcastChanges also runs on every slot change, not just once per tick.
        if (player.level().getGameTime() - lastStockSync >= STOCK_SYNC_INTERVAL) {
            sendStock(false);
        }
    }

    /**
     * Sends the network stock to the client and restarts the periodic sync from now.
     */
    void sendStockNow() {
        sendStock(true);
    }

    /**
     * @param force send even if the stock is what the client was last sent
     */
    private void sendStock(boolean force) {
        if (workbench == null || !(player instanceof ServerPlayer serverPlayer)
                || !serverPlayer.connection.hasChannel(StockPayload.TYPE)) {
            return;
        }
        lastStockSync = player.level().getGameTime();
        InventorySummary summary = workbench.getNetworkStock(false);
        boolean tuned = workbench.getFrequency() != null;
        if (!force && tuned == lastSentTuned && isSameStock(summary, lastSentStock)) {
            return;
        }
        List<BigItemStack> stacks = new ArrayList<>();
        InventorySummary sent = new InventorySummary();
        for (BigItemStack entry : summary.getStacks()) {
            ItemStack stack = entry.stack.copyWithCount(1);
            stacks.add(new BigItemStack(stack, entry.count));
            sent.add(stack, entry.count);
        }
        lastSentStock = sent;
        lastSentTuned = tuned;
        PacketDistributor.sendToPlayer(serverPlayer, new StockPayload(containerId, tuned, stacks));
    }

    /**
     * @return whether {@code current} holds exactly the items and counts of {@code previous}
     */
    public static boolean isSameStock(InventorySummary current, @Nullable InventorySummary previous) {
        if (previous == null) {
            return false;
        }
        List<BigItemStack> stacks = current.getStacks();
        if (stacks.size() != previous.getStacks().size()) {
            return false;
        }
        for (BigItemStack entry : stacks) {
            if (previous.getCountOf(entry.stack) != entry.count) {
                return false;
            }
        }
        return true;
    }

    public BlockPos getPos() {
        return pos;
    }

    void handleRequest(ItemStack item, int count) {
        if (workbench != null) {
            sendStatus(workbench.requestItem(item, Math.min(count, LogisticsWorkbenchBlockEntity.MAX_REQUEST)));
        }
    }

    void handleAction(WorkbenchPackets.Action action, int amount) {
        if (workbench == null) {
            return;
        }
        Component message = switch (action) {
            case ORDER_PATTERN -> workbench.orderPattern(
                    Math.max(1, Math.min(amount, LogisticsWorkbenchBlockEntity.MAX_CRAFTS)));
            case CLEAR_GRID -> workbench.clearGrid(player);
            case SEND_RETURNS -> workbench.sendReturns();
        };
        sendStatus(message);
    }

    void handleTransfer(List<List<ItemStack>> options, boolean max, int crafts) {
        if (workbench != null) {
            sendStatus(workbench.transferRecipe(player, options, max,
                    Math.max(1, Math.min(crafts, LogisticsWorkbenchBlockEntity.MAX_CRAFTS))));
        }
    }

    void handleAddress(boolean isReturn, String address) {
        if (workbench == null) {
            return;
        }
        if (isReturn) {
            workbench.setReturnAddress(address);
        } else {
            workbench.setDeliveryAddress(address);
        }
    }

    private void sendStatus(Component message) {
        if (player instanceof ServerPlayer serverPlayer && !message.getString().isEmpty()
                && serverPlayer.connection.hasChannel(StatusPayload.TYPE)) {
            PacketDistributor.sendToPlayer(serverPlayer, new StatusPayload(containerId, message));
        }
    }

    // --- Client state ----------------------------------------------------------------------------------------

    public void receiveStock(boolean tuned, List<BigItemStack> stock) {
        this.tuned = tuned;
        this.stock = stock;
        stockVersion++;
    }

    public void receiveStatus(Component status) {
        this.status = status;
        this.statusTime = System.currentTimeMillis();
    }

    /**
     * Holds a JEI recipe transfer until the screen has asked how many crafts to order ingredients for.
     */
    public void setPendingTransfer(List<List<ItemStack>> options, ItemStack result) {
        pendingTransfer = new PendingTransfer(options, result.copy());
    }

    /**
     * @return the transfer waiting for a craft count, or {@code null}; either way, none is waiting afterwards
     */
    @Nullable
    public PendingTransfer takePendingTransfer() {
        PendingTransfer transfer = pendingTransfer;
        pendingTransfer = null;
        return transfer;
    }

    /**
     * A JEI recipe transfer held until the player chooses how many crafts to order for.
     *
     * @param options the acceptable items for each grid slot, as sent in a transfer
     * @param result  what the recipe makes, shown while asking
     */
    public record PendingTransfer(List<List<ItemStack>> options, ItemStack result) {
    }

    public List<BigItemStack> getStock() {
        return stock;
    }

    public int getStockVersion() {
        return stockVersion;
    }

    public boolean isTuned() {
        return tuned;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getReturnAddress() {
        return returnAddress;
    }

    /**
     * @return the latest status message if it's recent enough to show, else {@code null}
     */
    @Nullable
    public Component getStatus(long maxAgeMillis) {
        return status != null && System.currentTimeMillis() - statusTime < maxAgeMillis ? status : null;
    }

    // --- Lifecycle -------------------------------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        if (workbench == null) {
            return true;
        }
        return !workbench.isRemoved() && player.distanceToSqr(pos.getCenter()) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (workbench != null) {
            workbench.removeMenu(this);
        }
    }
}
