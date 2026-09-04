package com.masterjangkir.touchracer;

/**
 * Ultra-Lean 8-Byte High-Speed Racing Packet.
 * - Byte 0: 0x01 (Header)
 * - Byte 1..2: uint16 Big-Endian Steering (0..32767, Center = 16384)
 * - Byte 3..4: uint16 Big-Endian Throttle / Gas (0..32767, 0 = Idle, 32767 = 100%)
 * - Byte 5..6: uint16 Big-Endian Brake Pedal (0..32767, 0 = Idle, 32767 = 100%)
 * - Byte 7: uint8 Buttons (Bit 0: Shift Up, Bit 1: Shift Down, Bit 2: Handbrake, Bit 3: NOS...)
 *
 * Fast, zero-allocation serialization for maximum responsiveness.
 */
public class TouchRacerPacket {
    public static final int PACKET_SIZE = 8;
    public static final byte HEADER_BYTE = 0x01;

    public static final int AXIS_MIN = 0;
    public static final int AXIS_CENTER = 16384;
    public static final int AXIS_MAX = 32767;
    public static final int MIX_ANALOG_BLEND = 0;
    public static final int MIX_BRAKE_PRIORITY = 1;

    private int steeringRaw = AXIS_CENTER;
    private int gasRaw = 0;
    private int brakeRaw = 0;
    private int buttonBitmask = 0;

    // Preallocated byte buffer for zero garbage collection
    private final byte[] packetBuffer = new byte[PACKET_SIZE];

    public TouchRacerPacket() {
        packetBuffer[0] = HEADER_BYTE;
    }

    public void setSteering(float normalizedSteering) {
        if (normalizedSteering < -1.0f) normalizedSteering = -1.0f;
        if (normalizedSteering > 1.0f) normalizedSteering = 1.0f;

        int val = AXIS_CENTER + Math.round(normalizedSteering * 16383.0f);
        if (val < AXIS_MIN) val = AXIS_MIN;
        if (val > AXIS_MAX) val = AXIS_MAX;
        this.steeringRaw = val;
    }

    public void setPedals(float gasRatio, float brakeRatio) {
        if (gasRatio < 0.0f) gasRatio = 0.0f;
        if (gasRatio > 1.0f) gasRatio = 1.0f;
        if (brakeRatio < 0.0f) brakeRatio = 0.0f;
        if (brakeRatio > 1.0f) brakeRatio = 1.0f;

        this.gasRaw = Math.round(gasRatio * 32767.0f);
        this.brakeRaw = Math.round(brakeRatio * 32767.0f);
    }

    public void setButton(int buttonIndex, boolean pressed) {
        if (buttonIndex < 0 || buttonIndex > 7) return;
        if (pressed) {
            buttonBitmask |= (1 << buttonIndex);
        } else {
            buttonBitmask &= ~(1 << buttonIndex);
        }
    }

    public void setButtonBitmask(int bitmask) {
        this.buttonBitmask = bitmask & 0xFF;
    }

    public int getButtonBitmask() {
        return buttonBitmask;
    }

    public int getSteeringRaw() {
        return steeringRaw;
    }

    public int getGasRaw() {
        return gasRaw;
    }

    public int getBrakeRaw() {
        return brakeRaw;
    }

    /**
     * Fast zero-allocation byte serializer.
     */
    public byte[] build() {
        packetBuffer[0] = HEADER_BYTE;

        packetBuffer[1] = (byte) ((steeringRaw >> 8) & 0xFF);
        packetBuffer[2] = (byte) (steeringRaw & 0xFF);

        packetBuffer[3] = (byte) ((gasRaw >> 8) & 0xFF);
        packetBuffer[4] = (byte) (gasRaw & 0xFF);

        packetBuffer[5] = (byte) ((brakeRaw >> 8) & 0xFF);
        packetBuffer[6] = (byte) (brakeRaw & 0xFF);

        packetBuffer[7] = (byte) (buttonBitmask & 0xFF);

        return packetBuffer;
    }
}
