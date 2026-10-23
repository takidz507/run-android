package com.takidz.run.ui.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.takidz.run.R;

/**
 * #19: Animated Origami Splash View.
 * Renders the chosen F1 Origami icon assembling/folding in with a short, elegant animation (< 1.1s),
 * instant skip on tap, and automatic fast-path in #21 Lite Mode.
 */
public class OrigamiSplashView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint creamFacetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint creamShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limeFacetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap brandBitmap;
    private float progress = 0f;
    private ValueAnimator animator;
    private Runnable onCompleteCallback;

    public OrigamiSplashView(Context context) {
        super(context);
        init(context);
    }

    public OrigamiSplashView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OrigamiSplashView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float d = getResources().getDisplayMetrics().density;
        bgPaint.setColor(Color.parseColor("#181512"));
        cardPaint.setColor(Color.parseColor("#502516"));
        creamFacetPaint.setColor(Color.parseColor("#F7E4C3"));
        creamShadePaint.setColor(Color.parseColor("#E3CBA4"));
        limeFacetPaint.setColor(Color.parseColor("#A4D43B"));

        textPaint.setColor(Color.parseColor("#F5F1E6"));
        textPaint.setTextSize(30f * d);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        dotPaint.setColor(Color.parseColor("#B8E646"));

        subPaint.setColor(Color.parseColor("#9E9689"));
        subPaint.setTextSize(12f * d);
        subPaint.setTextAlign(Paint.Align.CENTER);

        try {
            brandBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_origami_brand);
        } catch (Exception ignored) {}

        // Skip instantly on tap (#19 requirement)
        setOnClickListener(v -> skipImmediately());
    }

    public void startSplash(boolean liteMode, Runnable onFinished) {
        this.onCompleteCallback = onFinished;
        if (liteMode) {
            progress = 1f;
            invalidate();
            postDelayed(this::finishSplash, 140L);
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(980L); // Under ~1.2s per #19
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finishSplash();
            }
        });
        animator.start();
    }

    public void skipImmediately() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        finishSplash();
    }

    private void finishSplash() {
        if (onCompleteCallback != null) {
            Runnable cb = onCompleteCallback;
            onCompleteCallback = null;
            cb.run();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float d = getResources().getDisplayMetrics().density;
        canvas.drawRect(0, 0, w, h, bgPaint);

        float cx = w / 2f;
        float cy = h / 2f - 28f * d;
        float boxSize = 132f * d;

        // Stage 1 (0.0 -> 0.45): Rounded espresso square unfolds
        float cardScale = Math.min(1f, progress / 0.40f);
        canvas.save();
        canvas.scale(0.6f + 0.4f * cardScale, 0.6f + 0.4f * cardScale, cx, cy);
        RectF cardRect = new RectF(cx - boxSize / 2f, cy - boxSize / 2f, cx + boxSize / 2f, cy + boxSize / 2f);
        canvas.drawRoundRect(cardRect, 32f * d, 32f * d, cardPaint);

        // Stage 2 (0.15 -> 0.85): Origami geometric facets fold in
        float foldP = Math.max(0f, Math.min(1f, (progress - 0.12f) / 0.65f));
        if (brandBitmap != null && progress > 0.62f) {
            int bmpAlpha = (int) (Math.min(1f, (progress - 0.62f) / 0.30f) * 255);
            Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            bmpPaint.setAlpha(bmpAlpha);
            canvas.drawBitmap(brandBitmap, null, cardRect, bmpPaint);
        }

        if (progress < 0.85f) {
            int facetAlpha = (int) ((progress < 0.65f ? foldP : Math.max(0f, 1f - (progress - 0.65f) / 0.20f)) * 255);
            creamFacetPaint.setAlpha(facetAlpha);
            creamShadePaint.setAlpha(facetAlpha);
            limeFacetPaint.setAlpha(facetAlpha);

            float rLeft = cx - 36f * d;
            float rTop = cy - 44f * d;
            float rRight = cx + 38f * d;
            float rBottom = cy + 44f * d;

            // Left vertical spine folding down
            Path spine = new Path();
            spine.moveTo(rLeft, rTop);
            spine.lineTo(rLeft + 18f * d, rTop);
            spine.lineTo(rLeft + 18f * d, rTop + (rBottom - rTop) * foldP);
            spine.lineTo(rLeft, rTop + (rBottom - rTop) * foldP);
            spine.close();
            canvas.drawPath(spine, creamFacetPaint);

            // Top loop folding across
            if (foldP > 0.25f) {
                float loopP = Math.min(1f, (foldP - 0.25f) / 0.5f);
                Path loop = new Path();
                loop.moveTo(rLeft + 18f * d, rTop);
                loop.lineTo(rLeft + 18f * d + (rRight - rLeft - 18f * d) * loopP, rTop + 14f * d);
                loop.lineTo(rLeft + 18f * d, cy + 4f * d);
                loop.close();
                canvas.drawPath(loop, creamShadePaint);
            }

            // Signature Lime Origami Triangle folding in
            if (foldP > 0.45f) {
                float limeP = Math.min(1f, (foldP - 0.45f) / 0.55f);
                Path limeTri = new Path();
                limeTri.moveTo(cx - 12f * d, cy - 2f * d);
                limeTri.lineTo(cx + 22f * d * limeP, cy - 6f * d);
                limeTri.lineTo(cx + 16f * d * limeP, rBottom);
                limeTri.close();
                canvas.drawPath(limeTri, limeFacetPaint);
            }
        }
        canvas.restore();

        // Stage 3 (0.45 -> 1.0): "Run." wordmark and subtitle fade in
        float textAlpha = Math.max(0f, Math.min(1f, (progress - 0.40f) / 0.45f));
        textPaint.setAlpha((int) (textAlpha * 255));
        dotPaint.setAlpha((int) (textAlpha * 255));
        subPaint.setAlpha((int) (textAlpha * 190));

        float textY = cy + boxSize / 2f + 44f * d;
        canvas.drawText("Run", cx - 5f * d, textY, textPaint);
        float textWidth = textPaint.measureText("Run");
        canvas.drawCircle(cx + textWidth / 2f + 2f * d, textY - 4f * d, 4.5f * d, dotPaint);

        canvas.drawText("Tap anywhere to skip", cx, textY + 24f * d, subPaint);
    }
}
