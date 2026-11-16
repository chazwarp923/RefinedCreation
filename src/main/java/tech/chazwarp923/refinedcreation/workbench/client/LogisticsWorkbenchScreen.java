package tech.chazwarp923.refinedcreation.workbench.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen.SearchSyncMode;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import tech.chazwarp923.refinedcreation.ClientConfig;
import tech.chazwarp923.refinedcreation.compat.jei.JeiSearchSync;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchBlockEntity;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchMenu;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.ActionPayload;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.AddressPayload;
import tech.chazwarp923.refinedcreation.workbench.WorkbenchPackets.RequestPayload;

/**
 * The Logistics Workbench GUI, styled after Create's Stock Keeper. It draws Create's own textures, which are
 * always present because Create is a required dependency. From top to bottom:
 * <ol>
 * <li>Create's banner header with the title and search bar, flanked by the JEI search sync button (when JEI
 * is installed) and the sort button;</li>
 * <li>the network's stock on wooden rows, as many as the screen has room for (click to order);</li>
 * <li>the crafting section: the 3x3 grid and result, the delivery and return addresses, and the Order, Clear
 * and Send buttons;</li>
 * <li>the Stock Keeper's order shelf, which holds the return slots;</li>
 * <li>the player's inventory, in Create's inventory frame.</li>
 * </ol>
 * The buffer is a small window of its own on the left, framed and titled like the main one.
 * <p>
 * The screen's origin ({@code leftPos}, {@code topPos}) is the top-left of the crafting section. The header and
 * stock list sit above it at negative y, so every real slot keeps a fixed position however tall the stock list
 * is. {@code imageWidth} and {@code imageHeight} cover only the area below the origin. The parts outside it are
 * reported through {@link #getExtraAreas()} for JEI, and through {@link #hasClickedOutside} for clicks.
 */
public class LogisticsWorkbenchScreen extends AbstractContainerScreen<LogisticsWorkbenchMenu> {
    private static final ResourceLocation STOCK_KEEPER =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/stock_keeper.png");

    // Window geometry, matching the Stock Keeper. Its 256px-wide textures are drawn 15px left of the 226px
    // logical window, and the visible frame runs from x+9 to x+217.
    private static final int WINDOW_WIDTH = 226;
    private static final int TEXTURE_X = -15;
    private static final int FRAME_LEFT = 9;
    private static final int FRAME_RIGHT = 217;
    private static final int ROW_HEIGHT = 20;
    private static final int COL_WIDTH = 20;
    private static final int STOCK_COLS = 9;
    private static final int MIN_STOCK_ROWS = 3;
    private static final int MAX_STOCK_ROWS = 15;

    private static final int CRAFT_ROWS = 3;
    private static final int CRAFT_HEIGHT = CRAFT_ROWS * ROW_HEIGHT;
    private static final int FOOTER_Y = CRAFT_HEIGHT;
    /**
     * The Stock Keeper's footer is 80px tall: the order shelf, then the address tag and Send arrow, then a deep
     * stone base. The workbench keeps only the shelf, with the return address and Send button up in the
     * crafting section instead. It draws the footer in slices, see {@link #FOOTER_SLICES}.
     */
    private static final int FOOTER_HEIGHT = 49;
    private static final int INVENTORY_X = 25;
    private static final int INVENTORY_Y = FOOTER_Y + FOOTER_HEIGHT + 3;
    /** Create's inventory texture without the pointer at its bottom. */
    private static final int INVENTORY_FRAME_HEIGHT = 100;
    private static final int BELOW_ORIGIN = INVENTORY_Y + INVENTORY_FRAME_HEIGHT;

    private static final int ITEMS_X = (WINDOW_WIDTH - STOCK_COLS * COL_WIDTH) / 2 + 1;

    /**
     * Rows of the footer texture to draw, as {start, count} pairs relative to its top. This keeps the shelf
     * and the brown frame's top under it (rows 0-38), the frame's bottom edge and a stone base as thick as the
     * window's sides (rows 63-68), and the texture's own bottom edge (rows 76-79).
     */
    private static final int[][] FOOTER_SLICES = {{0, 39}, {63, 6}, {76, 4}};
    /**
     * The footer texture's first 24 columns are outside the window. Next to the address tag they hold the
     * tag's rope, which would otherwise poke out beside the shelf.
     */
    private static final int FOOTER_SKIP_COLUMNS = 24;

    /**
     * The buffer panel: a small window in the same stone-and-wood frame as the main one, standing 3px to its
     * left. It spans the crafting section and footer, with a banner title like the main header.
     */
    private static final int BORDER = 9;
    private static final int PANEL_GAP = 3;
    private static final int PANEL_WIDTH = BORDER * 2 + 6 + LogisticsWorkbenchMenu.BUFFER_COLS * 18;
    private static final int PANEL_X = FRAME_LEFT - PANEL_GAP - PANEL_WIDTH;
    /**
     * Tall enough for the title and the slots: 15px above the slots for the frame and banner, 12px below for
     * a 3px margin and the frame. Its bottom lines up with the main window's.
     */
    private static final int PANEL_HEIGHT =
            15 + LogisticsWorkbenchBlockEntity.BUFFER_SLOTS / LogisticsWorkbenchMenu.BUFFER_COLS * 18 + 12;
    private static final int PANEL_Y = FOOTER_Y + FOOTER_HEIGHT - PANEL_HEIGHT;
    /** The frame from the outside in, as in the Stock Keeper texture's sides. */
    private static final int[] FRAME_COLORS =
            {0xFF000000, 0xFF9AA49D, 0xFF8D8F8D, 0xFF8D8F8D, 0xFF8D8F8D, 0xFF8D8F8D, 0xFF828784, 0xFF404543,
                    0xFF000000};

    // Crafting section, right of the grid: the delivery address above the result and the return address
    // below it. Each address box's clipboard icon sits just right of its tag. The Order, Clear and Send
    // buttons stand in a column on the right, one beside each row.
    private static final int TAG_X = 80;
    private static final int TAG_WIDTH = 78;
    private static final int TAG_HEIGHT = 15;
    private static final int DELIVERY_Y = 3;
    private static final int RETURN_Y = 42;
    // In line with the address tags: order beside the delivery tag, send beside the return tag.
    private static final int BUTTONS_X = 179;
    private static final int ORDER_Y = DELIVERY_Y;
    private static final int CLEAR_Y = 22;
    private static final int SEND_Y = RETURN_Y;

    private static final long STATUS_MILLIS = 6000;

    // Beside the header's search bar (relative to the header's top): the JEI sync mode on the left, where the
    // Stock Keeper has it, and the sort button on the right.
    private static final int SYNC_BUTTON_X = 25;
    private static final int SYNC_BUTTON_Y = 18;
    private static final int SYNC_BUTTON_SIZE = 15;
    private static final int SORT_BUTTON_X = 186;
    private static final int SORT_BUTTON_Y = 18;

    private static final boolean JEI_LOADED = ModList.get().isLoaded("jei");

    // Create's palette.
    private static final int COLOR_TITLE = 0x714A40;
    private static final int COLOR_SEARCH = 0x4A2D31;
    private static final int COLOR_CREAM = 0xF8F8EC;
    private static final int COLOR_HINT = 0xCDBCA8;
    private static final int COLOR_INVENTORY_LABEL = 0x404040;
    private static final int ARGB_BLACK = 0xFF000000;
    private static final int ARGB_STONE = 0xFF8D8F8D;
    private static final int ARGB_STONE_LIGHT = 0xFF9AA49D;
    private static final int ARGB_STONE_DARK = 0xFF404543;
    private static final int ARGB_WOOD = 0xFF8C5D4B;
    private static final int ARGB_WOOD_DARK = 0xFF714A40;
    private static final int ARGB_CREAM = 0xFFF8F8EC;
    private static final int ARGB_CREAM_SHADE = 0xFFCDBCA8;

    private int stockRows = MIN_STOCK_ROWS;

    private EditBox search;
    private AddressEditBox deliveryAddress;
    private AddressEditBox returnAddress;
    private IconButton orderButton;
    private IconButton clearButton;
    private IconButton sendButton;
    private IconButton sortButton;

    private List<BigItemStack> filtered = List.of();
    private int seenStockVersion = -1;
    private String seenSearch = "";
    /** JEI's search text when it was last copied into ours. */
    private String seenJeiSearch = "";
    private int scrollRow;

    public LogisticsWorkbenchScreen(LogisticsWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WINDOW_WIDTH;
        imageHeight = BELOW_ORIGIN;
    }

    /** Height of the header and stock list, which sit above the origin. */
    private int aboveOrigin() {
        return AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER.getHeight() + stockRows * ROW_HEIGHT;
    }

    @Override
    protected void init() {
        // Only on first opening: init runs again whenever the window is resized.
        boolean opening = search == null;
        if (opening) {
            WorkbenchClientPackets.onScreenOpened(menu);
        }
        // As many stock rows as fit, like the Stock Keeper's own window.
        int fixed = AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER.getHeight() + BELOW_ORIGIN;
        stockRows = Math.max(MIN_STOCK_ROWS, Math.min(MAX_STOCK_ROWS, (height - 10 - fixed) / ROW_HEIGHT));

        super.init();
        // Centre everything: the buffer panel on the left, and the header and stock list above the origin.
        int visibleLeft = PANEL_X;
        leftPos = (width - (FRAME_RIGHT - visibleLeft)) / 2 - visibleLeft;
        topPos = (height - (aboveOrigin() + BELOW_ORIGIN)) / 2 + aboveOrigin();

        int headerY = topPos - aboveOrigin();
        search = new EditBox(new NoShadowFontWrapper(font), leftPos + 71, headerY + 22, 100, 9,
                Component.translatable("refinedcreation.workbench.search"));
        search.setMaxLength(50);
        search.setBordered(false);
        search.setTextColor(COLOR_SEARCH);
        search.setValue(seenSearch);
        addRenderableWidget(search);
        // As the Stock Keeper does: take JEI's search, and on opening, give JEI ours.
        pullSearchFromJei();
        if (opening) {
            pushSearchToJei();
        }

        deliveryAddress = addressBox(leftPos + TAG_X + 3, topPos + DELIVERY_Y + 3, TAG_WIDTH - 6,
                menu.getDeliveryAddress(), false);
        returnAddress = addressBox(leftPos + TAG_X + 3, topPos + RETURN_Y + 3, TAG_WIDTH - 6,
                menu.getReturnAddress(), true);

        // Left-click cycles the sort mode; right-click (in mouseClicked) reverses the order.
        sortButton = new WoodButton(leftPos + SORT_BUTTON_X, headerY + SORT_BUTTON_Y,
                LogisticsWorkbenchScreen::renderSortIcon);
        sortButton.withCallback(() -> {
            ClientConfig.WorkbenchSort[] modes = ClientConfig.WorkbenchSort.values();
            ClientConfig.WORKBENCH_SORT.set(modes[(sortMode().ordinal() + 1) % modes.length]);
            ClientConfig.WORKBENCH_SORT.save();
            updateSortButton();
            refilter();
        });
        addRenderableWidget(sortButton);
        updateSortButton();

        orderButton = new WoodButton(leftPos + BUTTONS_X, topPos + ORDER_Y, WoodButton.PLUS);
        orderButton.withCallback(() -> sendAction(WorkbenchPackets.Action.ORDER_PATTERN, orderAmount()));
        orderButton.setToolTip(Component.translatable("refinedcreation.workbench.order"));
        orderButton.getToolTip().add(Component.translatable("refinedcreation.workbench.order.tooltip")
                .withStyle(ChatFormatting.GRAY));
        addRenderableWidget(orderButton);

        clearButton = new WoodButton(leftPos + BUTTONS_X, topPos + CLEAR_Y, WoodButton.BIN);
        clearButton.withCallback(() -> sendAction(WorkbenchPackets.Action.CLEAR_GRID, 0));
        clearButton.setToolTip(Component.translatable("refinedcreation.workbench.clear"));
        clearButton.getToolTip().add(Component.translatable("refinedcreation.workbench.clear.tooltip")
                .withStyle(ChatFormatting.GRAY));
        addRenderableWidget(clearButton);

        sendButton = new WoodButton(leftPos + BUTTONS_X, topPos + SEND_Y, WoodButton.SEND);
        sendButton.withCallback(() -> sendAction(WorkbenchPackets.Action.SEND_RETURNS, 0));
        sendButton.setToolTip(Component.translatable("refinedcreation.workbench.send"));
        sendButton.getToolTip().add(Component.translatable("refinedcreation.workbench.send.tooltip")
                .withStyle(ChatFormatting.GRAY));
        addRenderableWidget(sendButton);
    }

    private AddressEditBox addressBox(int x, int y, int width, String value, boolean isReturn) {
        // Create's address box: suggests known addresses and accepts clipboards, like the Stock Keeper's.
        AddressEditBox box = new AddressEditBox(this, new NoShadowFontWrapper(font), x, y, width, 10, true);
        box.setMaxLength(LogisticsWorkbenchBlockEntity.MAX_ADDRESS_LENGTH);
        box.setTextColor(COLOR_TITLE);
        box.setValue(value);
        // Set after the initial value so opening the screen doesn't echo the address back to the server.
        box.setResponder(text -> PacketDistributor.sendToServer(
                new AddressPayload(menu.containerId, isReturn, text)));
        return addRenderableWidget(box);
    }

    private static int orderAmount() {
        if (hasControlDown()) {
            return 64;
        }
        return hasShiftDown() ? 16 : 1;
    }

    private void sendAction(WorkbenchPackets.Action action, int amount) {
        PacketDistributor.sendToServer(new ActionPayload(menu.containerId, action, amount));
    }

    // --- Stock list ------------------------------------------------------------------------------------------

    @Override
    protected void containerTick() {
        super.containerTick();
        deliveryAddress.tick();
        returnAddress.tick();
        if (JEI_LOADED && JeiSearchSync.isTyping() && !seenJeiSearch.equals(JeiSearchSync.getFilterText())) {
            pullSearchFromJei();
        }
        String query = search.getValue();
        if (menu.getStockVersion() != seenStockVersion || !query.equals(seenSearch)) {
            if (!query.equals(seenSearch)) {
                scrollRow = 0;
                pushSearchToJei();
            }
            seenStockVersion = menu.getStockVersion();
            seenSearch = query;
            refilter();
        }
    }

    private void refilter() {
        String query = seenSearch.toLowerCase(Locale.ROOT).strip();
        List<BigItemStack> result = new ArrayList<>();
        for (BigItemStack entry : menu.getStock()) {
            if (query.isEmpty() || entry.stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(entry);
            }
        }
        result.sort(sortComparator());
        filtered = result;
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    // --- JEI search sync -------------------------------------------------------------------------------------

    /**
     * Copies JEI's search into ours, if Create's search sync setting allows it.
     */
    private void pullSearchFromJei() {
        if (!JEI_LOADED || !syncMode().isBothOr(SearchSyncMode.SYNC_FROM_JEI)) {
            return;
        }
        String text = JeiSearchSync.getFilterText();
        if (text != null) {
            seenJeiSearch = text;
            search.setValue(text);
        }
    }

    /**
     * Copies our search into JEI's, if Create's search sync setting allows it.
     */
    private void pushSearchToJei() {
        if (JEI_LOADED && syncMode().isBothOr(SearchSyncMode.SYNC_FROM_STOCK_KEEPER)) {
            JeiSearchSync.setFilterText(search.getValue());
        }
    }

    /**
     * The workbench shares the Stock Keeper's search sync setting, so one button sets both.
     */
    private static SearchSyncMode syncMode() {
        return AllConfigs.client().syncRecipeViewerSearch.get();
    }

    private boolean isOverSyncButton(double mouseX, double mouseY) {
        int x = leftPos + SYNC_BUTTON_X;
        int y = topPos - aboveOrigin() + SYNC_BUTTON_Y;
        return JEI_LOADED && mouseX >= x && mouseX < x + SYNC_BUTTON_SIZE && mouseY >= y
                && mouseY < y + SYNC_BUTTON_SIZE + 1;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
    }

    // --- Sorting ----------------------------------------------------------------------------------------------

    private static ClientConfig.WorkbenchSort sortMode() {
        return ClientConfig.WORKBENCH_SORT.get();
    }

    private static boolean sortDescending() {
        return ClientConfig.WORKBENCH_SORT_DESCENDING.get();
    }

    /**
     * The order for the stock list, as in a Refined Storage grid. Ties fall back to the name and then the item
     * ID, so the order is stable from one sync to the next.
     */
    private static Comparator<BigItemStack> sortComparator() {
        Comparator<BigItemStack> byName = Comparator.comparing(
                (BigItemStack entry) -> entry.stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        Comparator<BigItemStack> byId = Comparator.comparing(
                (BigItemStack entry) -> BuiltInRegistries.ITEM.getKey(entry.stack.getItem()).toString());
        Comparator<BigItemStack> primary = switch (sortMode()) {
            case QUANTITY -> Comparator.comparingInt((BigItemStack entry) -> entry.count);
            case NAME -> byName;
            case ID -> byId;
        };
        if (sortDescending()) {
            primary = primary.reversed();
        }
        return primary.thenComparing(byName).thenComparing(byId);
    }

    private void updateSortButton() {
        String mode = sortMode().name().toLowerCase(Locale.ROOT);
        sortButton.setToolTip(Component.translatable("refinedcreation.workbench.sort",
                Component.translatable("refinedcreation.workbench.sort." + mode),
                Component.translatable(sortDescending()
                        ? "refinedcreation.workbench.sort.descending" : "refinedcreation.workbench.sort.ascending")));
        sortButton.getToolTip().add(Component.translatable("refinedcreation.workbench.sort.tooltip")
                .withStyle(ChatFormatting.GRAY));
        sortButton.getToolTip().add(Component.translatable("refinedcreation.workbench.sort.direction.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * The sort button's glyph: a short label for the mode, and a small arrow under it for the direction.
     */
    private static void renderSortIcon(GuiGraphics graphics, int x, int y) {
        String label = switch (sortMode()) {
            case QUANTITY -> "64";
            case NAME -> "Az";
            case ID -> "ID";
        };
        Font font = Minecraft.getInstance().font;
        // Text widths include a trailing pixel of spacing.
        graphics.drawString(font, label, x + (WoodButton.SIZE - font.width(label) + 1) / 2, y + 2,
                WoodButton.ARGB_INK, false);
        // A 5px triangle: pointing down for descending, up for ascending.
        boolean descending = sortDescending();
        for (int row = 0; row < 3; row++) {
            int half = descending ? 2 - row : row;
            graphics.fill(x + 7 - half, y + 10 + row, x + 8 + half, y + 11 + row, WoodButton.ARGB_INK);
        }
    }

    private int maxScroll() {
        int rows = (filtered.size() + STOCK_COLS - 1) / STOCK_COLS;
        return Math.max(0, rows - stockRows);
    }

    private int stockTop() {
        return topPos - stockRows * ROW_HEIGHT;
    }

    /**
     * @return the stock entry under the mouse, or {@code null}
     */
    @Nullable
    private BigItemStack stockAt(double mouseX, double mouseY) {
        int x = (int) mouseX - leftPos - ITEMS_X;
        int y = (int) mouseY - stockTop() - 1;
        if (x < 0 || y < 0 || x >= STOCK_COLS * COL_WIDTH || y >= stockRows * ROW_HEIGHT) {
            return null;
        }
        int index = (scrollRow + y / ROW_HEIGHT) * STOCK_COLS + x / COL_WIDTH;
        return index < filtered.size() ? filtered.get(index) : null;
    }

    // --- Input -----------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicking anywhere else releases a text field, so inventory keys work again.
        for (EditBox box : new EditBox[] {search, deliveryAddress, returnAddress}) {
            if (box.isFocused() && !box.isMouseOver(mouseX, mouseY)) {
                box.setFocused(false);
                if (getFocused() == box) {
                    setFocused(null);
                }
            }
        }
        // Right-clicking the search bar clears and selects it, as in the Stock Keeper.
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && search.isMouseOver(mouseX, mouseY)) {
            search.setValue("");
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && sortButton.isMouseOver(mouseX, mouseY)) {
            ClientConfig.WORKBENCH_SORT_DESCENDING.set(!sortDescending());
            ClientConfig.WORKBENCH_SORT_DESCENDING.save();
            updateSortButton();
            refilter();
            playClick();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverSyncButton(mouseX, mouseY)) {
            SearchSyncMode.cycleConfig();
            pushSearchToJei();
            playClick();
            return true;
        }
        BigItemStack entry = stockAt(mouseX, mouseY);
        if (entry != null && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            int count = hasShiftDown() ? entry.stack.getMaxStackSize() : 1;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                count = Math.max(1, entry.stack.getMaxStackSize() / 2);
            }
            PacketDistributor.sendToServer(new RequestPayload(menu.containerId, entry.stack.copyWithCount(1), count));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - stockTop();
        if (x >= FRAME_LEFT && x < FRAME_RIGHT && y >= 0 && y < stockRows * ROW_HEIGHT) {
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While typing, keep keys like E (inventory) and number keys (hotbar swaps) out of the container.
        if (getFocused() instanceof EditBox box && box.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * The window isn't a rectangle. It covers the header, stock list and crafting section, the footer, the
     * inventory frame and the buffer panel.
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        double x = mouseX - guiLeft;
        double y = mouseY - guiTop;
        boolean inWindow = x >= FRAME_LEFT && x < FRAME_RIGHT && y >= -aboveOrigin() && y < FOOTER_Y + FOOTER_HEIGHT;
        boolean inInventory = x >= INVENTORY_X && x < INVENTORY_X + 176 && y >= INVENTORY_Y && y < BELOW_ORIGIN;
        boolean inPanel = x >= PANEL_X && x < PANEL_X + PANEL_WIDTH && y >= PANEL_Y && y < PANEL_Y + PANEL_HEIGHT;
        return !inWindow && !inInventory && !inPanel;
    }

    /**
     * @return the parts of the GUI outside {@code imageWidth} x {@code imageHeight}, for JEI's exclusion zones
     */
    public List<Rect2i> getExtraAreas() {
        return List.of(
                new Rect2i(leftPos + TEXTURE_X + 16, topPos - aboveOrigin(), 224, aboveOrigin()),
                new Rect2i(leftPos + PANEL_X, topPos + PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT));
    }

    // --- Rendering -------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderStock(graphics, mouseX, mouseY);
        renderStatus(graphics);
        renderTooltip(graphics, mouseX, mouseY);
        renderStockTooltip(graphics, mouseX, mouseY);
        renderWidgetTooltips(graphics, mouseX, mouseY);
        renderAreaHints(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int textureX = x + TEXTURE_X;

        // Header, wooden body (stock rows + crafting rows), footer: the Stock Keeper's window.
        int headerY = y - aboveOrigin();
        AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER.render(graphics, textureX, headerY);
        int bodyY = headerY + AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER.getHeight();
        for (int row = 0; row < stockRows + CRAFT_ROWS; row++) {
            AllGuiTextures.STOCK_KEEPER_REQUEST_BODY.render(graphics, textureX, bodyY + row * ROW_HEIGHT);
        }
        renderFooter(graphics, textureX, y + FOOTER_Y);
        if (JEI_LOADED) {
            syncMode().buttonTexture.render(graphics, x + SYNC_BUTTON_X, headerY + SYNC_BUTTON_Y);
        }

        // Title on the banner, and the search hint.
        graphics.drawString(font, title, x + WINDOW_WIDTH / 2 - font.width(title) / 2, headerY + 4, COLOR_TITLE,
                false);
        if (search.getValue().isEmpty() && !search.isFocused()) {
            Component hint = Component.translatable("refinedcreation.workbench.search");
            graphics.drawString(font, hint, x + WINDOW_WIDTH / 2 - font.width(hint) / 2, search.getY(),
                    COLOR_SEARCH, false);
        }

        // A groove between the stock list and the crafting section.
        graphics.fill(x + FRAME_LEFT + 9, y - 1, x + FRAME_RIGHT - 9, y, ARGB_WOOD_DARK);
        graphics.fill(x + FRAME_LEFT + 9, y, x + FRAME_RIGHT - 9, y + 1, 0xFF926651);

        renderScrollbar(graphics);

        // Crafting section: grid, arrow, result, delivery address.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics,
                        x + LogisticsWorkbenchMenu.GRID_X - 1 + col * 18, y + LogisticsWorkbenchMenu.GRID_Y - 1 + row * 18);
            }
        }
        // The head of Create's JEI arrow, which is short enough to sit between the grid and the result.
        AllGuiTextures arrow = AllGuiTextures.JEI_ARROW;
        graphics.blit(arrow.location, x + 80, y + LogisticsWorkbenchMenu.RESULT_Y + 3, arrow.getStartX() + 18,
                arrow.getStartY(), 24, arrow.getHeight());
        int resultX = x + LogisticsWorkbenchMenu.RESULT_X - 1;
        int resultY = y + LogisticsWorkbenchMenu.RESULT_Y - 1;
        graphics.fill(resultX - 2, resultY - 2, resultX + 20, resultY + 20, ARGB_BLACK);
        graphics.fill(resultX - 1, resultY - 1, resultX + 19, resultY + 19, 0xFFB59370);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, resultX, resultY);
        tag(graphics, x + TAG_X, y + DELIVERY_Y, TAG_WIDTH, TAG_HEIGHT);
        tag(graphics, x + TAG_X, y + RETURN_Y, TAG_WIDTH, TAG_HEIGHT);
        if (deliveryAddress.getValue().isEmpty() && !deliveryAddress.isFocused()) {
            graphics.drawString(font, Component.translatable("refinedcreation.workbench.delivery_address")
                    .withStyle(ChatFormatting.ITALIC), deliveryAddress.getX(), deliveryAddress.getY(),
                    COLOR_HINT, false);
        }

        if (returnAddress.getValue().isEmpty() && !returnAddress.isFocused()) {
            graphics.drawString(font, Component.translatable("refinedcreation.workbench.return_address")
                    .withStyle(ChatFormatting.ITALIC), returnAddress.getX(), returnAddress.getY(), COLOR_HINT, false);
        }

        // Buffer panel and the player's inventory.
        renderBufferPanel(graphics, x, y);
        renderInventoryFrame(graphics, x + INVENTORY_X, y + INVENTORY_Y);
    }

    private static void renderFooter(GuiGraphics graphics, int x, int y) {
        AllGuiTextures footer = AllGuiTextures.STOCK_KEEPER_REQUEST_FOOTER;
        int drawY = y;
        for (int[] slice : FOOTER_SLICES) {
            graphics.blit(footer.location, x + FOOTER_SKIP_COLUMNS, drawY,
                    footer.getStartX() + FOOTER_SKIP_COLUMNS, footer.getStartY() + slice[0],
                    footer.getWidth() - FOOTER_SKIP_COLUMNS, slice[1]);
            drawY += slice[1];
        }
        // The address tag's rope also crosses the window's left frame on rows 34-38. Repaint the frame
        // there (columns 24-31) from the plain row above.
        for (int row = 34; row <= 38; row++) {
            graphics.blit(footer.location, x + FOOTER_SKIP_COLUMNS, y + row, footer.getStartX() + FOOTER_SKIP_COLUMNS,
                    footer.getStartY() + 33, 8, 1);
        }
        // The shelf has two slanted nail marks at its centre (texture x 123-132, rows 31-32), which would sit
        // right under the 5th return slot and look like a glitch. Paint them over with the shelf's wood stripes.
        graphics.fill(x + 123, y + 31, x + 133, y + 32, 0xFF926651);
        graphics.fill(x + 123, y + 32, x + 133, y + 33, ARGB_WOOD);
    }

    /**
     * Draws Create's inventory frame without the downward pointer under its hotbar, which the workbench has no
     * use for. The pointer hangs below the frame (rows 100-107) and its base also runs through the frame's
     * bottom edge (rows 97-99, columns 44-59). There, the edge is rebuilt from a plain column of it.
     */
    private static void renderInventoryFrame(GuiGraphics graphics, int x, int y) {
        AllGuiTextures inventory = AllGuiTextures.PLAYER_INVENTORY;
        ResourceLocation texture = inventory.location;
        int u = inventory.getStartX();
        int v = inventory.getStartY();
        int edgeTop = 97;
        int edgeHeight = INVENTORY_FRAME_HEIGHT - edgeTop;
        int pointerLeft = 44;
        int pointerRight = 60;
        int plainColumn = 40;
        graphics.blit(texture, x, y, u, v, inventory.getWidth(), edgeTop);
        graphics.blit(texture, x, y + edgeTop, u, v + edgeTop, pointerLeft, edgeHeight);
        for (int column = pointerLeft; column < pointerRight; column++) {
            graphics.blit(texture, x + column, y + edgeTop, u + plainColumn, v + edgeTop, 1, edgeHeight);
        }
        graphics.blit(texture, x + pointerRight, y + edgeTop, u + pointerRight, v + edgeTop,
                inventory.getWidth() - pointerRight, edgeHeight);
    }

    private void renderBufferPanel(GuiGraphics graphics, int x, int y) {
        int left = x + PANEL_X;
        int top = y + PANEL_Y;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        for (int i = 0; i < FRAME_COLORS.length; i++) {
            graphics.fill(left + i, top + i, right - i, bottom - i, FRAME_COLORS[i]);
        }
        graphics.fill(left + BORDER, top + BORDER, right - BORDER, bottom - BORDER, ARGB_WOOD);
        // The darker inner trim the Stock Keeper's wood has along its sides.
        graphics.fill(left + BORDER + 1, top + BORDER, left + BORDER + 2, bottom - BORDER, ARGB_WOOD_DARK);
        graphics.fill(right - BORDER - 2, top + BORDER, right - BORDER - 1, bottom - BORDER, ARGB_WOOD_DARK);

        int rows = LogisticsWorkbenchBlockEntity.BUFFER_SLOTS / LogisticsWorkbenchMenu.BUFFER_COLS;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < LogisticsWorkbenchMenu.BUFFER_COLS; col++) {
                AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics,
                        x + LogisticsWorkbenchMenu.BUFFER_X - 1 + col * 18,
                        y + LogisticsWorkbenchMenu.BUFFER_Y - 1 + row * 18);
            }
        }

        // Title on a banner across the top edge, like the main window's header.
        Component label = Component.translatable("refinedcreation.workbench.buffer");
        int textWidth = font.width(label);
        int bannerX = left + PANEL_WIDTH / 2 - (textWidth + 10) / 2;
        int bannerY = top + 1;
        AllGuiTextures bannerMiddle = AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_M;
        AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_L.render(graphics, bannerX - 8, bannerY - 4);
        graphics.blit(bannerMiddle.location, bannerX, bannerY - 4, textWidth + 10, bannerMiddle.getHeight(),
                bannerMiddle.getStartX(), bannerMiddle.getStartY(), bannerMiddle.getWidth(),
                bannerMiddle.getHeight(), 256, 256);
        AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_R.render(graphics, bannerX + textWidth + 10, bannerY - 4);
        graphics.drawString(font, label, bannerX + 5, bannerY, COLOR_TITLE, false);
    }

    /**
     * A cream address tag like the Stock Keeper's, sized to fit.
     */
    private static void tag(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ARGB_BLACK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, ARGB_CREAM);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, ARGB_CREAM_SHADE);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, ARGB_CREAM_SHADE);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int max = maxScroll();
        if (max == 0) {
            return;
        }
        int trackHeight = stockRows * ROW_HEIGHT - 4;
        int totalRows = max + stockRows;
        int barSize = Math.max(5, trackHeight * stockRows / totalRows);
        int barX = leftPos + ITEMS_X + STOCK_COLS * COL_WIDTH;
        int barY = stockTop() + 2 + (trackHeight - barSize) * scrollRow / max;
        AllGuiTextures pad = AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD;
        graphics.blit(pad.location, barX, barY, pad.getWidth(), barSize, pad.getStartX(), pad.getStartY(),
                pad.getWidth(), pad.getHeight(), 256, 256);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(graphics, barX, barY);
        if (barSize > 16) {
            AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(graphics, barX, barY + barSize / 2 - 4);
        }
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(graphics, barX, barY + barSize - 5);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, INVENTORY_X + 8, INVENTORY_Y + 6, COLOR_INVENTORY_LABEL,
                false);
    }

    private void renderStock(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.isTuned() || filtered.isEmpty()) {
            Component message = Component.translatable(menu.isTuned()
                    ? "refinedcreation.workbench.empty" : "refinedcreation.workbench.untuned");
            int centreX = leftPos + WINDOW_WIDTH / 2;
            int centreY = stockTop() + stockRows * ROW_HEIGHT / 2 - 4;
            List<FormattedCharSequence> lines = font.split(message, 160);
            for (int i = 0; i < lines.size(); i++) {
                int lineX = centreX - font.width(lines.get(i)) / 2;
                int lineY = centreY + (i - lines.size() / 2) * (font.lineHeight + 1);
                graphics.drawString(font, lines.get(i), lineX + 1, lineY + 1, 0x4A2D31, false);
                graphics.drawString(font, lines.get(i), lineX, lineY, COLOR_CREAM, false);
            }
            return;
        }
        BigItemStack hovered = stockAt(mouseX, mouseY);
        PoseStack pose = graphics.pose();
        for (int row = 0; row < stockRows; row++) {
            for (int col = 0; col < STOCK_COLS; col++) {
                int index = (scrollRow + row) * STOCK_COLS + col;
                if (index >= filtered.size()) {
                    return;
                }
                BigItemStack entry = filtered.get(index);
                // An 18px slot centred in each 20px cell, with the item 1px inside it.
                int cellX = leftPos + ITEMS_X + col * COL_WIDTH;
                int cellY = stockTop() + row * ROW_HEIGHT + 1;
                AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, cellX, cellY);

                // Hovered items grow slightly, as in the Stock Keeper.
                pose.pushPose();
                pose.translate(cellX + 9, cellY + 9, 0);
                if (entry == hovered) {
                    pose.scale(1.075f, 1.075f, 1);
                }
                graphics.renderItem(entry.stack, -8, -8);
                pose.popPose();

                pose.pushPose();
                pose.translate(cellX, cellY, 200);
                graphics.renderItemDecorations(font, entry.stack, 1, 1, "");
                drawCount(graphics, entry.count);
                pose.popPose();
            }
        }
    }

    /**
     * Draws a count in Create's small number font at the bottom-right of an 18px cell, formatted the way the
     * Stock Keeper does.
     */
    private static void drawCount(GuiGraphics graphics, int count) {
        if (count <= 1) {
            return;
        }
        String text = count >= BigItemStack.INF ? "+"
                : count >= 1_000_000 ? (count / 1_000_000) + "m"
                : count >= 10_000 ? (count / 1000) + "k"
                : count >= 1000 ? ((count * 10) / 1000) / 10f + "k"
                : Integer.toString(count);

        AllGuiTextures numbers = AllGuiTextures.NUMBERS;
        int x = (int) Math.floor(-text.length() * 2.5);
        RenderSystem.enableBlend();
        for (char c : text.toCharArray()) {
            int u = (c - '0') * 6;
            int width = numbers.getWidth();
            switch (c) {
                case '.' -> {
                    width = 3;
                    u = 60;
                }
                case 'k' -> u = 64;
                case 'm' -> {
                    width = 7;
                    u = 70;
                }
                case '+' -> {
                    width = 9;
                    u = 84;
                }
                default -> {
                }
            }
            graphics.blit(STOCK_KEEPER, 14 + x, 10, 0, numbers.getStartX() + u, numbers.getStartY(), width,
                    numbers.getHeight(), 256, 256);
            x += width - 1;
        }
    }

    private void renderStockTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        BigItemStack entry = stockAt(mouseX, mouseY);
        if (entry == null || !menu.getCarried().isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(entry.stack));
        lines.add(Component.translatable("refinedcreation.workbench.available",
                entry.count >= BigItemStack.INF
                        ? Component.translatable("refinedcreation.workbench.craftable")
                        : Component.literal(Integer.toString(entry.count))).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("refinedcreation.workbench.stock.tooltip").withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * Create's icon buttons keep their own tooltips, which only Create's screen base class draws.
     */
    private void renderWidgetTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (IconButton button : new IconButton[] {orderButton, clearButton, sendButton, sortButton}) {
            if (button.isHovered() && !button.getToolTip().isEmpty()) {
                graphics.renderComponentTooltip(font, button.getToolTip(), mouseX, mouseY);
            }
        }
        // Create's own tooltip for its search sync button.
        if (isOverSyncButton(mouseX, mouseY)) {
            String key = "create.gui.stock_keeper.jei_sync." + syncMode().getSerializedName();
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable(key),
                    Component.translatable(key + ".description").withStyle(ChatFormatting.GRAY),
                    Component.translatable("create.gui.stock_keeper.click_to_cycle")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)), mouseX, mouseY);
        }
    }

    /**
     * Explains each slot area when hovering one of its empty slots.
     */
    private void renderAreaHints(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot == null || hoveredSlot.hasItem() || !menu.getCarried().isEmpty()) {
            return;
        }
        int index = hoveredSlot.index;
        String key = null;
        if (index >= LogisticsWorkbenchMenu.BUFFER_START && index < LogisticsWorkbenchMenu.RETURN_START) {
            key = "refinedcreation.workbench.buffer.tooltip";
        } else if (index >= LogisticsWorkbenchMenu.RETURN_START && index < LogisticsWorkbenchMenu.PLAYER_START) {
            key = "refinedcreation.workbench.returns.tooltip";
        } else if (index > LogisticsWorkbenchMenu.RESULT_SLOT && index < LogisticsWorkbenchMenu.BUFFER_START) {
            key = "refinedcreation.workbench.grid.tooltip";
        }
        if (key != null) {
            graphics.renderTooltip(font, font.split(Component.translatable(key), 160), mouseX, mouseY);
        }
    }

    /**
     * Shows the latest status on a Stock Keeper banner below the return shelf.
     */
    private void renderStatus(GuiGraphics graphics) {
        Component status = menu.getStatus(STATUS_MILLIS);
        if (status == null) {
            return;
        }
        int textWidth = font.width(status);
        int msgX = leftPos + WINDOW_WIDTH / 2 - (textWidth + 10) / 2;
        // Across the window's bottom edge, under the return shelf, so it never hides the stock list. The
        // 16px banner straddles the gap between the window and the inventory frame.
        int msgY = topPos + FOOTER_Y + FOOTER_HEIGHT - 5;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        AllGuiTextures left = AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_L;
        AllGuiTextures middle = AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_M;
        left.render(graphics, msgX - 8, msgY - 4);
        graphics.blit(middle.location, msgX, msgY - 4, textWidth + 10, middle.getHeight(), middle.getStartX(),
                middle.getStartY(), middle.getWidth(), middle.getHeight(), 256, 256);
        AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_R.render(graphics, msgX + textWidth + 10, msgY - 4);
        // Errors keep their red; everything else uses the banner's brown.
        int color = status.getStyle().getColor() != null ? status.getStyle().getColor().getValue() : 0x8C5D4B;
        graphics.drawString(font, status, msgX + 5, msgY, color, false);
        pose.popPose();
    }
}
