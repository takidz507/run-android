package com.takidz.run.utils;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

/**
 * Run v3.2.0 Theme & Palette Engine:
 * Preserves all 7 themes:
 * 1. Warm Paper (Default)   — Light · warm paper (#F5F1E6), soft ivory cards (#FAF8F2), lime highlighter (#CBEF68)
 * 2. Warm Paper Dark        — Dark · same soul, at night (#181713)
 * 3. Clean Air              — Light · minimal and airy (#F6F8F5)
 * 4. Clean Air Dark         — Dark · airy sage-charcoal (#0F130F)
 * 5. Deep Night             — AMOLED · neon lime (#060805)
 * 6. Liquid Glass           — Frosted translucent surfaces over deep teal-emerald backdrop
 * 7. Liquid Glass Dark      — Deep obsidian frosted translucency
 *
 * Fixes v3.1 owner-reported bugs:
 * - Every BottomSheet paints its full opaque/frosted theme backdrop (`sheetBg`) and clears Material's
 *   default cream surface so Liquid Glass / Dark sheets never render white text on a light background.
 * - Provides `cardOpaque` for scrollable sheet cards and configures `BottomSheetBehavior` to `STATE_EXPANDED`
 *   with `skipCollapsed = true` so scrolling inside Settings never hides the list or leaves stray lime artifacts.
 */
public class ThemeHelper {

    public static final String THEME_WARM_PAPER = "warm_paper";
    public static final String THEME_WARM_PAPER_DARK = "warm_paper_dark";
    public static final String THEME_CLEAN_AIR = "clean_air";
    public static final String THEME_DEEP_NIGHT = "deep_night";
    public static final String THEME_GLASS = "liquid_glass";
    public static final String THEME_CLEAN_AIR_DARK = "clean_air_dark";
    public static final String THEME_GLASS_DARK = "liquid_glass_dark";

    // #17: Curated Per-Room Identity Color Presets
    public static final String[] ROOM_ACCENT_HEX = new String[]{
            "#B8E646", // 0: Warm Lime (Default)
            "#E06D53", // 1: Terracotta Paper
            "#E5A93B", // 2: Amber Ochre
            "#46B886", // 3: Sage Emerald
            "#3BA8B5", // 4: Coastal Teal
            "#5B7CE6", // 5: Cobalt Ink
            "#9B6BE6", // 6: Iris Violet
            "#D96B98"  // 7: Rose Clay
    };

    public static final String[] ROOM_BG_STYLES = new String[]{
            "paper",   // 0: Warm Paper Grain
            "dots",    // 1: Architect Dot Grid
            "lines",   // 2: Ruled Notebook
            "grid",    // 3: Study Graph Grid
            "waves",   // 4: Calm Contours
            "origami"  // 5: Origami Facets
    };

    public static class Palette {
        public final String themeKey;
        public final int bg;
        public final int bgElevated;
        public final int panel;
        public final int panelSoft;
        public final int panelHover;
        public final int sheetBg;
        public final int cardOpaque;
        public final int text;
        public final int muted;
        public final int subtle;
        public final int accent;
        public final int accentInk;
        public final int ownBubble;
        public final int ownBubbleText;
        public final int ownBubbleStroke;
        public final int highlighter;
        public final int dockBg;
        public final int dockText;
        public final int purple;
        public final int success;
        public final int danger;
        public final int warning;
        public final boolean isLight;

        public Palette(
                String themeKey,
                String bg, String bgElevated, String panel, String panelSoft, String panelHover,
                String text, String muted, String subtle, String accent, String accentInk,
                String ownBubble, String ownBubbleText, String ownBubbleStroke, String highlighter,
                String dockBg, String dockText,
                String purple, String success, String danger, String warning, boolean isLight
        ) {
            this.themeKey = themeKey;
            this.bg = Color.parseColor(bg);
            this.bgElevated = Color.parseColor(bgElevated);
            this.panel = Color.parseColor(panel);
            this.panelSoft = Color.parseColor(panelSoft);
            this.panelHover = Color.parseColor(panelHover);
            this.text = Color.parseColor(text);
            this.muted = Color.parseColor(muted);
            this.subtle = Color.parseColor(subtle);
            this.accent = Color.parseColor(accent);
            this.accentInk = Color.parseColor(accentInk);
            this.ownBubble = Color.parseColor(ownBubble);
            this.ownBubbleText = Color.parseColor(ownBubbleText);
            this.ownBubbleStroke = Color.parseColor(ownBubbleStroke);
            this.highlighter = Color.parseColor(highlighter);
            this.dockBg = Color.parseColor(dockBg);
            this.dockText = Color.parseColor(dockText);
            this.purple = Color.parseColor(purple);
            this.success = Color.parseColor(success);
            this.danger = Color.parseColor(danger);
            this.warning = Color.parseColor(warning);
            this.isLight = isLight;

            // Guaranteed opaque sheet & card surfaces so dark/glass bottom sheets are always 100% readable
            if (isLight) {
                this.sheetBg = this.panel;
                this.cardOpaque = this.panel;
            } else {
                this.sheetBg = ColorUtils.compositeColors(this.panel, this.bgElevated);
                this.cardOpaque = ColorUtils.compositeColors(this.panel, this.bgElevated);
            }
        }
    }

    public static String normalizeThemeMode(String mode) {
        if (mode == null) return THEME_WARM_PAPER;
        String m = mode.trim().toLowerCase(Locale.US);
        switch (m) {
            case THEME_WARM_PAPER:
            case "light":
                return THEME_WARM_PAPER;
            case THEME_WARM_PAPER_DARK:
            case "dark":
                return THEME_WARM_PAPER_DARK;
            case THEME_CLEAN_AIR:
                return THEME_CLEAN_AIR;
            case THEME_DEEP_NIGHT:
            case "amoled":
                return THEME_DEEP_NIGHT;
            case THEME_GLASS:
            case "glass":
            case "liquid":
                return THEME_GLASS;
            case THEME_CLEAN_AIR_DARK:
                return THEME_CLEAN_AIR_DARK;
            case THEME_GLASS_DARK:
                return THEME_GLASS_DARK;
            default:
                return THEME_WARM_PAPER;
        }
    }

    public static Palette getPalette(String mode) {
        String norm = normalizeThemeMode(mode);
        switch (norm) {
            case THEME_WARM_PAPER_DARK:
                return new Palette(
                        THEME_WARM_PAPER_DARK,
                        "#181713", "#201E19", "#24221C", "#2E2B23", "#3A362C",
                        "#F4F0E6", "#A6A090", "#7D7768", "#BCEB4B", "#172107",
                        "#384B17", "#F2F8E4", "#557322", "#47611B",
                        "#12110E", "#EAE5D9",
                        "#B5A8FF", "#9BE870", "#FF857A", "#F5B942", false
                );
            case THEME_CLEAN_AIR:
                return new Palette(
                        THEME_CLEAN_AIR,
                        "#F6F8F5", "#EEF2EC", "#FFFFFF", "#ECF0EA", "#DCE2D9",
                        "#171B16", "#60685D", "#8B9488", "#8AD834", "#132205",
                        "#E3F5C6", "#162408", "#C5E892", "#D5F2A7",
                        "#1B1F1A", "#F4F7F3",
                        "#5C5CE0", "#268740", "#D13B49", "#D98A1C", true
                );
            case THEME_DEEP_NIGHT:
                return new Palette(
                        THEME_DEEP_NIGHT,
                        "#060805", "#0C0F0A", "#11150F", "#181E15", "#252E20",
                        "#F2F7EF", "#8D9986", "#5E6958", "#C6FF33", "#0E1A00",
                        "#273D0F", "#EEFFD6", "#456E1A", "#365714",
                        "#0B0E0A", "#ECF5E8",
                        "#A89FFF", "#8CF56E", "#FF7A85", "#F7B93E", false
                );
            case THEME_CLEAN_AIR_DARK:
                return new Palette(
                        THEME_CLEAN_AIR_DARK,
                        "#0F130F", "#161B16", "#1D231D", "#252C25", "#333D33",
                        "#EDF3EC", "#9AA79B", "#6E7A6F", "#8AD834", "#132205",
                        "#2C4517", "#EBF7DA", "#49682A", "#3E5E1E",
                        "#0B0E0B", "#EFF5EE",
                        "#A79BFF", "#7BD88F", "#FF857A", "#F5B942", false
                );
            case THEME_GLASS_DARK:
                return new Palette(
                        THEME_GLASS_DARK,
                        "#04080A", "#0B1519", "#1FFFFFFF", "#14FFFFFF", "#33FFFFFF",
                        "#EFF8F3", "#A9C2B6", "#75907F", "#B9F25E", "#14230A",
                        "#263B14", "#F2FBEA", "#40FFFFFF", "#3B591A",
                        "#1AFFFFFF", "#E6F4EC",
                        "#C0B4FF", "#93F07A", "#FF8A80", "#F7B93E", false
                );
            case THEME_GLASS:
                return new Palette(
                        THEME_GLASS,
                        "#0C1512", "#13221D", "#29FFFFFF", "#1AFFFFFF", "#40FFFFFF",
                        "#F4FAF6", "#BCD2C6", "#84A293", "#B9F25E", "#14230A",
                        "#2D4717", "#F2FBEA", "#4DFFFFFF", "#42631E",
                        "#24FFFFFF", "#EAF6EE",
                        "#C0B4FF", "#93F07A", "#FF8A80", "#F7B93E", false
                );
            case THEME_WARM_PAPER:
            default:
                // Exact Design A (Warm Paper) tokens from 7196.png
                return new Palette(
                        THEME_WARM_PAPER,
                        "#F5F2E8", "#ECE7D9", "#FAF8F2", "#EAE4D3", "#E2DAC6",
                        "#1C1A15", "#6E695C", "#969080", "#B5E53E", "#1A2407",
                        "#D8F296", "#1B250A", "#C1E26B", "#CBEF68",
                        "#22201B", "#FAF8F2",
                        "#6857D6", "#2E7D32", "#C93B3B", "#D6831A", true
                );
        }
    }

    /**
     * Fixes v3.1 Bugs #1 and #2 for ALL BottomSheetDialogs:
     * 1. Clears Material's default cream surface on `@id/design_bottom_sheet` and applies the theme's
     *    full dark/glass/paper backdrop (`sheetBg`) so white text in Liquid Glass / Dark is never drawn on light cream.
     * 2. Expands `BottomSheetBehavior` and sets `skipCollapsed(true)` so scrolling down and up inside
     *    Settings or other sheets is smooth and never collapses/hides the content.
     */
    public static void prepareBottomSheet(BottomSheetDialog dialog, View rootView, Palette p) {
        if (dialog == null || rootView == null || p == null) return;
        applySheetSurface(rootView, p);
        dialog.setOnShowListener(d -> {
            try {
                FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                    BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setSkipCollapsed(true);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            } catch (Exception ignored) {}
        });
    }

    public static void applySheetSurface(View rootView, Palette p) {
        if (rootView == null || p == null) return;
        float d = rootView.getResources().getDisplayMetrics().density;
        GradientDrawable bg;
        if (isGlassTheme(p.themeKey)) {
            bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{
                            ColorUtils.blendARGB(p.bgElevated, Color.WHITE, 0.14f),
                            p.bgElevated,
                            p.bg
                    }
            );
            bg.setStroke((int) (1.2f * d), p.panelHover);
        } else {
            bg = new GradientDrawable();
            bg.setColor(p.sheetBg);
            bg.setStroke((int) (1f * d), p.panelHover);
        }
        float r = 26f * d;
        bg.setCornerRadii(new float[]{r, r, r, r, 0f, 0f, 0f, 0f});
        rootView.setBackground(bg);
    }

    public static int parseRoomAccentColor(String hex, Palette fallbackPalette) {
        if (hex != null && !hex.trim().isEmpty()) {
            try {
                return Color.parseColor(hex.trim());
            } catch (Exception ignored) {}
        }
        return fallbackPalette != null ? fallbackPalette.accent : Color.parseColor("#B5E53E");
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static String darkVariantOf(String key) {
        switch (normalizeThemeMode(key)) {
            case THEME_WARM_PAPER:
                return THEME_WARM_PAPER_DARK;
            case THEME_CLEAN_AIR:
                return THEME_CLEAN_AIR_DARK;
            case THEME_GLASS:
                return THEME_GLASS_DARK;
            default:
                return normalizeThemeMode(key);
        }
    }

    public static String lightVariantOf(String key) {
        switch (normalizeThemeMode(key)) {
            case THEME_WARM_PAPER_DARK:
                return THEME_WARM_PAPER;
            case THEME_CLEAN_AIR_DARK:
                return THEME_CLEAN_AIR;
            case THEME_GLASS_DARK:
                return THEME_GLASS;
            default:
                return normalizeThemeMode(key);
        }
    }

    public static boolean isDarkKey(String key) {
        String k = normalizeThemeMode(key);
        return k.equals(darkVariantOf(k));
    }

    public static boolean isGlassTheme(String key) {
        String k = normalizeThemeMode(key);
        return THEME_GLASS.equals(k) || THEME_GLASS_DARK.equals(k);
    }

    public static void applyAppNightMode(String mode) {
        Palette p = getPalette(mode);
        if (p.isLight) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }
}
