package com.takidz.run.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.takidz.run.utils.ThemeHelper;

/**
 * #17: Per-room identity background canvas.
 * Renders the room owner's chosen background pattern ("paper", "dots", "lines", "grid", "waves", "origami")
 * tinted subtly with the room's synced accent color.
 */
public class RoomPatternBackgroundView extends View {

    private final Paint patternPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint washPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String bgStyle = "paper";
    private int accentColor = 0xFFB8E646;
    private boolean isLightTheme = true;

    public RoomPatternBackgroundView(Context context) {
        super(context);
        init();
    }

    public RoomPatternBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RoomPatternBackgroundView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        patternPaint.setStyle(Paint.Style.STROKE);
        washPaint.setStyle(Paint.Style.FILL);
    }

    public void setRoomIdentity(String style, int roomAccentColor, boolean isLight) {
        this.bgStyle = (style != null && !style.isEmpty()) ? style : "paper";
        this.accentColor = roomAccentColor;
        this.isLightTheme = isLight;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float d = getResources().getDisplayMetrics().density;

        // Subtle top ambient wash from the room's identity color
        washPaint.setColor(ThemeHelper.withAlpha(accentColor, isLightTheme ? 16 : 22));
        canvas.drawRect(0, 0, w, h, washPaint);

        int strokeAlpha = isLightTheme ? 38 : 34;
        patternPaint.setColor(ThemeHelper.withAlpha(accentColor, strokeAlpha));
        patternPaint.setStrokeWidth(1.1f * d);

        if ("dots".equalsIgnoreCase(bgStyle)) {
            patternPaint.setStyle(Paint.Style.FILL);
            float step = 24f * d;
            float radius = 1.6f * d;
            for (float y = step; y < h; y += step) {
                for (float x = step; x < w; x += step) {
                    canvas.drawCircle(x, y, radius, patternPaint);
                }
            }
        } else if ("lines".equalsIgnoreCase(bgStyle)) {
            patternPaint.setStyle(Paint.Style.STROKE);
            float step = 28f * d;
            for (float y = step; y < h; y += step) {
                canvas.drawLine(16f * d, y, w - 16f * d, y, patternPaint);
            }
        } else if ("grid".equalsIgnoreCase(bgStyle)) {
            patternPaint.setStyle(Paint.Style.STROKE);
            float step = 32f * d;
            for (float y = step; y < h; y += step) {
                canvas.drawLine(0, y, w, y, patternPaint);
            }
            for (float x = step; x < w; x += step) {
                canvas.drawLine(x, 0, x, h, patternPaint);
            }
        } else if ("waves".equalsIgnoreCase(bgStyle)) {
            patternPaint.setStyle(Paint.Style.STROKE);
            float stepY = 48f * d;
            float waveLen = 96f * d;
            float amp = 8f * d;
            Path path = new Path();
            for (float y = stepY; y < h; y += stepY) {
                path.reset();
                path.moveTo(0, y);
                for (float x = 0; x < w + waveLen; x += waveLen) {
                    path.quadTo(x + waveLen * 0.25f, y - amp, x + waveLen * 0.5f, y);
                    path.quadTo(x + waveLen * 0.75f, y + amp, x + waveLen, y);
                }
                canvas.drawPath(path, patternPaint);
            }
        } else if ("origami".equalsIgnoreCase(bgStyle)) {
            patternPaint.setStyle(Paint.Style.STROKE);
            float size = 72f * d;
            Path tri = new Path();
            for (float y = 0; y < h + size; y += size) {
                for (float x = 0; x < w + size; x += size) {
                    tri.reset();
                    tri.moveTo(x, y);
                    tri.lineTo(x + size, y);
                    tri.lineTo(x + size * 0.5f, y + size);
                    tri.close();
                    canvas.drawPath(tri, patternPaint);
                }
            }
        }
    }
}
