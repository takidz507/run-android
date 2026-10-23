package com.takidz.run.utils;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * #15 & #21: First-class 60fps motion helper with automatic Lite Mode adaptation.
 *
 * v3.1 (owner request): every theme carries its OWN motion character.
 * Activities call {@link #setThemeProfile(String)} whenever a theme is applied;
 * all animations below then speak that theme's motion language:
 *  - Warm Paper      — soft paper glide: gentle rise, decelerate settle.
 *  - Warm Paper Dark — the same soul, slower and quieter (longer fades).
 *  - Clean Air       — barely-there: short, small, fast.
 *  - Deep Night      — crisp neon snap: quick with a small electric overshoot.
 *  - Liquid Glass    — liquid spring: panels swell in with a soft overshoot,
 *                      bubbles pour in with a slow elastic settle.
 */
public class MotionHelper {

    private static class Profile {
        final long panelMs, bubbleMs, colorMs;
        final float panelRiseDp, bubbleRiseDp, bubbleOvershoot, panelOvershoot;
        final boolean panelScale;

        Profile(long panelMs, long bubbleMs, long colorMs,
                float panelRiseDp, float bubbleRiseDp,
                float bubbleOvershoot, float panelOvershoot, boolean panelScale) {
            this.panelMs = panelMs;
            this.bubbleMs = bubbleMs;
            this.colorMs = colorMs;
            this.panelRiseDp = panelRiseDp;
            this.bubbleRiseDp = bubbleRiseDp;
            this.bubbleOvershoot = bubbleOvershoot;
            this.panelOvershoot = panelOvershoot;
            this.panelScale = panelScale;
        }
    }

    private static final Profile WARM_PAPER =
            new Profile(220, 190, 240, 24f, 18f, 0.85f, 0f, false);
    private static final Profile WARM_PAPER_DARK =
            new Profile(300, 240, 320, 16f, 12f, 0.5f, 0f, false);
    private static final Profile CLEAN_AIR =
            new Profile(150, 130, 160, 10f, 8f, 0.3f, 0f, false);
    private static final Profile DEEP_NIGHT =
            new Profile(170, 150, 180, 18f, 14f, 1.1f, 0f, false);
    private static final Profile LIQUID_GLASS =
            new Profile(380, 340, 380, 26f, 20f, 1.5f, 0.9f, true);

    private static volatile Profile current = WARM_PAPER;
    private static volatile String currentKey = ThemeHelper.THEME_WARM_PAPER;

    /** Called by activities whenever a theme is applied. */
    public static void setThemeProfile(String themeKey) {
        String key = ThemeHelper.normalizeThemeMode(themeKey);
        currentKey = key;
        switch (key) {
            case ThemeHelper.THEME_WARM_PAPER_DARK:
                current = WARM_PAPER_DARK;
                break;
            case ThemeHelper.THEME_CLEAN_AIR:
            case ThemeHelper.THEME_CLEAN_AIR_DARK:
                current = CLEAN_AIR;
                break;
            case ThemeHelper.THEME_DEEP_NIGHT:
                current = DEEP_NIGHT;
                break;
            case ThemeHelper.THEME_GLASS:
            case ThemeHelper.THEME_GLASS_DARK:
                current = LIQUID_GLASS;
                break;
            case ThemeHelper.THEME_WARM_PAPER:
            default:
                current = WARM_PAPER;
                break;
        }
    }

    public static String currentThemeKey() {
        return currentKey;
    }

    public static boolean isGlass() {
        return ThemeHelper.isGlassTheme(currentKey);
    }

    private static Interpolator panelInterpolator(Profile p) {
        if (p.panelOvershoot > 0f) return new OvershootInterpolator(p.panelOvershoot);
        return new DecelerateInterpolator(1.6f);
    }

    public static void animatePanelEntrance(View panel, boolean liteMode) {
        if (panel == null) return;
        panel.setVisibility(View.VISIBLE);
        if (liteMode) {
            panel.setAlpha(1f);
            panel.setTranslationY(0f);
            panel.setScaleX(1f);
            panel.setScaleY(1f);
            return;
        }
        Profile p = current;
        float density = panel.getResources().getDisplayMetrics().density;
        panel.setAlpha(0f);
        panel.setTranslationY(p.panelRiseDp * density / 2f);
        if (p.panelScale) {
            panel.setScaleX(0.94f);
            panel.setScaleY(0.94f);
        }
        panel.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(p.panelMs)
                .setInterpolator(panelInterpolator(p))
                .start();
    }

    public static void animateMessageBubbleIn(View bubble, boolean liteMode) {
        if (bubble == null) return;
        if (liteMode) {
            bubble.setAlpha(1f);
            bubble.setTranslationY(0f);
            bubble.setScaleX(1f);
            bubble.setScaleY(1f);
            return;
        }
        Profile p = current;
        float density = bubble.getResources().getDisplayMetrics().density;
        bubble.setAlpha(0f);
        bubble.setTranslationY(p.bubbleRiseDp * density / 2f);
        bubble.setScaleX(0.96f);
        bubble.setScaleY(0.96f);
        bubble.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(p.bubbleMs)
                .setInterpolator(new OvershootInterpolator(p.bubbleOvershoot))
                .start();
    }

    public static void animateButtonPulse(View view, boolean liteMode) {
        if (view == null || liteMode) return;
        // Liquid Glass presses feel like pressing a droplet: deeper squash, springy return.
        final float squash = isGlass() ? 0.90f : 0.93f;
        final long retMs = isGlass() ? 220L : 130L;
        view.animate()
                .scaleX(squash)
                .scaleY(squash)
                .setDuration(75L)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(retMs)
                        .setInterpolator(new OvershootInterpolator(isGlass() ? 1.8f : 1.4f))
                        .start())
                .start();
    }

    public static void animateBackgroundColor(final View target, int fromColor, int toColor, boolean liteMode) {
        if (target == null) return;
        if (liteMode || fromColor == toColor) {
            target.setBackgroundColor(toColor);
            return;
        }
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        anim.setDuration(current.colorMs);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(a -> target.setBackgroundColor((int) a.getAnimatedValue()));
        anim.start();
    }

    /** v3.1 Liquid Glass: a soft sheen sweep for glass surfaces (FAB, dock, sheets). */
    public static void animateGlassSheen(View view, boolean liteMode) {
        if (view == null || liteMode || !isGlass()) return;
        view.setAlpha(0.55f);
        view.animate()
                .alpha(1f)
                .setDuration(420L)
                .setInterpolator(new LinearInterpolator())
                .start();
    }
}
