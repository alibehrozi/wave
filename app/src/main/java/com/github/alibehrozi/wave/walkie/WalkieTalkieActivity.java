package com.github.alibehrozi.wave.walkie;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.github.alibehrozi.wave.R;
import com.github.alibehrozi.wave.WaveApplication;
import com.github.alibehrozi.wave.adapters.VoiceEffectAdapter;
import com.github.alibehrozi.wave.models.ChannelBookmark;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDevice;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDeviceInfo;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrErrorType;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.manager.SdrManager;

import java.util.List;
import java.util.Locale;

/**
 * Professional Walkie Talkie Transceiver Activity.
 * Provides half-duplex voice communication with tactile PTT (Push-To-Talk),
 * live audio oscilloscope visualization, roger beeps, frequency tuning, and persistent bookmarks.
 */
public class WalkieTalkieActivity extends ComponentActivity {

    private static final String TAG = "WalkieTalkieActivity";
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private WalkieTalkieFlowgraphManager flowgraphManager;
    private BookmarkManager bookmarkManager;
    private Vibrator vibrator;

    // UI Elements
    private TextView tvHardwareBadge;
    private TextView tvChannelBadge;
    private TextView tvFrequencyDisplay;
    private TextView tvPttStatusBadge;
    private TextView tvPttActionText;
    private TextView tvTxTimer;
    private TextView tvSquelchLabel;
    private TextView chipCtcss;

    // Voice Changer UI
    private TextView tvVoiceFxBadge;
    private RecyclerView recyclerVoiceEffects;
    private VoiceEffectAdapter voiceEffectAdapter;

    private ProgressBar progressSMeter;
    private View viewPttGlowRing;
    private LinearLayout btnPtt;
    private ImageView ivPttIcon;

    private ImageButton btnBack;
    private ImageButton btnSettings;
    private ImageButton btnBookmarkCurrent;
    private View btnDirectFrequencyInput;
    private TextView btnAddBookmarkQuick;

    private Button btnStepMinus1M;
    private Button btnStepMinus12k;
    private Button btnStepPlus12k;
    private Button btnStepPlus1M;

    private SeekBar seekSquelch;
    private LinearLayout layoutBookmarksContainer;

    // Band chips & Mode
    private TextView chipFmMode;
    private TextView chipBandFm;
    private TextView chipBandPmr;
    private TextView chipBandFrs;
    private TextView chipBand2m;
    private TextView chipBand70cm;
    private TextView chipBandMarine;

    // RF State
    private double currentFrequencyHz = 446.00625e6; // Default PMR 1
    private String currentChannelName = "PMR CH 1";
    private boolean isMuted = false;
    private long txStartTime = 0;
    private TextView btnTxTestTone;
    private boolean isTestToneActive = false;

    // CTCSS sub-tones
    private static final double[] CTCSS_TONES = { 0.0, 67.0, 71.9, 77.0, 88.5, 100.0, 123.0, 141.3 };
    private int ctcssIndex = 0;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable txTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (flowgraphManager != null && flowgraphManager.isTransmitting()) {
                long elapsed = SystemClock.elapsedRealtime() - txStartTime;
                long seconds = elapsed / 1000;
                long tenths = (elapsed % 1000) / 100;
                tvTxTimer.setText(String.format(Locale.US, "%02d:%02d.%d", seconds / 60, seconds % 60, tenths));
                uiHandler.postDelayed(this, 100);
            }
        }
    };

    private final SdrManager.Listener sdrListener = new SdrManager.Listener() {
        @Override
        public void onSdrConnected(@NonNull SdrDevice sdr, @NonNull UsbDevice device) {
            runOnUiThread(() -> {
                String name = sdr.getDeviceInfo() != null ? sdr.getDeviceInfo().getProduct() : null;
                if (name == null || name.isEmpty()) name = device.getProductName();
                if (name == null || name.isEmpty()) name = "SDR Radio";

                tvHardwareBadge.setText(name.toUpperCase(Locale.US) + " ACTIVE");
                tvHardwareBadge.setTextColor(0xFF1A7F37); // status_green
                Toast.makeText(WalkieTalkieActivity.this, "SDR Online: " + name, Toast.LENGTH_SHORT).show();

                if (flowgraphManager != null) {
                    flowgraphManager.onSdrConnected(sdr);
                }
            });
        }

        @Override
        public void onSdrDisconnected(@NonNull UsbDevice device, @Nullable SdrDeviceInfo deviceInfo) {
            runOnUiThread(() -> {
                tvHardwareBadge.setText("INTERNAL RF SIM");
                tvHardwareBadge.setTextColor(0xFF57606A); // text_secondary
                Toast.makeText(WalkieTalkieActivity.this, "SDR Disconnected", Toast.LENGTH_SHORT).show();

                if (flowgraphManager != null) {
                    flowgraphManager.onSdrDisconnected();
                }
            });
        }

        @Override
        public void onError(@NonNull SdrErrorType errorType, @Nullable String message) {
            runOnUiThread(() -> {
                Log.e(TAG, "SDR Error: " + errorType + " - " + message);
                Toast.makeText(WalkieTalkieActivity.this, "SDR Error: " + (message != null ? message : errorType.name()), Toast.LENGTH_SHORT).show();
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_walkie_talkie);

        setupWindowInsets();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        bookmarkManager = new BookmarkManager(this);
        flowgraphManager = new WalkieTalkieFlowgraphManager(this);

        initViews();
        setupListeners();
        renderBookmarks();
        updateFrequencyUI();

        handleUsbIntent(getIntent());
        checkAudioPermission();
    }

    private void setupWindowInsets() {
        View rootView = findViewById(R.id.root_layout);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleUsbIntent(intent);
    }

    private void handleUsbIntent(Intent intent) {
        if (intent != null && UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
                Log.i(TAG, "USB Device Attached from Intent: " + device.getProductName());
                SdrManager sdrManager = WaveApplication.getSdrManager();
                if (sdrManager != null) {
                    sdrManager.connect(device);
                }
            }
        }
    }

    private void initViews() {
        tvHardwareBadge = findViewById(R.id.tvHardwareBadge);
        tvChannelBadge = findViewById(R.id.tvChannelBadge);
        tvFrequencyDisplay = findViewById(R.id.tvFrequencyDisplay);
        tvPttStatusBadge = findViewById(R.id.tvPttStatusBadge);
        tvPttActionText = findViewById(R.id.tvPttActionText);
        tvTxTimer = findViewById(R.id.tvTxTimer);
        tvSquelchLabel = findViewById(R.id.tvSquelchLabel);
        chipCtcss = findViewById(R.id.chipCtcss);

        btnSettings = findViewById(R.id.btn_settings);
        tvVoiceFxBadge = findViewById(R.id.tv_voice_fx_badge);
        recyclerVoiceEffects = findViewById(R.id.recycler_voice_effects);

        progressSMeter = findViewById(R.id.progressSMeter);
        viewPttGlowRing = findViewById(R.id.viewPttGlowRing);
        btnPtt = findViewById(R.id.btnPtt);
        ivPttIcon = findViewById(R.id.ivPttIcon);

        btnBack = findViewById(R.id.btnBack);
        btnBookmarkCurrent = findViewById(R.id.btnBookmarkCurrent);
        btnDirectFrequencyInput = findViewById(R.id.btnDirectFrequencyInput);
        btnAddBookmarkQuick = findViewById(R.id.btnAddBookmarkQuick);

        btnStepMinus1M = findViewById(R.id.btnStepMinus1M);
        btnStepMinus12k = findViewById(R.id.btnStepMinus12k);
        btnStepPlus12k = findViewById(R.id.btnStepPlus12k);
        btnStepPlus1M = findViewById(R.id.btnStepPlus1M);

        seekSquelch = findViewById(R.id.seekSquelch);
        layoutBookmarksContainer = findViewById(R.id.layoutBookmarksContainer);

        chipFmMode = findViewById(R.id.chipFmMode);
        chipBandFm = findViewById(R.id.chipBandFm);
        chipBandPmr = findViewById(R.id.chipBandPmr);
        chipBandFrs = findViewById(R.id.chipBandFrs);
        chipBand2m = findViewById(R.id.chipBand2m);
        chipBand70cm = findViewById(R.id.chipBand70cm);
        chipBandMarine = findViewById(R.id.chipBandMarine);
        btnTxTestTone = findViewById(R.id.btnTxTestTone);

        initVoiceEffects();
        updateFmModeUI();

        // Hardware status update
        if (flowgraphManager.isHardwareConnected()) {
            tvHardwareBadge.setText(flowgraphManager.getDeviceName().toUpperCase(Locale.US));
            tvHardwareBadge.setTextColor(0xFF1A7F37);
        } else {
            tvHardwareBadge.setText("SIM RF MODE");
            tvHardwareBadge.setTextColor(0xFF0969DA);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsDialog());
        }

        // Dedicated 1 kHz Test Tone Transmitter Toggle
        if (btnTxTestTone != null) {
            btnTxTestTone.setOnClickListener(v -> toggleTestToneTransmission());
        }

        // Modulation Mode Toggle (NFM / WFM)
        chipFmMode.setOnClickListener(v -> {
            WalkieTalkieFlowgraphManager.FmMode current = flowgraphManager.getFmMode();
            WalkieTalkieFlowgraphManager.FmMode next = (current == WalkieTalkieFlowgraphManager.FmMode.WFM)
                    ? WalkieTalkieFlowgraphManager.FmMode.NFM
                    : WalkieTalkieFlowgraphManager.FmMode.WFM;
            flowgraphManager.setFmMode(next);
            updateFmModeUI();
            triggerHapticPulse(30);
            Toast.makeText(this, "Modulation: " + next.displayName, Toast.LENGTH_SHORT).show();
        });

        // PTT Touch Listener (Hold to talk)
        btnPtt.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startTransmission();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopTransmission();
                    return true;
            }
            return false;
        });

        // Frequency Step Buttons
        btnStepMinus1M.setOnClickListener(v -> adjustFrequency(-1.0e6));
        btnStepMinus12k.setOnClickListener(v -> adjustFrequency(-12.5e3));
        btnStepPlus12k.setOnClickListener(v -> adjustFrequency(12.5e3));
        btnStepPlus1M.setOnClickListener(v -> adjustFrequency(1.0e6));

        // Direct keypad tuning
        btnDirectFrequencyInput.setOnClickListener(v -> showDirectFrequencyDialog());

        // Bookmarks
        btnBookmarkCurrent.setOnClickListener(v -> showAddBookmarkDialog());
        btnAddBookmarkQuick.setOnClickListener(v -> showAddBookmarkDialog());

        // Squelch slider
        seekSquelch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float db = -80.0f + progress;
                tvSquelchLabel.setText(String.format(Locale.US, "SQL: %ddB", (int) db));
                flowgraphManager.setSquelch(db);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // CTCSS sub-tone cycle
        chipCtcss.setOnClickListener(v -> {
            ctcssIndex = (ctcssIndex + 1) % CTCSS_TONES.length;
            double tone = CTCSS_TONES[ctcssIndex];
            if (tone == 0.0) {
                chipCtcss.setText("CTCSS: OFF");
                chipCtcss.setTextColor(0xFF57606A);
            } else {
                chipCtcss.setText(String.format(Locale.US, "CTCSS: %.1fHz", tone));
                chipCtcss.setTextColor(0xFF0969DA);
            }
        });

        // Radio Band Presets
        setupBandChips();

        // Audio Level Callback for VU meter and PTT glow
        flowgraphManager.setAudioLevelListener((rmsLevel, waveform, isTx) -> runOnUiThread(() -> {
            if (isTx) {
                // Dynamic outer ring scale based on voice volume
                float scale = 1.0f + (rmsLevel * 0.25f);
                viewPttGlowRing.setScaleX(scale);
                viewPttGlowRing.setScaleY(scale);
                progressSMeter.setProgress((int) (rmsLevel * 100));
            }
        }));
    }

    private void initVoiceEffects() {
        if (recyclerVoiceEffects == null) return;
        recyclerVoiceEffects.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        voiceEffectAdapter = new VoiceEffectAdapter(VoiceChanger.getAllEffects(), effect -> {
            flowgraphManager.setVoiceEffect(effect);
            if (tvVoiceFxBadge != null) {
                tvVoiceFxBadge.setText(effect.title.toUpperCase(Locale.US));
            }
            triggerHapticPulse(25);
            Toast.makeText(this, "Voice Effect: " + effect.title, Toast.LENGTH_SHORT).show();
        });
        recyclerVoiceEffects.setAdapter(voiceEffectAdapter);
    }

    private void showSettingsDialog() {
        boolean currentlyMuted = (flowgraphManager != null && flowgraphManager.isMuted()) || isMuted;
        String muteOption = currentlyMuted ? "🔊 Unmute RX Speaker (Currently Muted)" : "🔇 Mute RX Speaker (Currently Active)";

        String[] options = new String[]{
                muteOption,
                "🎚️ Reset Squelch (-50 dB)",
                "ℹ️ Hardware & Radio Diagnostics",
                "Roger Beep: Classic Motorola Dual-Tone",
                "TX Mic Gain: High (+6 dB)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Walkie Talkie Settings")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            toggleMute();
                            break;
                        case 1:
                            if (seekSquelch != null) {
                                seekSquelch.setProgress(30);
                            }
                            if (flowgraphManager != null) {
                                flowgraphManager.setSquelch(-50.0f);
                            }
                            if (tvSquelchLabel != null) {
                                tvSquelchLabel.setText("SQL: -50dB");
                            }
                            Toast.makeText(this, "Squelch reset to default (-50 dB)", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            showRadioDiagnosticsDialog();
                            break;
                        default:
                            Toast.makeText(this, options[which], Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .setPositiveButton("Close", null)
                .show();
    }

    private void setupBandChips() {
        chipBandFm.setOnClickListener(v -> {
            selectBand(chipBandFm);
            setExactFrequency(98.500e6, "FM BROADCAST");
            flowgraphManager.setFmMode(WalkieTalkieFlowgraphManager.FmMode.WFM);
            updateFmModeUI();
        });

        chipBandPmr.setOnClickListener(v -> {
            selectBand(chipBandPmr);
            setExactFrequency(446.00625e6, "PMR CH 1");
            flowgraphManager.setFmMode(WalkieTalkieFlowgraphManager.FmMode.NFM);
            updateFmModeUI();
        });

        chipBandFrs.setOnClickListener(v -> {
            selectBand(chipBandFrs);
            setExactFrequency(462.5625e6, "FRS CH 1");
            flowgraphManager.setFmMode(WalkieTalkieFlowgraphManager.FmMode.NFM);
            updateFmModeUI();
        });

        chipBand2m.setOnClickListener(v -> {
            selectBand(chipBand2m);
            setExactFrequency(146.520e6, "2M CALLING");
            flowgraphManager.setFmMode(WalkieTalkieFlowgraphManager.FmMode.NFM);
            updateFmModeUI();
        });

        chipBand70cm.setOnClickListener(v -> {
            selectBand(chipBand70cm);
            setExactFrequency(446.000e6, "70CM CALLING");
            flowgraphManager.setFmMode(WalkieTalkieFlowgraphManager.FmMode.NFM);
            updateFmModeUI();
        });

        chipBandMarine.setOnClickListener(v -> {
            selectBand(chipBandMarine);
            setExactFrequency(156.800e6, "MARINE 16");
            flowgraphManager.setFmMode(WalkieTalkieFlowgraphManager.FmMode.NFM);
            updateFmModeUI();
        });
    }

    private void selectBand(TextView selected) {
        TextView[] chips = { chipBandFm, chipBandPmr, chipBandFrs, chipBand2m, chipBand70cm, chipBandMarine };
        for (TextView chip : chips) {
            boolean isSel = (chip == selected);
            chip.setBackgroundResource(isSel ? R.drawable.bg_walkie_chip_selected : R.drawable.bg_walkie_chip_unselected);
            chip.setTextColor(isSel ? 0xFFFFFFFF : 0xFF57606A);
        }
    }

    private void startTransmission() {
        triggerHapticPulse(40);
        txStartTime = SystemClock.elapsedRealtime();

        // Update UI to TX state
        btnPtt.setBackgroundResource(R.drawable.bg_ptt_active);
        viewPttGlowRing.setBackgroundResource(R.drawable.bg_ptt_active);
        ivPttIcon.setColorFilter(0xFFCF222E);
        tvPttActionText.setText("TRANSMITTING");
        tvPttStatusBadge.setText("• TX ON AIR •");
        tvPttStatusBadge.setTextColor(0xFFCF222E);

        uiHandler.post(txTimerRunnable);
        flowgraphManager.startTx();
    }

    private void stopTransmission() {
        triggerHapticPulse(25);
        uiHandler.removeCallbacks(txTimerRunnable);

        if (isTestToneActive) {
            isTestToneActive = false;
            flowgraphManager.setTestToneEnabled(false);
            if (btnTxTestTone != null) {
                btnTxTestTone.setText("⚡ 1 kHz TEST TONE");
                btnTxTestTone.setBackgroundResource(R.drawable.bg_frequency_chip);
                btnTxTestTone.setTextColor(0xFFBC4C00);
            }
        }

        // Update UI back to RX state
        btnPtt.setBackgroundResource(R.drawable.bg_ptt_standby);
        viewPttGlowRing.setBackgroundResource(R.drawable.bg_ptt_standby);
        viewPttGlowRing.setScaleX(1.0f);
        viewPttGlowRing.setScaleY(1.0f);
        ivPttIcon.setColorFilter(0xFF1A7F37);
        tvPttActionText.setText("HOLD TO TALK");
        tvTxTimer.setText("RELEASE FOR BEEP");
        tvPttStatusBadge.setText("• RX MONITORING •");
        tvPttStatusBadge.setTextColor(0xFF1A7F37);
        progressSMeter.setProgress(20);

        flowgraphManager.stopTx();
    }

    private void toggleTestToneTransmission() {
        if (isTestToneActive) {
            stopTransmission();
            Toast.makeText(this, "Test tone stopped", Toast.LENGTH_SHORT).show();
        } else {
            isTestToneActive = true;
            flowgraphManager.setTestToneEnabled(true);
            triggerHapticPulse(40);
            txStartTime = SystemClock.elapsedRealtime();

            btnPtt.setBackgroundResource(R.drawable.bg_ptt_active);
            viewPttGlowRing.setBackgroundResource(R.drawable.bg_ptt_active);
            ivPttIcon.setColorFilter(0xFFFFFFFF);
            tvPttActionText.setText("TEST TONE");
            tvPttStatusBadge.setText("• 1 kHz TONE ON AIR •");
            tvPttStatusBadge.setTextColor(0xFFF59E0B);

            if (btnTxTestTone != null) {
                btnTxTestTone.setText("⏹ STOP TEST TONE");
                btnTxTestTone.setBackgroundResource(R.drawable.bg_walkie_chip_selected);
                btnTxTestTone.setTextColor(0xFFFFFFFF);
            }

            uiHandler.post(txTimerRunnable);
            flowgraphManager.startTx();
            Toast.makeText(this, "Transmitting 1 kHz tone at " + String.format(Locale.US, "%.4f MHz", currentFrequencyHz / 1e6), Toast.LENGTH_SHORT).show();
        }
    }

    private void adjustFrequency(double deltaHz) {
        currentFrequencyHz = Math.max(1.0e6, currentFrequencyHz + deltaHz);
        currentChannelName = "MANUAL";
        updateFrequencyUI();
    }

    private void setExactFrequency(double freqHz, String channelName) {
        this.currentFrequencyHz = freqHz;
        this.currentChannelName = channelName;
        updateFrequencyUI();
    }

    private void updateFrequencyUI() {
        double mhz = currentFrequencyHz / 1.0e6;
        tvFrequencyDisplay.setText(String.format(Locale.US, "%.6f MHz", mhz));
        tvChannelBadge.setText(currentChannelName);

        boolean bookmarked = bookmarkManager.isFrequencyBookmarked(currentFrequencyHz);
        btnBookmarkCurrent.setImageResource(R.drawable.ic_star_24);
        btnBookmarkCurrent.setColorFilter(bookmarked ? 0xFFBC4C00 : 0xFF8C959F);

        if (currentFrequencyHz >= 87.0e6 && currentFrequencyHz <= 108.5e6) {
            flowgraphManager.setFmMode(WalkieTalkieFlowgraphManager.FmMode.WFM);
        }

        flowgraphManager.setFrequency(currentFrequencyHz);
        updateFmModeUI();
    }

    private void updateFmModeUI() {
        if (chipFmMode == null || flowgraphManager == null) return;
        WalkieTalkieFlowgraphManager.FmMode mode = flowgraphManager.getFmMode();
        if (mode == WalkieTalkieFlowgraphManager.FmMode.WFM) {
            chipFmMode.setText("WFM 75k");
            chipFmMode.setTextColor(0xFF0969DA);
            chipFmMode.setBackgroundResource(R.drawable.bg_frequency_chip);
        } else {
            chipFmMode.setText("NFM 5k");
            chipFmMode.setTextColor(0xFF1A7F37);
            chipFmMode.setBackgroundResource(R.drawable.bg_frequency_chip);
        }
    }

    private void renderBookmarks() {
        layoutBookmarksContainer.removeAllViews();
        List<ChannelBookmark> bookmarks = bookmarkManager.getBookmarks();

        for (ChannelBookmark b : bookmarks) {
            TextView chip = new TextView(this);
            chip.setText("★ " + b.getName());
            chip.setTextSize(11);
            chip.setTextColor(0xFF1F2328);
            chip.setBackgroundResource(R.drawable.bg_frequency_chip);
            chip.setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dpToPx(6));
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                setExactFrequency(b.getFrequencyHz(), b.getName());
                Toast.makeText(this, "Tuned to " + b.getName(), Toast.LENGTH_SHORT).show();
            });

            chip.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Remove Bookmark")
                        .setMessage("Delete '" + b.getName() + "' from saved channels?")
                        .setPositiveButton("Delete", (d, which) -> {
                            bookmarkManager.removeBookmark(b.getId());
                            renderBookmarks();
                            updateFrequencyUI();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });

            layoutBookmarksContainer.addView(chip);
        }
    }

    private void showDirectFrequencyDialog() {
        EditText input = new EditText(this);
        input.setText(String.format(Locale.US, "%.4f", currentFrequencyHz / 1e6));
        input.setSelectAllOnFocus(true);
        input.setTextColor(0xFF0F172A);

        new AlertDialog.Builder(this)
                .setTitle("Tune Frequency (MHz)")
                .setView(input)
                .setPositiveButton("Tune", (dialog, which) -> {
                    try {
                        double mhz = Double.parseDouble(input.getText().toString().trim());
                        setExactFrequency(mhz * 1.0e6, "MANUAL");
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid frequency", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddBookmarkDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_bookmark, null);
        TextView tvDialogFreq = dialogView.findViewById(R.id.tvDialogFrequency);
        EditText etName = dialogView.findViewById(R.id.etBookmarkName);

        tvDialogFreq.setText(String.format(Locale.US, "%.6f MHz", currentFrequencyHz / 1.0e6));
        etName.setText(currentChannelName.equals("MANUAL") ? "Channel" : currentChannelName);
        etName.selectAll();

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) name = String.format(Locale.US, "%.4f MHz", currentFrequencyHz / 1e6);
                    ChannelBookmark newBookmark = new ChannelBookmark(name, currentFrequencyHz);
                    bookmarkManager.addBookmark(newBookmark);
                    currentChannelName = name;
                    renderBookmarks();
                    updateFrequencyUI();
                    Toast.makeText(this, "Bookmark saved: " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void triggerHapticPulse(int durationMs) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMs);
            }
        }
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.RECORD_AUDIO },
                    REQUEST_RECORD_AUDIO
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone enabled for PTT", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Microphone permission is required for Walkie Talkie transmission", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;
        if (flowgraphManager != null) {
            flowgraphManager.setMuted(isMuted);
        }
        triggerHapticPulse(25);
        Toast.makeText(this, isMuted ? "Speaker Muted" : "Speaker Unmuted", Toast.LENGTH_SHORT).show();
    }

    private void showRadioDiagnosticsDialog() {
        String dev = (flowgraphManager != null && flowgraphManager.isHardwareConnected())
                ? flowgraphManager.getDeviceName()
                : "Simulation (Internal Loopback)";
        String mode = (flowgraphManager != null) ? flowgraphManager.getFmMode().displayName : "NFM";
        String freq = String.format(Locale.US, "%.4f MHz", currentFrequencyHz / 1e6);
        String voiceFx = (flowgraphManager != null) ? flowgraphManager.getVoiceEffect().title : "Natural";
        String muteState = isMuted ? "Muted (Silent)" : "Active (Playing)";

        String message = "Hardware SDR: " + dev + "\n"
                + "Tuned Frequency: " + freq + "\n"
                + "Modulation: " + mode + "\n"
                + "Voice Changer: " + voiceFx + "\n"
                + "Audio Output: " + muteState;

        new AlertDialog.Builder(this)
                .setTitle("Radio Diagnostics")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SdrManager sdrManager = WaveApplication.getSdrManager();
        if (sdrManager != null) {
            sdrManager.addListener(sdrListener);

            List<SdrDevice> connected = sdrManager.getConnectedDevices();
            if (!connected.isEmpty()) {
                SdrDevice sdr = connected.get(0);
                String name = sdr.getDeviceInfo() != null ? sdr.getDeviceInfo().getProduct() : "SDR Radio";
                tvHardwareBadge.setText(name.toUpperCase(Locale.US) + " ACTIVE");
                tvHardwareBadge.setTextColor(0xFF10B981);
                if (flowgraphManager != null) {
                    flowgraphManager.onSdrConnected(sdr);
                }
            } else {
                List<UsbDevice> available = sdrManager.getAvailableUsbDevices();
                if (!available.isEmpty()) {
                    sdrManager.connect(available.get(0));
                } else {
                    tvHardwareBadge.setText("INTERNAL RF SIM");
                    tvHardwareBadge.setTextColor(0xFF94A3B8);
                }
            }
        }

        if (flowgraphManager != null) {
            flowgraphManager.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SdrManager sdrManager = WaveApplication.getSdrManager();
        if (sdrManager != null) {
            sdrManager.removeListener(sdrListener);
        }
        if (flowgraphManager != null) {
            flowgraphManager.stop();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
