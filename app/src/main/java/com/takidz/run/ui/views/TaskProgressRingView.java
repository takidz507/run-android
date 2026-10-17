package com.takidz.run.ui.views;

import android.content.Context;

import android.graphics.Canvas;

import android.graphics.Color;

import android.graphics.Paint;

import android.graphics.RectF;

import android.util.AttributeSet;

import android.view.View;

import androidx.annotation.Nullable;

/**

* Custom Circular Progress Ring matching the Shared Tasks progress ring in `run`.

*/

public class TaskProgressRingView extends View {

private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

private final RectF arcBounds = new RectF();

private int progressPercent = 0;

private int accentColor = Color.parseColor("#c8f169");



private int trackColor = Color.parseColor("#252733");

private int textColor = Color.parseColor("#f4f3f8");

public TaskProgressRingView(Context context) {

super(context);

init();

}

public TaskProgressRingView(Context context, @Nullable AttributeSet attrs) {

super(context, attrs);

init();

}

public TaskProgressRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {

super(context, attrs, defStyleAttr);

init();

}

private void init() {

float density = getResources().getDisplayMetrics().density;

float strokeWidth = 8f * density;

trackPaint.setStyle(Paint.Style.STROKE);

trackPaint.setStrokeWidth(strokeWidth);

trackPaint.setColor(trackColor);

trackPaint.setStrokeCap(Paint.Cap.ROUND);



progressPaint.setStyle(Paint.Style.STROKE);

progressPaint.setStrokeWidth(strokeWidth);

progressPaint.setColor(accentColor);

progressPaint.setStrokeCap(Paint.Cap.ROUND);

textPaint.setColor(textColor);

textPaint.setTextSize(16f * density);

textPaint.setTextAlign(Paint.Align.CENTER);

textPaint.setFakeBoldText(true);

}

public void setProgress(int completed, int total) {

if (total <= 0) {

this.progressPercent = 0;

} else {

this.progressPercent = Math.min(100, Math.max(0, Math.round((completed * 100f) /

total)));

}

invalidate();

}

public void setColors(int accent, int track, int text) {

this.accentColor = accent;

this.trackColor = track;

this.textColor = text;



progressPaint.setColor(accentColor);

trackPaint.setColor(trackColor);

textPaint.setColor(textColor);

invalidate();

}

@Override

protected void onDraw(Canvas canvas) {

super.onDraw(canvas);

float pad = progressPaint.getStrokeWidth();

arcBounds.set(pad, pad, getWidth() - pad, getHeight() - pad);

canvas.drawOval(arcBounds, trackPaint);

float sweepAngle = (360f * progressPercent) / 100f;

canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint);

String label = progressPercent + "%";

Paint.FontMetrics fm = textPaint.getFontMetrics();

float textY = (getHeight() / 2f) - ((fm.ascent + fm.descent) / 2f);

canvas.drawText(label, getWidth() / 2f, textY, textPaint);

}

}


