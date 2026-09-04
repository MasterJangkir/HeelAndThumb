package com.masterjangkir.touchracer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * High-Precision Steering Processor.
 * - Uses 3D Coordinate Remapping for Landscape so Pitch absorbs face-tilt and keeps the Center rock-solid.
 * - 100% Linear steering curve: 0° to 45° and 45° to 90° have the exact same smooth, predictable sensitivity.
 * - Configurable sensitivity (45° to 180°) and deadzone.
 */
public class SteeringProcessor implements SensorEventListener {

    public interface OnSteeringUpdateListener {
        void onSteeringUpdate(float normalizedSteering, float rawAngleDegrees);
    }

    private final SensorManager sensorManager;
    private final OnSteeringUpdateListener listener;

    private Sensor rotationSensor;
    private Sensor gyroSensor;
    private Sensor accelSensor;

    // Configuration
    private float sensitivityDegrees = 90.0f; // 45° to 180°
    private float deadzoneRatio = 0.015f;      // 1.5% default deadzone

    // Calibration offset
    private float centerOffsetDegrees = 0.0f;

    // State
    private float currentAngleDegrees = 0.0f;
    private float normalizedOutput = 0.0f;

    // Fallback complementary filter variables
    private long lastTimestamp = 0;
    private float filterAngle = 0.0f;
    private static final float FILTER_ALPHA = 0.97f;

    private final float[] rotationMatrix = new float[9];
    private final float[] remappedMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private boolean isRegistered = false;

    public SteeringProcessor(Context context, OnSteeringUpdateListener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;

        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationSensor == null) {
                rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            }
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    public void start() {
        if (sensorManager == null || isRegistered) return;

        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            if (gyroSensor != null) {
                sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
            }
            if (accelSensor != null) {
                sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME);
            }
        }
        isRegistered = true;
    }

    public void stop() {
        if (sensorManager != null && isRegistered) {
            sensorManager.unregisterListener(this);
            isRegistered = false;
        }
    }

    public void calibrateCenter() {
        centerOffsetDegrees = currentAngleDegrees;
    }

    public void setSensitivity(float degrees) {
        if (degrees < 45.0f) degrees = 45.0f;
        if (degrees > 180.0f) degrees = 180.0f;
        this.sensitivityDegrees = degrees;
    }

    public float getSensitivity() {
        return sensitivityDegrees;
    }

    public void setDeadzone(float ratio) {
        if (ratio < 0.0f) ratio = 0.0f;
        if (ratio > 0.20f) ratio = 0.20f;
        this.deadzoneRatio = ratio;
    }

    public float getDeadzone() {
        return deadzoneRatio;
    }

    public float getNormalizedOutput() {
        return normalizedOutput;
    }

    public float getCurrentAngleDegrees() {
        return currentAngleDegrees - centerOffsetDegrees;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();

        if (type == Sensor.TYPE_ROTATION_VECTOR || type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            processRotationVector(event.values);
        } else if (type == Sensor.TYPE_GYROSCOPE) {
            processGyroscope(event.values, event.timestamp);
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            processAccelerometer(event.values);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void processRotationVector(float[] rotationVector) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);

        // Remap coordinate system for Landscape:
        // Y becomes world X, -X becomes world Y.
        // Pitch absorbs face tilt to anchor gravity and keep the center rock-solid!
        SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                remappedMatrix
        );

        SensorManager.getOrientation(remappedMatrix, orientationAngles);

        // Roll (orientationAngles[2]) is the pure steering wheel tilt
        float rollRad = orientationAngles[2];
        float angleDeg = (float) Math.toDegrees(rollRad);

        updateSteering(angleDeg);
    }

    private void processGyroscope(float[] gyroValues, long timestamp) {
        if (lastTimestamp != 0) {
            float dt = (timestamp - lastTimestamp) * 1.0e-9f;
            float gyroRateZ = (float) Math.toDegrees(gyroValues[2]);
            filterAngle += gyroRateZ * dt;
        }
        lastTimestamp = timestamp;
    }

    private void processAccelerometer(float[] accelValues) {
        float ax = accelValues[0];
        float ay = accelValues[1];
        float accelAngle = (float) Math.toDegrees(Math.atan2(ay, ax)) - 90.0f;

        // Complementary filter
        filterAngle = FILTER_ALPHA * filterAngle + (1.0f - FILTER_ALPHA) * accelAngle;
        updateSteering(filterAngle);
    }

    private void updateSteering(float rawAngle) {
        currentAngleDegrees = rawAngle;
        float calibratedAngle = rawAngle - centerOffsetDegrees;

        // Wrap to -180..+180
        while (calibratedAngle > 180f) calibratedAngle -= 360f;
        while (calibratedAngle < -180f) calibratedAngle += 360f;

        // 100% LINEAR scaling: Every degree produces the exact same proportional response!
        float norm = calibratedAngle / sensitivityDegrees;
        if (norm < -1.0f) norm = -1.0f;
        if (norm > 1.0f) norm = 1.0f;

        // Apply deadzone smoothly without any sudden jump or quadratic acceleration
        float sign = Math.signum(norm);
        float absNorm = Math.abs(norm);

        if (absNorm <= deadzoneRatio) {
            norm = 0.0f;
        } else {
            norm = sign * ((absNorm - deadzoneRatio) / (1.0f - deadzoneRatio));
        }

        normalizedOutput = norm;

        if (listener != null) {
            listener.onSteeringUpdate(normalizedOutput, calibratedAngle);
        }
    }
}
