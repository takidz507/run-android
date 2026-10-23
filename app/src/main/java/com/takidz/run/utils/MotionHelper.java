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
 * Run v3.2.0 Motion Engine (#15 & #21):
 * Preserves the owner-praised Liquid Glass droplet/elastic physics as the gold standard
 * and elevates all 7 themes to have expressive, intentional, tactile motion (scale + rise + spring settle),
 * while respecting Lite Mode (#21).
 */
public class MotionHelper {

    private static class Profile {
        final long panelMs, bubbleMs, colorMs, retMs;
        final float panelRiseDp, bubbleRiseDp, bubbleOvershoot, panelOvershoot;
        final float pressSquash, pressOvershoot;
        final boolean panelScale;

        Profile(long panelMs, long bubbleMs, long colorMs, long retMs,
                float panelRiseDp, float bubbleRiseDp,
                float bubbleOvershoot, float panelOvershoot,
                float pressSquash, float pressOvershoot, boolean panelScale) {
            this.panelMs = panelMs;
            this.bubbleMs = bubbleMs;
            this.colorMs = colorMs;
            this.retMs = retMs;
            this.panelRiseDp = panelRiseDp;
            this.bubbleRiseDp = bubbleRiseDp;
            this.bubbleOvershoot = bubbleOvershoot;
            this.panelOvershoot = panelOvershoot;
            this.pressSquash = pressSquash;
            this.pressOvershoot = pressOvershoot;
            this.panelScale = panelScale;
        }
    }

    // Elevated profiles inspired by the Liquid Glass gold standard:
    // Every theme now has tactile scale + organic spring overshoot tailored to its personality.
    private static final Profile WARM_PAPER =
            new Profile(300, 260, 260, 190, 20f, 16f, 1.15f, 0.75f, 0.92f, 1.55f, true);
    private static final Profile WARM_PAPER_DARK =
            new Profile(330, 280, 300, 200, 18f, 15f, 1.05f, 0.68f, 0.92f, 1.45f, true);
    private static final Profile CLEAN_AIR =
            new Profile(250, 220, 220, 170, 16f, 14f, 1.10f, 0.70f, 0.93f, 1.50f, true);
    private static final Profile DEEP_NIGHT =
            new Profile(260, 230, 220, 185, 20f, 16f, 1.35f, 0.85f, 0.91f, 1.65f, true);
    // Gold standard Liquid Glass profile (untouched droplet feel + elastic swell)
    private static final Profile LIQUID_GLASS =
            new Profile(380, 340, 380, 220, 26f, 20f, 1.5f, 0.9f, 0.90f, 1.8f, true);

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
            panel.setScaleX(0.95f);
            panel.setScaleY(0.95f);
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

    public static void animateStaggeredCard(View card, int index, boolean liteMode) {
        if (card == null) return;
        if (liteMode) {
            card.setAlpha(1f);
            card.setTranslationY(0f);
            card.setScaleX(1f);
            card.setScaleY(1f);
            return;
        }
        Profile p = current;
        float density = card.getResources().getDisplayMetrics().density;
        card.setAlpha(0f);
        card.setTranslationY(p.bubbleRiseDp * density * 0.55f);
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);
        long delay = Math.min(index * 38L, 220L);
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(p.panelMs)
                .setInterpolator(new OvershootInterpolator(p.panelOvershoot))
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
        bubble.setScaleX(0.95f);
        bubble.setScaleY(0.95f);
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
        Profile p = current;
        view.animate()
                .scaleX(p.pressSquash)
                .scaleY(p.pressSquash)
                .setDuration(75L)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(p.retMs)
                        .setInterpolator(new OvershootInterpolator(p.pressOvershoot))
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

    /** Liquid Glass & tactile sheen sweep for FAB, dock, and sheets. */
    public static void animateGlassSheen(View view, boolean liteMode) {
        if (view == null || liteMode) return;
        float startAlpha = isGlass() ? 0.55f : 0.78f;
        long dur = isGlass() ? 420L : 260L;
        view.setAlpha(startAlpha);
        view.animate()
                .alpha(1f)
                .setDuration(dur)
                .setInterpolator(new LinearInterpolator())
                .start();
    }
}
