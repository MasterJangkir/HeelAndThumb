package com.masterjangkir.touchracer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles all network transport modes:
 * 1. Wi-Fi UDP (Port 41503) - ultra low latency datagrams
 * 2. Wi-Fi TCP (Port 41503) - reliable stream
 * 3. Bluetooth SPP / RFCOMM - wireless without Wi-Fi router
 * 4. USB (ADB Reverse or USB Tethering to PC)
 */
public class NetworkManager {

    public enum ConnectionType {
        WIFI_UDP,
        WIFI_TCP,
        BLUETOOTH,
        USB
    }

    public enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    public interface ConnectionListener {
        void onStateChanged(State state, String message);
        void onPacketRateUpdate(int packetsPerSecond);
    }

    public static final int DEFAULT_PORT = 41503;
    // Standard Bluetooth Serial Port Profile (SPP) UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private ConnectionType connectionType = ConnectionType.WIFI_UDP;
    private String targetHost = "192.168.1.100";
    private int targetPort = DEFAULT_PORT;
    private String bluetoothDeviceAddress = "";

    private ConnectionListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread workerThread;

    // Sockets
    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private OutputStream tcpOutputStream;
    private BluetoothSocket btSocket;
    private OutputStream btOutputStream;

    // Current packet to send
    private final Object packetLock = new Object();
    private byte[] latestPacketData;

    // Packet rate calculation
    private int packetCount = 0;
    private long lastRateCheck = System.currentTimeMillis();

    public NetworkManager(ConnectionListener listener) {
        this.listener = listener;
    }

    public void setConfig(ConnectionType type, String host, int port, String btAddress) {
        this.connectionType = type;
        this.targetHost = host;
        this.targetPort = port > 0 ? port : DEFAULT_PORT;
        this.bluetoothDeviceAddress = btAddress;
    }

    public synchronized void connect() {
        disconnect();

        isRunning.set(true);
        notifyState(State.CONNECTING, "Connecting via " + connectionType.name() + "...");

        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    switch (connectionType) {
                        case WIFI_UDP:
                            runUdpLoop();
                            break;
                        case WIFI_TCP:
                        case USB:
                            runTcpLoop();
                            break;
                        case BLUETOOTH:
                            runBluetoothLoop();
                            break;
                    }
                } catch (SecurityException se) {
                    if (isRunning.get()) {
                        notifyState(State.ERROR, "Izin Bluetooth belum diberikan: " + se.getMessage());
                    }
                } catch (Exception e) {
                    if (isRunning.get()) {
                        notifyState(State.ERROR, "Error: " + e.getMessage());
                    }
                } finally {
                    cleanup();
                    if (isRunning.get()) {
                        notifyState(State.DISCONNECTED, "Disconnected");
                    }
                }
            }
        }, "TouchRacerNetThread");

        workerThread.start();
    }

    public synchronized void disconnect() {
        isRunning.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        cleanup();
        notifyState(State.DISCONNECTED, "Disconnected");
    }

    public boolean isConnected() {
        return isRunning.get();
    }

    /**
     * Updates the latest packet to be transmitted by the worker loop.
     */
    public void sendPacket(byte[] data) {
        synchronized (packetLock) {
            this.latestPacketData = data;
        }
    }

    private void runUdpLoop() throws Exception {
        udpSocket = new DatagramSocket();
        udpSocket.setReuseAddress(true);
        InetAddress address = InetAddress.getByName(targetHost);

        notifyState(State.CONNECTED, "UDP to " + targetHost + ":" + targetPort);

        byte[] localBuf = new byte[TouchRacerPacket.PACKET_SIZE];
        DatagramPacket datagram = new DatagramPacket(localBuf, localBuf.length, address, targetPort);

        // 60 Hz - 80 Hz send interval (~14ms)
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            boolean hasData = false;
            synchronized (packetLock) {
                if (latestPacketData != null) {
                    System.arraycopy(latestPacketData, 0, localBuf, 0, localBuf.length);
                    hasData = true;
                }
            }

            if (hasData) {
                datagram.setData(localBuf);
                udpSocket.send(datagram);
                recordPacketSent();
            }

            Thread.sleep(10); // 100 Hz ultra-low latency send rate
        }
    }

    private void runTcpLoop() throws Exception {
        tcpSocket = new Socket();
        tcpSocket.setTcpNoDelay(true); // Disable Nagle's algorithm for instant transmission!
        tcpSocket.connect(new InetSocketAddress(targetHost, targetPort), 5000);
        tcpOutputStream = tcpSocket.getOutputStream();

        notifyState(State.CONNECTED, "TCP to " + targetHost + ":" + targetPort);

        byte[] localBuf = new byte[TouchRacerPacket.PACKET_SIZE];

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            boolean hasData = false;
            synchronized (packetLock) {
                if (latestPacketData != null) {
                    System.arraycopy(latestPacketData, 0, localBuf, 0, localBuf.length);
                    hasData = true;
                }
            }

            if (hasData) {
                tcpOutputStream.write(localBuf);
                tcpOutputStream.flush();
                recordPacketSent();
            }

            Thread.sleep(10); // 100 Hz ultra-low latency send rate
        }
    }

    private void runBluetoothLoop() throws Exception {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            throw new Exception("Bluetooth is not enabled on this device");
        }

        if (bluetoothDeviceAddress == null || bluetoothDeviceAddress.isEmpty()) {
            throw new Exception("No Bluetooth device selected");
        }

        BluetoothDevice device = adapter.getRemoteDevice(bluetoothDeviceAddress);
        try {
            adapter.cancelDiscovery(); // Cancel discovery to speed up connection
        } catch (SecurityException ignored) {}

        btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        btSocket.connect();
        btOutputStream = btSocket.getOutputStream();

        String devName = bluetoothDeviceAddress;
        try {
            String name = device.getName();
            if (name != null && !name.trim().isEmpty()) {
                devName = name;
            }
        } catch (SecurityException ignored) {}

        notifyState(State.CONNECTED, "Bluetooth to " + devName);
        byte[] localBuf = new byte[TouchRacerPacket.PACKET_SIZE];

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            boolean hasData = false;
            synchronized (packetLock) {
                if (latestPacketData != null) {
                    System.arraycopy(latestPacketData, 0, localBuf, 0, localBuf.length);
                    hasData = true;
                }
            }

            if (hasData) {
                btOutputStream.write(localBuf);
                btOutputStream.flush();
                recordPacketSent();
            }

            Thread.sleep(14);
        }
    }

    private void recordPacketSent() {
        packetCount++;
        long now = System.currentTimeMillis();
        if (now - lastRateCheck >= 1000) {
            final int rate = packetCount;
            packetCount = 0;
            lastRateCheck = now;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        listener.onPacketRateUpdate(rate);
                    }
                }
            });
        }
    }

    private void cleanup() {
        try {
            if (udpSocket != null) {
                udpSocket.close();
                udpSocket = null;
            }
        } catch (Exception ignored) {}

        try {
            if (tcpOutputStream != null) {
                tcpOutputStream.close();
                tcpOutputStream = null;
            }
            if (tcpSocket != null) {
                tcpSocket.close();
                tcpSocket = null;
            }
        } catch (Exception ignored) {}

        try {
            if (btOutputStream != null) {
                btOutputStream.close();
                btOutputStream = null;
            }
            if (btSocket != null) {
                btSocket.close();
                btSocket = null;
            }
        } catch (Exception ignored) {}
    }

    private void notifyState(final State state, final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onStateChanged(state, message);
                }
            }
        });
    }
}
