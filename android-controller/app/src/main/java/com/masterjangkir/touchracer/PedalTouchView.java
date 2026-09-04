package com.masterjangkir.touchracer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

/**
 * Responsive Multi-Touch Pedal View for Landscape Cockpit.
 * - Left half = BRAKE pedal.
 * - Right half = GAS / ACCELERATOR pedal.
 * - Multi-touch pointer ID tracking for 100% simultaneous, independent thumb control.
 * - Scales perfectly to any screen resolution and aspect ratio (FHD+, QHD, 19.5:9, 20:9, etc.).
 */
public class PedalTouchView extends View {

    public interface OnPedalChangeListener {
        void onPedalsChanged(float gasRatio, float brakeRatio);
    }

    public static final int CONTROL_MODE_ABSOLUTE = 0;
    public static final int CONTROL_MODE_RELATIVE = 1;

    private OnPedalChangeListener listener;
    private int controlMode = CONTROL_MODE_ABSOLUTE;

    // Pedal values: 0.0f (idle) to 1.0f (full)
    private float gasRatio = 0.0f;
    private float brakeRatio = 0.0f;

    // Multi-touch tracking
    private int gasPointerId = MotionEvent.INVALID_POINTER_ID;
    private int brakePointerId = MotionEvent.INVALID_POINTER_ID;

    // Anchor points for relative mode
    private float gasStartY = 0.0f;
    private float brakeStartY = 0.0f;

    // Paints
    private Paint bgTrackPaint;
    private Paint trackBorderPaint;
    private Paint gasBarPaint;
    private Paint brakeBarPaint;
    private Paint textPaint;
    private Paint tickPaint;

    private float density = 1.0f;

    public PedalTouchView(Context context) {
        super(context);
        init();
    }

    public PedalTouchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PedalTouchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;

        bgTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgTrackPaint.setStyle(Paint.Style.FILL);

        trackBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackBorderPaint.setStyle(Paint.Style.STROKE);
        trackBorderPaint.setStrokeWidth(1.5f * density);

        gasBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gasBarPaint.setStyle(Paint.Style.FILL);

        brakeBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brakeBarPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(Color.argb(70, 255, 255, 255));
        tickPaint.setStrokeWidth(1.5f * density);
    }

    public void setOnPedalChangeListener(OnPedalChangeListener listener) {
        this.listener = listener;
    }

    public void setControlMode(int mode) {
        this.controlMode = mode;
    }

    public float getGasRatio() {
        return gasRatio;
    }

    public float getBrakeRatio() {
        return brakeRatio;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);
        float x = event.getX(actionIndex);
        float y = event.getY(actionIndex);
        float viewH = getHeight();
        float viewW = getWidth();
        float halfW = viewW / 2.0f;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (x < halfW) {
                    brakePointerId = pointerId;
                    brakeStartY = y;
                    updateBrake(y, viewH);
                } else {
                    gasPointerId = pointerId;
                    gasStartY = y;
                    updateGas(y, viewH);
                }
                notifyChange();
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                int pointerCount = event.getPointerCount();
                for (int i = 0; i < pointerCount; i++) {
                    int pId = event.getPointerId(i);
                    float py = event.getY(i);

                    if (pId == brakePointerId) {
                        updateBrake(py, viewH);
                    } else if (pId == gasPointerId) {
                        updateGas(py, viewH);
                    }
                }
                notifyChange();
                invalidate();
                return true;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pointerId == brakePointerId) {
                    brakePointerId = MotionEvent.INVALID_POINTER_ID;
                    brakeRatio = 0.0f;
                }
                if (pointerId == gasPointerId) {
                    gasPointerId = MotionEvent.INVALID_POINTER_ID;
                    gasRatio = 0.0f;
                }
                if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                    brakePointerId = MotionEvent.INVALID_POINTER_ID;
                    gasPointerId = MotionEvent.INVALID_POINTER_ID;
                    brakeRatio = 0.0f;
                    gasRatio = 0.0f;
                }
                notifyChange();
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private void updateBrake(float y, float height) {
        if (height <= 0) return;

        if (controlMode == CONTROL_MODE_ABSOLUTE) {
            // Bottom 92% is idle, Top 12% is full brake
            float effectiveBottom = height * 0.92f;
            float effectiveTop = height * 0.12f;
            float raw = (effectiveBottom - y) / (effectiveBottom - effectiveTop);
            brakeRatio = Math.max(0.0f, Math.min(1.0f, raw));
        } else {
            float maxDrag = height * 0.45f;
            float deltaY = brakeStartY - y;
            brakeRatio = Math.max(0.0f, Math.min(1.0f, deltaY / maxDrag));
        }
    }

    private void updateGas(float y, float height) {
        if (height <= 0) return;

        if (controlMode == CONTROL_MODE_ABSOLUTE) {
            // Bottom 92% is idle, Top 12% is full gas
            float effectiveBottom = height * 0.92f;
            float effectiveTop = height * 0.12f;
            float raw = (effectiveBottom - y) / (effectiveBottom - effectiveTop);
            gasRatio = Math.max(0.0f, Math.min(1.0f, raw));
        } else {
            float maxDrag = height * 0.45f;
            float deltaY = gasStartY - y;
            gasRatio = Math.max(0.0f, Math.min(1.0f, deltaY / maxDrag));
        }
    }

    private void notifyChange() {
        if (listener != null) {
            listener.onPedalsChanged(gasRatio, brakeRatio);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float halfW = w / 2.0f;

        // Dynamic pedal column widths:
        // Left pedal takes from 2% of width to (halfW - centerMargin)
        // Right pedal takes from (halfW + centerMargin) to 98% of width
        float centerHalfMargin = Math.max(130f * density, w * 0.16f);
        float sideMargin = Math.max(14f * density, w * 0.02f);

        float pedalTop = h * 0.15f;
        float pedalBottom = h * 0.88f;
        float pedalH = pedalBottom - pedalTop;
        float cornerRadius = 16f * density;

        // --- BRAKE PEDAL (LEFT) ---
        float brakeLeft = sideMargin;
        float brakeRight = halfW - centerHalfMargin;
        RectF brakeTrack = new RectF(brakeLeft, pedalTop, brakeRight, pedalBottom);

        bgTrackPaint.setColor(Color.argb(55, 60, 20, 25));
        canvas.drawRoundRect(brakeTrack, cornerRadius, cornerRadius, bgTrackPaint);

        trackBorderPaint.setColor(Color.argb(120, 255, 60, 70));
        canvas.drawRoundRect(brakeTrack, cornerRadius, cornerRadius, trackBorderPaint);

        // Active Brake Bar
        if (brakeRatio > 0.005f) {
            float activeTop = pedalBottom - (brakeRatio * pedalH);
            RectF activeBrake = new RectF(brakeLeft, activeTop, brakeRight, pedalBottom);
            LinearGradient brakeGrad = new LinearGradient(
                    brakeLeft, pedalBottom, brakeLeft, activeTop,
                    Color.argb(220, 255, 23, 68),
                    Color.argb(255, 255, 100, 120),
                    Shader.TileMode.CLAMP
            );
            brakeBarPaint.setShader(brakeGrad);
            canvas.drawRoundRect(activeBrake, cornerRadius, cornerRadius, brakeBarPaint);
        }

        // --- GAS PEDAL (RIGHT) ---
        float gasLeft = halfW + centerHalfMargin;
        float gasRight = w - sideMargin;
        RectF gasTrack = new RectF(gasLeft, pedalTop, gasRight, pedalBottom);

        bgTrackPaint.setColor(Color.argb(55, 15, 60, 30));
        canvas.drawRoundRect(gasTrack, cornerRadius, cornerRadius, bgTrackPaint);

        trackBorderPaint.setColor(Color.argb(120, 0, 230, 118));
        canvas.drawRoundRect(gasTrack, cornerRadius, cornerRadius, trackBorderPaint);

        // Active Gas Bar
        if (gasRatio > 0.005f) {
            float activeTop = pedalBottom - (gasRatio * pedalH);
            RectF activeGas = new RectF(gasLeft, activeTop, gasRight, pedalBottom);
            LinearGradient gasGrad = new LinearGradient(
                    gasLeft, pedalBottom, gasLeft, activeTop,
                    Color.argb(220, 0, 200, 83),
                    Color.argb(255, 105, 240, 174),
                    Shader.TileMode.CLAMP
            );
            gasBarPaint.setShader(gasGrad);
            canvas.drawRoundRect(activeGas, cornerRadius, cornerRadius, gasBarPaint);
        }

        // Ticks for both pedals at 25%, 50%, 75%
        for (float pct : new float[]{0.25f, 0.50f, 0.75f}) {
            float tickY = pedalBottom - (pct * pedalH);
            canvas.drawLine(brakeLeft + 15f * density, tickY, brakeRight - 15f * density, tickY, tickPaint);
            canvas.drawLine(gasLeft + 15f * density, tickY, gasRight - 15f * density, tickY, tickPaint);
        }

        // Labels
        float labelSize = Math.max(16f * density, h * 0.055f);
        textPaint.setTextSize(labelSize);

        textPaint.setColor(Color.argb(240, 255, 100, 100));
        canvas.drawText("BRAKE", (brakeLeft + brakeRight) / 2f, pedalTop - 12f * density, textPaint);

        textPaint.setColor(Color.argb(240, 100, 255, 120));
        canvas.drawText("GAS / THROTTLE", (gasLeft + gasRight) / 2f, pedalTop - 12f * density, textPaint);

        // Percentage text inside pedals
        float pctSize = Math.max(20f * density, h * 0.07f);
        textPaint.setTextSize(pctSize);
        textPaint.setColor(Color.WHITE);
        int brakePct = Math.round(brakeRatio * 100f);
        int gasPct = Math.round(gasRatio * 100f);

        canvas.drawText(brakePct + "%", (brakeLeft + brakeRight) / 2f, pedalBottom + 26f * density, textPaint);
        canvas.drawText(gasPct + "%", (gasLeft + gasRight) / 2f, pedalBottom + 26f * density, textPaint);
    }
}
