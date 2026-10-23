package com.takidz.run.utils;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * #15 & #21: First-class 60fps motion helper with automatic Lite Mode adaptation.
 */
public class MotionHelper {

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
        panel.setAlpha(0f);
        panel.setTranslationY(24f);
        panel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(new DecelerateInterpolator(1.6f))
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
        bubble.setAlpha(0f);
        bubble.setTranslationY(18f);
        bubble.setScaleX(0.96f);
        bubble.setScaleY(0.96f);
        bubble.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(190L)
                .setInterpolator(new OvershootInterpolator(0.85f))
                .start();
    }

    public static void animateButtonPulse(View view, boolean liteMode) {
        if (view == null || liteMode) return;
        view.animate()
                .scaleX(0.93f)
                .scaleY(0.93f)
                .setDuration(75L)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(130L)
                        .setInterpolator(new OvershootInterpolator(1.4f))
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
        anim.setDuration(240L);
        anim.addUpdateListener(a -> target.setBackgroundColor((int) a.getAnimatedValue()));
        anim.start();
    }
}
