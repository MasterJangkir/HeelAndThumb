package com.masterjangkir.touchracer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * Renders a visual racing steering wheel gauge with live rotation angle.
 * Fully responsive and density-scaled.
 */
public class SteeringGaugeView extends View {

    private float angleDegrees = 0.0f;
    private float normalizedSteering = 0.0f;

    private Paint wheelPaint;
    private Paint spokePaint;
    private Paint centerPaint;
    private Paint textPaint;
    private Paint markerPaint;

    private RectF wheelBounds = new RectF();
    private float density = 1.0f;

    public SteeringGaugeView(Context context) {
        super(context);
        init();
    }

    public SteeringGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SteeringGaugeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;

        wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wheelPaint.setStyle(Paint.Style.STROKE);
        wheelPaint.setColor(Color.argb(220, 230, 230, 230));
        wheelPaint.setStrokeWidth(6f * density);

        spokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        spokePaint.setStyle(Paint.Style.STROKE);
        spokePaint.setColor(Color.argb(180, 180, 180, 180));
        spokePaint.setStrokeWidth(4f * density);

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(Color.argb(240, 230, 40, 40));

        markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(Color.argb(255, 255, 215, 0)); // Gold center tape

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setSteering(float normalized, float rawDegrees) {
        this.normalizedSteering = normalized;
        this.angleDegrees = rawDegrees;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2.0f;
        float cy = h * 0.44f;
        float radius = Math.min(w * 0.40f, h * 0.38f);

        // Top zero marker (stationary center notch)
        markerPaint.setColor(Color.argb(180, 255, 255, 255));
        canvas.drawCircle(cx, cy - radius - 8f * density, 3f * density, markerPaint);

        // Save canvas for wheel rotation
        canvas.save();
        canvas.rotate(angleDegrees, cx, cy);

        // Outer rim
        wheelBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawOval(wheelBounds, wheelPaint);

        // Top dead-center yellow racing stripe on wheel
        markerPaint.setColor(Color.argb(255, 255, 215, 0));
        RectF stripe = new RectF(cx - 5f * density, cy - radius - 6f * density, cx + 5f * density, cy - radius + 6f * density);
        canvas.drawRoundRect(stripe, 2f * density, 2f * density, markerPaint);

        // Spokes (horizontal and downward T-spoke like a GT3 racing wheel)
        canvas.drawLine(cx - radius + 4f * density, cy, cx - 12f * density, cy, spokePaint);
        canvas.drawLine(cx + 12f * density, cy, cx + radius - 4f * density, cy, spokePaint);
        canvas.drawLine(cx, cy + 12f * density, cx, cy + radius - 4f * density, spokePaint);

        // Center hub
        canvas.drawCircle(cx, cy, 14f * density, centerPaint);

        canvas.restore();

        // Degrees and percentage text below wheel
        textPaint.setTextSize(14f * density);
        textPaint.setColor(Color.argb(240, 220, 220, 220));
        String degText = String.format("%.1f°", angleDegrees);
        canvas.drawText(degText, cx, cy + radius + 18f * density, textPaint);

        textPaint.setTextSize(12f * density);
        textPaint.setColor(Color.argb(180, 180, 180, 180));
        int pct = Math.round(normalizedSteering * 100f);
        String pctText = (pct >= 0 ? "+" : "") + pct + "%";
        canvas.drawText(pctText, cx, cy + radius + 32f * density, textPaint);
    }
}
