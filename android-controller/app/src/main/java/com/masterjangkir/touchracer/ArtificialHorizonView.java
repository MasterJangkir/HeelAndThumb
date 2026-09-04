package com.masterjangkir.touchracer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * Aircraft-style Artificial Horizon / Gyroscope Attitude Indicator for Racing Cockpit.
 * Replaces the circular steering wheel with an authentic horizon line that rolls with phone tilt.
 */
public class ArtificialHorizonView extends View {

    private float angleDegrees = 0.0f;
    private float normalizedSteering = 0.0f;

    private Paint horizonLinePaint;
    private Paint horizonGroundPaint;
    private Paint pitchLadderPaint;
    private Paint reticlePaint;
    private Paint textPaint;
    private Paint borderPaint;

    private float density = 1.0f;
    private Path horizonPath = new Path();

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

        horizonLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        horizonLinePaint.setColor(Color.argb(240, 0, 229, 255)); // Bright Cyan Horizon
        horizonLinePaint.setStrokeWidth(2.5f * density);
        horizonLinePaint.setStyle(Paint.Style.STROKE);

        horizonGroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        horizonGroundPaint.setStyle(Paint.Style.FILL);

        pitchLadderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pitchLadderPaint.setColor(Color.argb(160, 200, 225, 255));
        pitchLadderPaint.setStrokeWidth(1.5f * density);
        pitchLadderPaint.setStyle(Paint.Style.STROKE);

        reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reticlePaint.setColor(Color.argb(255, 255, 215, 0)); // Aircraft Yellow Reticle
        reticlePaint.setStrokeWidth(2.5f * density);
        reticlePaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.argb(70, 50, 65, 85));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f * density);
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
        float cy = h * 0.46f;
        float radius = Math.min(w * 0.44f, h * 0.42f);

        // Circular viewport clipping for the attitude indicator
        canvas.save();
        Path clipPath = new Path();
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // Sky & Ground background
        canvas.drawColor(Color.argb(40, 0, 30, 60)); // Night sky tint

        // Rotate canvas for artificial horizon line
        // Rotating the horizon opposite to phone roll mimics true aircraft gyro horizon!
        canvas.save();
        canvas.rotate(-angleDegrees, cx, cy);

        // Ground shading below the horizon line
        horizonGroundPaint.setColor(Color.argb(50, 80, 40, 10));
        canvas.drawRect(cx - radius * 2f, cy, cx + radius * 2f, cy + radius * 2f, horizonGroundPaint);

        // Level Horizon Line (wide horizontal line across gyro)
        canvas.drawLine(cx - radius * 1.5f, cy, cx + radius * 1.5f, cy, horizonLinePaint);

        // Pitch / Roll Ladder lines (+30°, +15°, -15°, -30°)
        for (int step : new int[]{-30, -15, 15, 30}) {
            float yOffset = cy - (step * 1.6f * density);
            float halfLen = (Math.abs(step) == 30 ? 24f : 16f) * density;
            canvas.drawLine(cx - halfLen, yOffset, cx + halfLen, yOffset, pitchLadderPaint);
        }

        canvas.restore(); // Restore unrotated coordinates for stationary reticle and bezel

        // Stationary Outer Bezel Circle
        canvas.drawCircle(cx, cy, radius, borderPaint);

        // Stationary Aircraft Reticle in Center: [ - • - ]
        float reticleWing = 22f * density;
        reticlePaint.setColor(Color.argb(255, 255, 215, 0));
        // Left wing
        canvas.drawLine(cx - reticleWing - 8f * density, cy, cx - 8f * density, cy, reticlePaint);
        canvas.drawLine(cx - 8f * density, cy, cx - 8f * density, cy + 4f * density, reticlePaint);
        // Right wing
        canvas.drawLine(cx + 8f * density, cy, cx + reticleWing + 8f * density, cy, reticlePaint);
        canvas.drawLine(cx + 8f * density, cy, cx + 8f * density, cy + 4f * density, reticlePaint);
        // Center dot
        reticlePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, 3f * density, reticlePaint);
        reticlePaint.setStyle(Paint.Style.STROKE);

        // Top dead-center 0° notch mark
        reticlePaint.setColor(Color.argb(200, 255, 255, 255));
        canvas.drawLine(cx, cy - radius, cx, cy - radius + 7f * density, reticlePaint);

        canvas.restore(); // Restore clip

        // Digital Angle Readout below the horizon gyro
        textPaint.setTextSize(14f * density);
        boolean isCentered = Math.abs(angleDegrees) < 1.0f;
        if (isCentered) {
            textPaint.setColor(Color.argb(255, 0, 230, 118)); // Green when centered
        } else {
            textPaint.setColor(Color.argb(240, 255, 215, 0)); // Yellow when turning
        }

        String degText = String.format(java.util.Locale.US, "%.1f\u00B0", angleDegrees);
        canvas.drawText(degText, cx, cy + radius + 16f * density, textPaint);

        textPaint.setTextSize(11f * density);
        textPaint.setColor(Color.argb(180, 160, 175, 195));
        int pct = Math.round(normalizedSteering * 100f);
        String pctText = (pct >= 0 ? "+" : "") + pct + "%";
        canvas.drawText(pctText, cx, cy + radius + 28f * density, textPaint);
    }
}
