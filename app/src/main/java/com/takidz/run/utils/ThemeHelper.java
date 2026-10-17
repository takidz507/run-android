package com.takidz.run.utils;

import android.graphics.Color;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Exact Theme Token Palette from the Run Technical Specification:
 * 1. Dark (Default): bg=#111218, panel=#1b1d26, panelSoft=#20222c, text=#f4f3f8, muted=#9b9dac, accent=#c8f169, accentInk=#1d2810
 * 2. AMOLED Black:   bg=#000000, panel=#080808, panelSoft=#101010, text=#f7f7f7, muted=#a3a3a3, accent=#c8f169, accentInk=#18210a
 * 3. Light:          bg=#f4f5f2, panel=#ffffff, panelSoft=#f6f7f3, text=#20221d, muted=#656a5d, accent=#3390ec, accentInk=#ffffff
 */
public class ThemeHelper {

    public static class Palette {
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
        public final int purple;
        public final int success;
        public final int danger;
        public final boolean isLight;

        public Palette(
                String bg, String bgElevated, String panel, String panelSoft, String panelHover,
                String text, String muted, String subtle, String accent, String accentInk,
                String purple, String success, String danger, boolean isLight
        ) {
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
            this.purple = Color.parseColor(purple);
            this.success = Color.parseColor(success);
            this.danger = Color.parseColor(danger);
            this.isLight = isLight;
        }
    }

    public static Palette getPalette(String mode) {
        if ("light".equalsIgnoreCase(mode)) {
            return new Palette(
                    "#f4f5f2", "#ffffff", "#ffffff", "#f6f7f3", "#eff1e9",
                    "#20221d", "#656a5d", "#858a7d", "#3390ec", "#ffffff",
                    "#6f62db", "#2c813d", "#c73e4d", true
            );
        } else if ("amoled".equalsIgnoreCase(mode)) {
            return new Palette(
                    "#000000", "#050505", "#080808", "#101010", "#171717",
                    "#f7f7f7", "#a3a3a3", "#777777", "#c8f169", "#18210a",
                    "#aaa2ff", "#a8ee9c", "#ff8c96", false
            );
        } else {
            // Default Dark
            return new Palette(
                    "#111218", "#171820", "#1b1d26", "#20222c", "#252733",
                    "#f4f3f8", "#9b9dac", "#747786", "#c8f169", "#1d2810",
                    "#aaa2ff", "#a8ee9c", "#ff8c96", false
            );
        }
    }

    public static void applyAppNightMode(String mode) {
        if ("light".equalsIgnoreCase(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }
}
