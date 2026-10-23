package com.takidz.run.utils;

import android.graphics.Color;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.Locale;

/**
 * #14 & #22: The Four Approved Switchable Themes from `themes-all-four.png`:
 * 1. Warm Paper (Default)   — Light · the original, warmer (#F5F1E6 paper, #B8E646 lime highlighter)
 * 2. Warm Paper Dark        — Dark · same soul, at night (#181713 warm espresso-charcoal, #BCEB4B lime)
 * 3. Clean Air              — Light · minimal and airy (#F7F8F6 white-sage, thin borders, #8CD936 leaf lime)
 * 4. Deep Night             — AMOLED · neon lime (#060705 true black, #C6FF33 electric neon lime)
 *
 * Also provides #17 Per-Room Identity color presets and background styles.
 */
public class ThemeHelper {

    public static final String THEME_WARM_PAPER = "warm_paper";
    public static final String THEME_WARM_PAPER_DARK = "warm_paper_dark";
    public static final String THEME_CLEAN_AIR = "clean_air";
    public static final String THEME_DEEP_NIGHT = "deep_night";
    // v3.1 (owner request): Liquid Glass — translucent frosted surfaces, iPhone-style
    public static final String THEME_GLASS = "liquid_glass";
    // v3.1 (owner request): EVERY design gets a dark-mode variant that the
    // settings Dark Mode switch flips to — dark must work with all designs.
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
                // Warm Paper Dark: Dark · same soul, at night
                return new Palette(
                        THEME_WARM_PAPER_DARK,
                        "#181713", "#201E19", "#23211B", "#2D2A22", "#3A362C",
                        "#F4F0E6", "#A6A090", "#7D7768", "#BCEB4B", "#172107",
                        "#384B17", "#F2F8E4", "#557322", "#47611B",
                        "#12110E", "#EAE5D9",
                        "#B5A8FF", "#9BE870", "#FF857A", "#F5B942", false
                );
            case THEME_CLEAN_AIR:
                // Clean Air: Light · minimal and airy
                return new Palette(
                        THEME_CLEAN_AIR,
                        "#F6F8F5", "#EEF2EC", "#FFFFFF", "#ECF0EA", "#DCE2D9",
                        "#171B16", "#60685D", "#8B9488", "#8AD834", "#132205",
                        "#E3F5C6", "#162408", "#C5E892", "#D5F2A7",
                        "#1B1F1A", "#F4F7F3",
                        "#5C5CE0", "#268740", "#D13B49", "#D98A1C", true
                );
            case THEME_DEEP_NIGHT:
                // Deep Night: AMOLED · neon lime
                return new Palette(
                        THEME_DEEP_NIGHT,
                        "#060805", "#0C0F0A", "#11150F", "#181E15", "#252E20",
                        "#F2F7EF", "#8D9986", "#5E6958", "#C6FF33", "#0E1A00",
                        "#273D0F", "#EEFFD6", "#456E1A", "#365714",
                        "#0B0E0A", "#ECF5E8",
                        "#A89FFF", "#8CF56E", "#FF7A85", "#F7B93E", false
                );
            case THEME_CLEAN_AIR_DARK:
                // Clean Air Dark (v3.1): Clean Air's airy soul after sunset —
                // same leaf-lime accent, deep sage-charcoal surfaces.
                return new Palette(
                        THEME_CLEAN_AIR_DARK,
                        "#0F130F", "#161B16", "#1D231D", "#252C25", "#333D33",
                        "#EDF3EC", "#9AA79B", "#6E7A6F", "#8AD834", "#132205",
                        "#2C4517", "#EBF7DA", "#49682A", "#3E5E1E",
                        "#0B0E0B", "#EFF5EE",
                        "#A79BFF", "#7BD88F", "#FF857A", "#F5B942", false
                );
            case THEME_GLASS_DARK:
                // Liquid Glass Dark (v3.1): the glass theme pushed deeper —
                // near-black backdrop, dimmer frost, same liquid translucency.
                return new Palette(
                        THEME_GLASS_DARK,
                        "#04080A", "#0A1418", "#1FFFFFFF", "#12FFFFFF", "#30FFFFFF",
                        "#EFF8F3", "#A9C2B6", "#75907F", "#B9F25E", "#14230A",
                        "#40B5E53E", "#F2FBEA", "#40FFFFFF", "#4DB5E53E",
                        "#1AFFFFFF", "#E6F4EC",
                        "#C0B4FF", "#93F07A", "#FF8A80", "#F7B93E", false
                );
            case THEME_GLASS:
                // Liquid Glass (v3.1): deep blue-teal backdrop, frosted translucent
                // panels (alpha whites), glass-edge strokes, frosted lime accent.
                // Panel colors carry alpha on purpose — surfaces float over the
                // gradient backdrop the activities install for this theme.
                return new Palette(
                        THEME_GLASS,
                        "#0C1512", "#14231C", "#29FFFFFF", "#1AFFFFFF", "#40FFFFFF",
                        "#F4FAF6", "#BCD2C6", "#84A293", "#B9F25E", "#14230A",
                        "#4DB5E53E", "#F2FBEA", "#4DFFFFFF", "#57B5E53E",
                        "#24FFFFFF", "#EAF6EE",
                        "#C0B4FF", "#93F07A", "#FF8A80", "#F7B93E", false
                );
            case THEME_WARM_PAPER:
            default:
                // Warm Paper (Default): Light · the original, warmer
                return new Palette(
                        THEME_WARM_PAPER,
                        "#F5F1E6", "#ECE7D9", "#FCF9F2", "#EAE4D3", "#DDD5C1",
                        "#1D1B16", "#6D685B", "#969080", "#B5E53E", "#1A2407",
                        "#DCF29D", "#1B250A", "#C2E36B", "#CBEF68",
                        "#1E1C17", "#F7F4EB",
                        "#6857D6", "#2E7D32", "#C93B3B", "#D6831A", true
                );
        }
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

    /** v3.1: dark variant of any design (Deep Night is already the darkest). */
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

    /** v3.1: light variant of any design. */
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

    /** v3.1: is this key one of the dark designs? */
    public static boolean isDarkKey(String key) {
        String k = normalizeThemeMode(key);
        return k.equals(darkVariantOf(k));
    }

    /** v3.1: both glass designs share the liquid-glass treatments. */
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
