package tech.chazwarp923.refinedcreation.workbench.client;

import com.simibubi.create.AllKeys;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A button in the style of the Stock Keeper's own (its lock and search sync buttons): a 15px wooden tile with
 * a dark brown glyph, rather than Create's grey widget button.
 * <p>
 * The tile is drawn from the same colours as the Stock Keeper's texture, so it needs no texture of its own.
 * Glyphs are pixel maps, see {@link #glyph(String...)}.
 */
public class WoodButton extends IconButton {
    public static final int SIZE = 15;

    /** Glyph colours: the Stock Keeper's dark brown lines and cream fill. */
    static final int ARGB_INK = 0xFF4A2D31;
    static final int ARGB_INK_LIGHT = 0xFFB59370;

    private static final int ARGB_EDGE_LIGHT = 0xFFA27C60;
    private static final int ARGB_CORNER = 0xFF986E56;
    private static final int ARGB_WOOD = 0xFF8C5D4B;
    private static final int ARGB_GRAIN = 0xFF875A4D;
    private static final int ARGB_EDGE_DARK = 0xFF714A40;
    private static final int ARGB_SHADE = 0xFF603D39;

    /** A plus: order more. */
    public static final ScreenElement PLUS = glyph(
            "...GGG...",
            "...GIG...",
            "...GIG...",
            "GGGGIGGGG",
            "GIIIIIIIG",
            "GGGGIGGGG",
            "...GIG...",
            "...GIG...",
            "...GGG...");

    /** A bin: clear the grid. */
    public static final ScreenElement BIN = glyph(
            "...GGG...",
            "GGGGGGGGG",
            "GIIIIIIIG",
            "GGGGGGGGG",
            ".GIGIGIG.",
            ".GIGIGIG.",
            ".GIGIGIG.",
            ".GIGIGIG.",
            ".GGGGGGG.");

    /** An arrow: send the returns. */
    public static final ScreenElement SEND = glyph(
            "....G....",
            "....GG...",
            "GGGGGIG..",
            "GIIIIIIG.",
            "GIIIIIIIG",
            "GIIIIIIG.",
            "GGGGGIG..",
            "....GG...",
            "....G....");

    public WoodButton(int x, int y, ScreenElement glyph) {
        super(x, y, SIZE, SIZE, glyph);
    }

    @Override
    public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
        boolean pressed = isHovered && AllKeys.isMouseButtonDown(0);
        renderTile(graphics, getX(), getY());
        if (isHovered) {
            // Lighten the face on hover, darken it while pressed.
            graphics.fill(getX() + 1, getY() + 1, getX() + 14, getY() + 14, pressed ? 0x30000000 : 0x30FFFFFF);
        }
        icon.render(graphics, getX(), getY() + (pressed ? 1 : 0));
    }

    /**
     * The Stock Keeper's blank wooden tile: a light top-left edge, a shaded bottom-right edge and grain stripes.
     */
    static void renderTile(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 1, y, x + 13, y + 1, ARGB_EDGE_LIGHT);
        graphics.fill(x, y + 1, x + 1, y + 13, ARGB_EDGE_LIGHT);
        graphics.fill(x + 13, y, x + 14, y + 1, ARGB_CORNER);
        graphics.fill(x, y + 13, x + 1, y + 14, ARGB_CORNER);
        graphics.fill(x + 1, y + 1, x + 14, y + 14, ARGB_WOOD);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, ARGB_CORNER);
        for (int stripe = 2; stripe <= 10; stripe += 4) {
            graphics.fill(x + 2, y + stripe, x + 13, y + stripe + 2, ARGB_GRAIN);
        }
        graphics.fill(x + 14, y + 1, x + 15, y + 2, ARGB_EDGE_DARK);
        graphics.fill(x + 14, y + 2, x + 15, y + 13, ARGB_SHADE);
        graphics.fill(x + 14, y + 13, x + 15, y + 14, ARGB_INK);
        graphics.fill(x + 1, y + 14, x + 2, y + 15, ARGB_SHADE);
        graphics.fill(x + 2, y + 14, x + 14, y + 15, ARGB_INK);
    }

    /**
     * A 9x9 glyph centred on the tile. {@code G} is a dark line, {@code I} a cream fill, anything else is clear.
     */
    static ScreenElement glyph(String... rows) {
        return (graphics, x, y) -> {
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < rows[row].length(); col++) {
                    char c = rows[row].charAt(col);
                    if (c == 'G' || c == 'I') {
                        int px = x + 3 + col;
                        int py = y + 3 + row;
                        graphics.fill(px, py, px + 1, py + 1, c == 'G' ? ARGB_INK : ARGB_INK_LIGHT);
                    }
                }
            }
        };
    }
}
