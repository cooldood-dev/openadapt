package dev.adapt.v3.ui.theme;

import dev.adapt.v3.ui.render.Render2D;
import dev.adapt.v3.util.render.ColorUtility;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// The flat OldSchool look: square, opaque, no effects. Palette taken from classic clickgui clients.
public final class OldSchoolStyle {
    public static final int PANEL_BG = 0xE8141925;
    public static final int HEADER_BG = 0xFF4B5874;
    public static final int HOVER = 0x16FFFFFF;
    public static final int TEXT_ON = 0xFFFFFFFF;
    public static final int TEXT_OFF = 0xFF7F889C;
    public static final int SETTING = 0xFFA9B1C2;
    public static final int SETTING_DIM = 0xFF6B7386;
    public static final int TRACK = 0xFF2C3446;
    public static final int LIST_ROW_BG = 0x8C000000;
    public static final float HEADER_HEIGHT = 18.0F;
    public static final float ROW_HEIGHT = 18.0F;
    public static final float ENUM_OPTION_HEIGHT = 13.0F;
    // enums with more values than this keep the dropdown instead of listing every option inline
    public static final int MAX_INLINE_ENUM = 8;

    public static boolean active() {
        return !Theme.isModern();
    }

    public static int accent() {
        return Theme.getAccentColor() | 0xFF000000;
    }

    public static void panel(GuiGraphicsExtractor graphics, float x, float y, float width, float height, float alpha) {
        Render2D.fillRect(graphics, x, y, width, height, ColorUtility.scaleAlpha(PANEL_BG, alpha));
    }

    public static void checkbox(GuiGraphicsExtractor graphics, float x, float y, float size, float on, float alpha) {
        Render2D.fillRect(graphics, x, y, size, size, ColorUtility.scaleAlpha(TRACK, alpha));
        if (on > 0.01F) {
            Render2D.fillRect(graphics, x + 1.0F, y + 1.0F, size - 2.0F, size - 2.0F, ColorUtility.scaleAlpha(accent(), alpha * on));
        }
    }

    public static void slider(GuiGraphicsExtractor graphics, float x, float y, float width, float normalized, float alpha) {
        Render2D.fillRect(graphics, x, y, width, 1.0F, ColorUtility.scaleAlpha(TRACK, alpha));
        float filled = Math.max(0.0F, Math.min(1.0F, normalized)) * width;
        if (filled > 0.0F) {
            Render2D.fillRect(graphics, x, y - 0.5F, filled, 2.0F, ColorUtility.scaleAlpha(accent(), alpha));
        }
    }
}
