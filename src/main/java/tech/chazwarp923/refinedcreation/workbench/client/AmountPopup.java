package tech.chazwarp923.refinedcreation.workbench.client;

import java.util.List;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import com.simibubi.create.content.trains.station.NoShadowFontWrapper;

import net.createmod.catnip.gui.element.ScreenElement;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A small window over the workbench screen that asks for a number: how many of an item to order, or how many
 * crafts to order ingredients for. It's drawn in the same stone-and-wood frame as the workbench's buffer panel.
 * <p>
 * While it's open, it takes every click, key and scroll. Enter or the tick orders, and Escape, the cross or a
 * click outside it cancels. The minus and plus buttons, and the scroll wheel, step by 1, by 16 with Shift and by
 * 64 with Ctrl, as the workbench's Order button does.
 */
final class AmountPopup {
    static final int WIDTH = 156;
    static final int HEIGHT = 84;

    private static final int MAX_DIGITS = 6;
    private static final int TEXT_LEFT = 12;
    private static final int ICON_SIZE = 16;
    private static final int TAG_X = 30;
    private static final int TAG_WIDTH = 96;
    private static final int ROW_AMOUNT = 36;
    private static final int ROW_BUTTONS = 57;

    private final Font font;
    private final int left;
    private final int top;
    private final Component title;
    private final Component subtitle;
    private final ItemStack icon;
    private final int max;
    private final IntConsumer onConfirm;

    private final EditBox amount;
    private final WoodButton minus;
    private final WoodButton plus;
    private final WoodButton confirm;
    private final WoodButton cancel;
    private boolean closed;

    /**
     * @param centreX   where to centre the popup
     * @param centreY   where to centre the popup
     * @param icon      shown beside the title; may be empty
     * @param initial   the starting amount
     * @param max       the largest amount that can be chosen, at least 1
     * @param onConfirm called with the chosen amount when the player orders
     */
    AmountPopup(Font font, int centreX, int centreY, Component title, Component subtitle, ItemStack icon,
                int initial, int max, IntConsumer onConfirm) {
        this.font = font;
        this.left = centreX - WIDTH / 2;
        this.top = centreY - HEIGHT / 2;
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.max = Math.max(1, max);
        this.onConfirm = onConfirm;

        amount = new EditBox(new NoShadowFontWrapper(font), left + TAG_X + 4, top + ROW_AMOUNT + 4, TAG_WIDTH - 8, 9,
                Component.translatable("refinedcreation.workbench.amount"));
        amount.setBordered(false);
        amount.setTextColor(LogisticsWorkbenchScreen.COLOR_TITLE);
        amount.setMaxLength(MAX_DIGITS);
        amount.setFilter(text -> text.chars().allMatch(Character::isDigit));
        amount.setValue(Integer.toString(clamp(initial)));
        // Typing past the limit snaps back to it, so what's shown is what gets ordered.
        amount.setResponder(text -> {
            if (parse(text) > this.max) {
                amount.setValue(Integer.toString(this.max));
            }
        });
        amount.setFocused(true);
        amount.moveCursorToEnd(false);

        minus = button(left + 12, top + ROW_AMOUNT, WoodButton.MINUS, "refinedcreation.workbench.amount.less",
                () -> step(-1));
        plus = button(left + 129, top + ROW_AMOUNT, WoodButton.PLUS, "refinedcreation.workbench.amount.more",
                () -> step(1));
        confirm = button(left + 111, top + ROW_BUTTONS, WoodButton.CHECK, "refinedcreation.workbench.amount.order",
                this::confirm);
        cancel = button(left + 129, top + ROW_BUTTONS, WoodButton.CROSS, "refinedcreation.workbench.amount.cancel",
                this::close);
    }

    private static WoodButton button(int x, int y, ScreenElement glyph,
                                     String tooltipKey, Runnable action) {
        WoodButton button = new WoodButton(x, y, glyph);
        button.withCallback(action);
        button.setToolTip(Component.translatable(tooltipKey));
        return button;
    }

    boolean isClosed() {
        return closed;
    }

    private int parse(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return max;
        }
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(max, value));
    }

    /**
     * @return how far one press of minus or plus moves, by the modifier keys held
     */
    private static int stepSize() {
        if (Screen.hasControlDown()) {
            return 64;
        }
        return Screen.hasShiftDown() ? 16 : 1;
    }

    private void step(int direction) {
        int current = parse(amount.getValue());
        // From 1, a step of 16 lands on 16 rather than 17, so the big steps stay on round numbers.
        int next = current == 1 && direction > 0 && stepSize() > 1 ? stepSize() : current + direction * stepSize();
        amount.setValue(Integer.toString(clamp(next)));
        amount.moveCursorToEnd(false);
    }

    private void confirm() {
        closed = true;
        onConfirm.accept(clamp(parse(amount.getValue())));
    }

    private void close() {
        closed = true;
    }

    private boolean isOver(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + WIDTH && mouseY >= top && mouseY < top + HEIGHT;
    }

    // --- Input -----------------------------------------------------------------------------------------------

    void mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOver(mouseX, mouseY)) {
            close();
            return;
        }
        for (WoodButton widget : List.of(minus, plus, confirm, cancel)) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
        amount.mouseClicked(mouseX, mouseY, button);
        amount.setFocused(true);
    }

    void mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (isOver(mouseX, mouseY) && scrollY != 0) {
            step(scrollY > 0 ? 1 : -1);
        }
    }

    void keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirm();
            case GLFW.GLFW_KEY_ESCAPE -> close();
            case GLFW.GLFW_KEY_UP -> step(1);
            case GLFW.GLFW_KEY_DOWN -> step(-1);
            default -> amount.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    void charTyped(char codePoint, int modifiers) {
        amount.charTyped(codePoint, modifiers);
    }

    // --- Rendering -------------------------------------------------------------------------------------------

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        // Above the stock list's items and their counts.
        graphics.pose().translate(0, 0, 400);

        LogisticsWorkbenchScreen.renderPanel(graphics, left, top, left + WIDTH, top + HEIGHT);

        int textX = left + TEXT_LEFT;
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, textX, top + 12);
            textX += ICON_SIZE + 4;
        }
        int textWidth = left + WIDTH - TEXT_LEFT - textX;
        drawShadowed(graphics, title, textX, top + 12, textWidth, LogisticsWorkbenchScreen.COLOR_CREAM);
        drawShadowed(graphics, subtitle, textX, top + 22, textWidth, LogisticsWorkbenchScreen.COLOR_HINT);

        LogisticsWorkbenchScreen.tag(graphics, left + TAG_X, top + ROW_AMOUNT, TAG_WIDTH, 15);
        amount.render(graphics, mouseX, mouseY, partialTick);
        for (WoodButton widget : List.of(minus, plus, confirm, cancel)) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        Component hint = Component.translatable("refinedcreation.workbench.amount.hint");
        drawShadowed(graphics, hint, left + TEXT_LEFT, top + ROW_BUTTONS + 4, 96,
                LogisticsWorkbenchScreen.COLOR_HINT);

        for (WoodButton widget : List.of(minus, plus, confirm, cancel)) {
            if (widget.isHovered()) {
                graphics.renderComponentTooltip(font, List.of(widget.getToolTip().get(0).copy()
                        .withStyle(ChatFormatting.WHITE)), mouseX, mouseY);
            }
        }
        graphics.pose().popPose();
    }

    /**
     * Draws a line of cream text with the workbench's dark shadow, cut short with "..." if it's too wide.
     */
    private void drawShadowed(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        String line = text.getString();
        if (font.width(line) > maxWidth) {
            line = font.plainSubstrByWidth(line, maxWidth - font.width("...")) + "...";
        }
        graphics.drawString(font, line, x + 1, y + 1, LogisticsWorkbenchScreen.COLOR_SEARCH, false);
        graphics.drawString(font, line, x, y, color, false);
    }
}
