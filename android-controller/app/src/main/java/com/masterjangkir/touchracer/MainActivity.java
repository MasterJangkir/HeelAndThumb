package com.masterjangkir.touchracer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Main Cockpit Activity for Touch Racer Enhanced Controller.
 * Provides:
 * - Immersive landscape cockpit UI
 * - Multi-touch simultaneous Gas & Brake pedals (Left = Brake, Right = Gas)
 * - Gyroscope & Accelerometer steering with real-time wheel animation
 * - Hardware Volume Buttons mapped to Shifter (Up = Gear Up, Down = Gear Down)
 * - 45° to 180° adjustable steering sensitivity
 * - Wi-Fi (UDP, TCP), Bluetooth, and USB connection support
 */
public class MainActivity extends Activity {

    private SettingsManager settingsManager;
    private NetworkManager networkManager;
    private SteeringProcessor steeringProcessor;
    private TouchRacerPacket packet;
    private Vibrator vibrator;

    // UI elements
    private ArtificialHorizonView horizonGauge;
    private PedalTouchView pedalView;
    private TextView tvStatus;
    private TextView tvPacketRate;
    private Button btnConnect;
    private Button btnCalibrate;
    private Button btnSettings;


    // Packet state
    private float currentSteering = 0.0f;
    private float currentGas = 0.0f;
    private float currentBrake = 0.0f;
    private volatile int shiftUpBuffer = 0;
    private volatile int shiftDownBuffer = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applyOrientationLock();
        // Immersive full screen and keep screen awake
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        hideSystemUI();

        setContentView(R.layout.activity_main);

        settingsManager = new SettingsManager(this);
        packet = new TouchRacerPacket();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initViews();
        initSteering();
        initPedals();
        initNetwork();
        initRacingButtons();
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void initViews() {
        horizonGauge = findViewById(R.id.horizonGauge);
        pedalView = findViewById(R.id.pedalView);
        tvStatus = findViewById(R.id.tvStatus);
        tvPacketRate = findViewById(R.id.tvPacketRate);
        btnConnect = findViewById(R.id.btnConnect);
        btnCalibrate = findViewById(R.id.btnCalibrate);
        btnSettings = findViewById(R.id.btnSettings);


        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (networkManager.isConnected()) {
                    networkManager.disconnect();
                } else {
                    networkManager.setConfig(
                            settingsManager.getConnectionType(),
                            settingsManager.getHost(),
                            settingsManager.getPort(),
                            settingsManager.getBluetoothAddress()
                    );
                    networkManager.connect();
                }
            }
        });

        btnCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (steeringProcessor != null) {
                    steeringProcessor.calibrateCenter();
                    vibrate(40);
                    Toast.makeText(MainActivity.this, "Steering zero calibrated!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });
    }

    private void initSteering() {
        steeringProcessor = new SteeringProcessor(this, new SteeringProcessor.OnSteeringUpdateListener() {
            @Override
            public void onSteeringUpdate(float normalizedSteering, float rawAngleDegrees) {
                currentSteering = normalizedSteering;
                horizonGauge.setSteering(normalizedSteering, rawAngleDegrees);
                updateAndSendPacket();
            }
        });
        steeringProcessor.setSensitivity(settingsManager.getSteeringSensitivity());
        steeringProcessor.setDeadzone(settingsManager.getSteeringDeadzone());
    }
    private void applyOrientationLock() {
        if (settingsManager != null && settingsManager.isReverseLandscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void initPedals() {
        pedalView.setControlMode(settingsManager.getPedalControlMode());
        pedalView.setOnPedalChangeListener(new PedalTouchView.OnPedalChangeListener() {
            @Override
            public void onPedalsChanged(float gasRatio, float brakeRatio) {
                currentGas = gasRatio;
                currentBrake = brakeRatio;
                updateAndSendPacket();
            }
        });
    }

    private void initNetwork() {
        networkManager = new NetworkManager(new NetworkManager.ConnectionListener() {
            @Override
            public void onStateChanged(NetworkManager.State state, String message) {
                tvStatus.setText(message);
                switch (state) {
                    case CONNECTED:
                        tvStatus.setTextColor(Color.argb(255, 60, 240, 60));
                        btnConnect.setText("DISCONNECT");
                        vibrate(100);
                        break;
                    case CONNECTING:
                        tvStatus.setTextColor(Color.argb(255, 255, 200, 40));
                        btnConnect.setText("CANCEL");
                        break;
                    case ERROR:
                        tvStatus.setTextColor(Color.argb(255, 255, 60, 60));
                        btnConnect.setText("CONNECT");
                        break;
                    case DISCONNECTED:
                        tvStatus.setTextColor(Color.argb(255, 180, 180, 180));
                        btnConnect.setText("CONNECT");
                        tvPacketRate.setText("0 Hz");
                        break;
                }
            }

            @Override
            public void onPacketRateUpdate(int packetsPerSecond) {
                tvPacketRate.setText(packetsPerSecond + " Hz");
            }
        });
    }

    private void initRacingButtons() {
        setupMomentaryButton(R.id.btnHandbrake, 2); // Button 3 (Dedicated Handbrake)
        setupMomentaryButton(R.id.btnBoost, 3);      // Button 4 (NOS/Boost)
        setupMomentaryButton(R.id.btnReset, 4);      // Button 5 (Reset)
        setupMomentaryButton(R.id.btnCamera, 5);     // Button 6 (Camera)
        setupMomentaryButton(R.id.btnPause, 6);      // Button 7 (Pause)
        setupMomentaryButton(R.id.btnHorn, 7);       // Button 8 (Horn)

        // On-screen gear shift buttons
        View btnShiftUp = findViewById(R.id.btnShiftUp);
        if (btnShiftUp != null) {
            btnShiftUp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shiftUpBuffer = 15;
                    shiftDownBuffer = 0;
                    updateAndSendPacket();
                    vibrate(40);
                }
            });
        }

        View btnShiftDown = findViewById(R.id.btnShiftDown);
        if (btnShiftDown != null) {
            btnShiftDown.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shiftDownBuffer = 15;
                    shiftUpBuffer = 0;
                    updateAndSendPacket();
                    vibrate(40);
                }
            });
        }
    }

    private void setupMomentaryButton(int viewId, final int buttonIndex) {
        View btn = findViewById(viewId);
        if (btn == null) return;

        btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    packet.setButton(buttonIndex, true);
                    vibrate(30);
                    updateAndSendPacket();
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    packet.setButton(buttonIndex, false);
                    updateAndSendPacket();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Intercept hardware Volume Buttons to act as Gear Shifters (Volume Up = Shift Up, Volume Down = Shift Down).
     * Suppresses default system volume dialog!
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (settingsManager.isVolumeShifterEnabled()) {
            if (event.getRepeatCount() > 0) {
                return true; // Ignore repeat when volume button is held!
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                shiftUpBuffer = 15;
                shiftDownBuffer = 0;
                updateAndSendPacket();
                vibrate(40);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                shiftDownBuffer = 15;
                shiftUpBuffer = 0;
                updateAndSendPacket();
                vibrate(40);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateAndSendPacket() {
        packet.setSteering(currentSteering);
        packet.setPedals(currentGas, currentBrake);

        if (shiftUpBuffer > 0) {
            packet.setButton(0, true);
            shiftUpBuffer--;
        } else {
            packet.setButton(0, false);
        }

        if (shiftDownBuffer > 0) {
            packet.setButton(1, true);
            shiftDownBuffer--;
        } else {
            packet.setButton(1, false);
        }

        if (networkManager != null && networkManager.isConnected()) {
            networkManager.sendPacket(packet.build());
        }
    }

    private void vibrate(long ms) {
        if (!settingsManager.isHapticEnabled() || vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    private void showSettingsDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_settings, null);

        final EditText etHost = dialogView.findViewById(R.id.etHost);
        final EditText etPort = dialogView.findViewById(R.id.etPort);
        final Spinner spConnType = dialogView.findViewById(R.id.spConnType);
        final Spinner spBluetooth = dialogView.findViewById(R.id.spBluetoothDevice);
        final SeekBar sbSensitivity = dialogView.findViewById(R.id.sbSensitivity);
        final TextView tvSensitivityVal = dialogView.findViewById(R.id.tvSensitivityVal);
        final SeekBar sbDeadzone = dialogView.findViewById(R.id.sbDeadzone);
        final TextView tvDeadzoneVal = dialogView.findViewById(R.id.tvDeadzoneVal);
        final CheckBox cbInvertPedals = dialogView.findViewById(R.id.cbInvertPedals);
        final RadioGroup rgMixMode = dialogView.findViewById(R.id.rgMixMode);
        final CheckBox cbVolumeShifter = dialogView.findViewById(R.id.cbVolumeShifter);
        final CheckBox cbHaptic = dialogView.findViewById(R.id.cbHaptic);
        final CheckBox cbReverseLandscape = dialogView.findViewById(R.id.cbReverseLandscape);
        cbReverseLandscape.setChecked(settingsManager.isReverseLandscape());

        etHost.setText(settingsManager.getHost());
        etPort.setText(String.valueOf(settingsManager.getPort()));

        // Connection types
        ArrayAdapter<String> connAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Wi-Fi UDP (Ultra Fast)", "Wi-Fi TCP", "Bluetooth", "USB (Tether / ADB)"});
        spConnType.setAdapter(connAdapter);
        spConnType.setSelection(settingsManager.getConnectionType().ordinal());

        // Bluetooth devices list
        final List<String> btNames = new ArrayList<>();
        final List<String> btAddresses = new ArrayList<>();
        btNames.add("None / Auto");
        btAddresses.add("");

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null && btAdapter.isEnabled()) {
            Set<BluetoothDevice> paired = btAdapter.getBondedDevices();
            if (paired != null) {
                for (BluetoothDevice dev : paired) {
                    btNames.add(dev.getName() + " (" + dev.getAddress() + ")");
                    btAddresses.add(dev.getAddress());
                }
            }
        }
        ArrayAdapter<String> btAdapterList = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, btNames);
        spBluetooth.setAdapter(btAdapterList);

        String savedBt = settingsManager.getBluetoothAddress();
        int btIdx = btAddresses.indexOf(savedBt);
        if (btIdx >= 0) spBluetooth.setSelection(btIdx);

        // Steering Sensitivity: 45° to 180°
        // SeekBar range: 0 to 135 (progress + 45 = degrees)
        float currentSens = settingsManager.getSteeringSensitivity();
        sbSensitivity.setMax(135);
        sbSensitivity.setProgress(Math.round(currentSens - 45f));
        tvSensitivityVal.setText(Math.round(currentSens) + "°");

        sbSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int deg = progress + 45;
                tvSensitivityVal.setText(deg + "°");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Deadzone: 0% to 15%
        float currentDz = settingsManager.getSteeringDeadzone();
        sbDeadzone.setMax(15);
        sbDeadzone.setProgress(Math.round(currentDz * 100f));
        tvDeadzoneVal.setText(Math.round(currentDz * 100f) + "%");

        sbDeadzone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvDeadzoneVal.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        cbInvertPedals.setChecked(settingsManager.isInvertPedals());

        if (settingsManager.getMixMode() == TouchRacerPacket.MIX_BRAKE_PRIORITY) {
            rgMixMode.check(R.id.rbMixBrakePriority);
        } else {
            rgMixMode.check(R.id.rbMixAnalogBlend);
        }

        cbVolumeShifter.setChecked(settingsManager.isVolumeShifterEnabled());
        cbHaptic.setChecked(settingsManager.isHapticEnabled());

        new AlertDialog.Builder(this)
                .setTitle("Touch Racer Controller Settings")
                .setView(dialogView)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        settingsManager.setHost(etHost.getText().toString().trim());
                        try {
                            settingsManager.setPort(Integer.parseInt(etPort.getText().toString().trim()));
                        } catch (Exception ignored) {}

                        NetworkManager.ConnectionType selectedType =
                                NetworkManager.ConnectionType.values()[spConnType.getSelectedItemPosition()];
                        settingsManager.setConnectionType(selectedType);

                        int btPos = spBluetooth.getSelectedItemPosition();
                        if (btPos >= 0 && btPos < btAddresses.size()) {
                            settingsManager.setBluetoothAddress(btAddresses.get(btPos));
                        }

                        float sens = sbSensitivity.getProgress() + 45f;
                        settingsManager.setSteeringSensitivity(sens);
                        if (steeringProcessor != null) {
                            steeringProcessor.setSensitivity(sens);
                        }

                        float dz = sbDeadzone.getProgress() / 100.0f;
                        settingsManager.setSteeringDeadzone(dz);
                        if (steeringProcessor != null) {
                            steeringProcessor.setDeadzone(dz);
                        }

                        settingsManager.setInvertPedals(cbInvertPedals.isChecked());

                        int mix = rgMixMode.getCheckedRadioButtonId() == R.id.rbMixBrakePriority
                                ? TouchRacerPacket.MIX_BRAKE_PRIORITY
                                : TouchRacerPacket.MIX_ANALOG_BLEND;
                        settingsManager.setMixMode(mix);
                        settingsManager.setReverseLandscape(cbReverseLandscape.isChecked());
                        applyOrientationLock();

                        settingsManager.setVolumeShifterEnabled(cbVolumeShifter.isChecked());
                        settingsManager.setHapticEnabled(cbHaptic.isChecked());

                        Toast.makeText(MainActivity.this, "Settings Saved!", Toast.LENGTH_SHORT).show();
                        hideSystemUI();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        hideSystemUI();
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (steeringProcessor != null) {
            steeringProcessor.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (steeringProcessor != null) {
            steeringProcessor.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkManager != null) {
            networkManager.disconnect();
        }
        if (steeringProcessor != null) {
            steeringProcessor.stop();
        }
    }
}
