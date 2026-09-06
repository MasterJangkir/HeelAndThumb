package com.masterjangkir.touchracer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * Sleek Minimalist Aircraft Horizon Line HUD for Racing Cockpit.
 * Provides real-time attitude & roll feedback without obscuring cockpit controls.
 */
public class ArtificialHorizonView extends View {

    private float angleDegrees = 0.0f;
    private float normalizedSteering = 0.0f;

    private Paint baselinePaint;
    private Paint horizonLinePaint;
    private Paint reticlePaint;
    private Paint textPaint;

    private float density = 1.0f;

    public ArtificialHorizonView(Context context) {
        super(context);
        init();
    }

    public ArtificialHorizonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ArtificialHorizonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;

        // Subtle baseline guide
        baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        baselinePaint.setColor(Color.argb(45, 140, 170, 200));
        baselinePaint.setStrokeWidth(1.2f * density);
        baselinePaint.setStyle(Paint.Style.STROKE);

        // Dynamic Glowing Horizon Line
        horizonLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        horizonLinePaint.setColor(Color.argb(250, 0, 229, 255)); // Neon Cyan
        horizonLinePaint.setStrokeWidth(2.2f * density);
        horizonLinePaint.setStyle(Paint.Style.STROKE);

        // Center Boresight Reticle
        reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reticlePaint.setColor(Color.argb(240, 255, 215, 0)); // Racing Gold
        reticlePaint.setStrokeWidth(2.0f * density);
        reticlePaint.setStyle(Paint.Style.STROKE);

        // Telemetry Digital Readout
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
        float cy = h * 0.38f;
        float halfWidth = w * 0.44f;

        // 1. Subtle horizontal level baseline guide
        canvas.drawLine(cx - halfWidth, cy, cx + halfWidth, cy, baselinePaint);

        // 2. Dynamic Rotating Artificial Horizon Bar
        canvas.save();
        canvas.rotate(-angleDegrees, cx, cy);

        // Main dynamic roll line
        canvas.drawLine(cx - halfWidth + 6f * density, cy, cx + halfWidth - 6f * density, cy, horizonLinePaint);

        // HUD Attitude roll end ticks
        float tickH = 5f * density;
        canvas.drawLine(cx - halfWidth + 6f * density, cy - tickH, cx - halfWidth + 6f * density, cy + tickH, horizonLinePaint);
        canvas.drawLine(cx + halfWidth - 6f * density, cy - tickH, cx + halfWidth - 6f * density, cy + tickH, horizonLinePaint);

        canvas.restore();

        // 3. Stationary Center Reticle: [ ── • ── ]
        float pipGap = 8f * density;
        float wingLen = 18f * density;
        reticlePaint.setColor(Color.argb(200, 255, 255, 255));
        canvas.drawLine(cx - pipGap - wingLen, cy, cx - pipGap, cy, reticlePaint);
        canvas.drawLine(cx + pipGap, cy, cx + pipGap + wingLen, cy, reticlePaint);

        // Center Pip (Neon Green when level, Gold when steered)
        boolean isCentered = Math.abs(angleDegrees) < 1.0f;
        reticlePaint.setStyle(Paint.Style.FILL);
        if (isCentered) {
            reticlePaint.setColor(Color.argb(255, 0, 230, 118));
        } else {
            reticlePaint.setColor(Color.argb(255, 255, 215, 0));
        }
        canvas.drawCircle(cx, cy, 2.5f * density, reticlePaint);
        reticlePaint.setStyle(Paint.Style.STROKE);

        // 4. Digital Angle & Steering % Readout
        textPaint.setTextSize(10.5f * density);
        if (isCentered) {
            textPaint.setColor(Color.argb(240, 0, 230, 118));
        } else {
            textPaint.setColor(Color.argb(240, 255, 215, 0));
        }

        String angleStr;
        if (isCentered) {
            angleStr = "CENTER 0.0\u00B0";
        } else if (angleDegrees < 0) {
            angleStr = String.format(java.util.Locale.US, "L %.1f\u00B0 (%d%%)", -angleDegrees, Math.round(-normalizedSteering * 100f));
        } else {
            angleStr = String.format(java.util.Locale.US, "R %.1f\u00B0 (%d%%)", angleDegrees, Math.round(normalizedSteering * 100f));
        }
        canvas.drawText(angleStr, cx, h - 2f * density, textPaint);
    }
}
