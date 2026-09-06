package com.github.alibehrozi.wave;

import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.alibehrozi.wave.adapters.DashboardToolsAdapter;
import com.github.alibehrozi.wave.adapters.SdrDeviceAdapter;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDevice;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDeviceInfo;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.manager.SdrManager;
import com.github.alibehrozi.wave.models.DashboardPresetsProvider;
import com.github.alibehrozi.wave.models.DashboardToolItem;

import java.util.Collections;
import java.util.List;

public class HomeActivity extends ComponentActivity {

    private static final String TAG = "HomeActivity";
    private static final int GRID_COLUMN_COUNT = 3;

    // Adapters & Layout Managers
    private SdrDeviceAdapter deviceAdapter;
    private DashboardToolsAdapter toolsAdapter;
    private LinearLayoutManager horizontalLayoutManager;
    private GridLayoutManager gridLayoutManager;

    // UI Components
    private RecyclerView recyclerDevices;
    private RecyclerView recyclerDashboardTools;
    private View layoutEmptyState;
    private TextView tvDeviceCountBadge;
    private TextView tvHeaderSdrStatus;
    private TextView tvToolsCountBadge;
    private ImageButton btnExpandTools;
    private ImageButton btnSettings;
    private View cardModeReceive;
    private View cardModeTransmit;

    // Filter Chips
    private TextView chipFilterAll;
    private TextView chipFilterRx;
    private TextView chipFilterTx;
    private TextView chipFilterAnalysis;
    private TextView chipFilterSecurity;

    // State & Data
    private boolean isToolsExpanded = false;
    private DashboardToolItem.Category currentCategory = DashboardToolItem.Category.ALL;
    private List<DashboardToolItem> allPresets;

    private final SdrManager.Listener sdrListener = new SdrManager.Listener() {
        @Override
        public void onSdrConnected(@NonNull SdrDevice sdr, @NonNull UsbDevice device) {
            String deviceName = getDeviceDisplayName(device, sdr);
            Log.i(TAG, "SDR Connected: " + deviceName);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(HomeActivity.this, "SDR Connected: " + deviceName, Toast.LENGTH_SHORT).show();
                refreshDeviceList();
            });
        }

        @Override
        public void onSdrDisconnected(@NonNull UsbDevice device, @Nullable SdrDeviceInfo deviceInfo) {
            String deviceName = (deviceInfo != null && deviceInfo.getProduct() != null)
                    ? deviceInfo.getProduct()
                    : getDeviceDisplayName(device, null);
            Log.i(TAG, "SDR Disconnected: " + deviceName);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(HomeActivity.this, "SDR Disconnected: " + deviceName, Toast.LENGTH_SHORT).show();
                refreshDeviceList();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        setupWindowInsets();
        initViews();
        loadPresetsData();
        setupDashboardTools();
        setupDeviceList();
        setupFilterChips();
        setupQuickModeCards();

        // System permissions and battery optimization
        PermissionManager.getInstance().requestNotificationPermission(this);
        PerformanceManager.getInstance(this).requestIgnoreBatteryOptimizations(this);

        // SDR hardware lifecycle
        registerSdrListener();
        refreshDeviceList();
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

    private void initViews() {
        recyclerDevices = findViewById(R.id.recycler_sdr_devices);
        recyclerDashboardTools = findViewById(R.id.recycler_dashboard_tools);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        tvDeviceCountBadge = findViewById(R.id.tv_device_count_badge);
        tvHeaderSdrStatus = findViewById(R.id.tv_header_sdr_status);
        tvToolsCountBadge = findViewById(R.id.tv_tools_count_badge);
        btnExpandTools = findViewById(R.id.btn_expand_tools);
        btnSettings = findViewById(R.id.btn_settings);

        cardModeReceive = findViewById(R.id.card_mode_receive);
        cardModeTransmit = findViewById(R.id.card_mode_transmit);

        chipFilterAll = findViewById(R.id.chip_filter_all);
        chipFilterRx = findViewById(R.id.chip_filter_rx);
        chipFilterTx = findViewById(R.id.chip_filter_tx);
        chipFilterAnalysis = findViewById(R.id.chip_filter_analysis);
        chipFilterSecurity = findViewById(R.id.chip_filter_security);

        btnSettings.setOnClickListener(v ->
                Toast.makeText(HomeActivity.this, "Settings", Toast.LENGTH_SHORT).show()
        );

        btnExpandTools.setOnClickListener(v -> toggleToolsLayout());
    }

    private void loadPresetsData() {
        allPresets = DashboardPresetsProvider.getDefaultPresets();
    }

    private void setupDashboardTools() {
        toolsAdapter = new DashboardToolsAdapter(this::onDashboardToolClicked);

        // Pre-instantiate reusable layout managers to avoid allocations on toggle
        horizontalLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        gridLayoutManager = new GridLayoutManager(this, GRID_COLUMN_COUNT);

        recyclerDashboardTools.setLayoutManager(horizontalLayoutManager);
        recyclerDashboardTools.setAdapter(toolsAdapter);

        filterToolsByCategory(DashboardToolItem.Category.ALL);
    }

    private void toggleToolsLayout() {
        isToolsExpanded = !isToolsExpanded;
        btnExpandTools.setImageResource(isToolsExpanded ? R.drawable.ic_expand_less_24 : R.drawable.ic_expand_more_24);
        toolsAdapter.setGridMode(isToolsExpanded);
        recyclerDashboardTools.setLayoutManager(isToolsExpanded ? gridLayoutManager : horizontalLayoutManager);
    }

    private void setupFilterChips() {
        chipFilterAll.setOnClickListener(v -> filterToolsByCategory(DashboardToolItem.Category.ALL));
        chipFilterRx.setOnClickListener(v -> filterToolsByCategory(DashboardToolItem.Category.RECEIVE));
        chipFilterTx.setOnClickListener(v -> filterToolsByCategory(DashboardToolItem.Category.TRANSMIT));
        chipFilterAnalysis.setOnClickListener(v -> filterToolsByCategory(DashboardToolItem.Category.ANALYSIS));
        chipFilterSecurity.setOnClickListener(v -> filterToolsByCategory(DashboardToolItem.Category.SECURITY));
    }

    private void filterToolsByCategory(DashboardToolItem.Category category) {
        currentCategory = category;

        updateChipState(chipFilterAll, category == DashboardToolItem.Category.ALL);
        updateChipState(chipFilterRx, category == DashboardToolItem.Category.RECEIVE);
        updateChipState(chipFilterTx, category == DashboardToolItem.Category.TRANSMIT);
        updateChipState(chipFilterAnalysis, category == DashboardToolItem.Category.ANALYSIS);
        updateChipState(chipFilterSecurity, category == DashboardToolItem.Category.SECURITY);

        List<DashboardToolItem> filtered = DashboardPresetsProvider.filterByCategory(allPresets, category);
        toolsAdapter.setItems(filtered);

        if (tvToolsCountBadge != null) {
            String countLabel = filtered.size() + (filtered.size() == 1 ? " Tool" : " Tools");
            tvToolsCountBadge.setText(countLabel);
        }
    }

    private void updateChipState(@Nullable TextView chip, boolean isSelected) {
        if (chip == null) return;
        chip.setBackgroundResource(isSelected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chip.setTextColor(ContextCompat.getColor(this, isSelected ? R.color.chip_text_selected : R.color.chip_text_unselected));
    }

    private void setupQuickModeCards() {
        cardModeReceive.setOnClickListener(v ->
                Toast.makeText(this, "Spectrum & Waterfall mode activated", Toast.LENGTH_SHORT).show()
        );

        cardModeTransmit.setOnClickListener(v ->
                Toast.makeText(this, "Signal Transmitter mode activated", Toast.LENGTH_SHORT).show()
        );
    }

    private void onDashboardToolClicked(@NonNull DashboardToolItem tool) {
        if ("adsb".equalsIgnoreCase(tool.getId()) || tool.getName().toLowerCase().contains("ads-b") || tool.getName().toLowerCase().contains("aircraft")) {
            startActivity(new Intent(this, AdsbActivity.class));
        } else {
            Toast.makeText(this, "Launching " + tool.getName() + " (" + tool.getFrequency() + ")", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupDeviceList() {
        deviceAdapter = new SdrDeviceAdapter(new SdrDeviceAdapter.OnDeviceActionListener() {
            @Override
            public void onInfoClick(@NonNull SdrDevice sdr) {
                showDeviceInfoDialog(sdr);
            }

            @Override
            public void onSettingsClick(@NonNull SdrDevice sdr) {
                SdrDeviceInfo info = sdr.getDeviceInfo();
                String name = info != null && info.getProduct() != null ? info.getProduct() : "SDR";
                Toast.makeText(HomeActivity.this, "Configure " + name, Toast.LENGTH_SHORT).show();
            }
        });
        recyclerDevices.setAdapter(deviceAdapter);
    }

    private void showDeviceInfoDialog(@NonNull SdrDevice sdr) {
        SdrDeviceInfo info = sdr.getDeviceInfo();
        String name = info != null && info.getProduct() != null ? info.getProduct() : "SDR";
        Toast.makeText(this, "Device Info: " + name, Toast.LENGTH_SHORT).show();
    }

    private void registerSdrListener() {
        SdrManager sdrManager = WaveApplication.getSdrManager();
        if (sdrManager != null) {
            sdrManager.addListener(sdrListener);
        } else {
            Log.w(TAG, "SdrManager is not initialized");
        }
    }

    private void unregisterSdrListener() {
        SdrManager sdrManager = WaveApplication.getSdrManager();
        if (sdrManager != null) {
            sdrManager.removeListener(sdrListener);
        }
    }

    /**
     * Refresh the device list and toggle UI state (cards vs empty state).
     */
    private void refreshDeviceList() {
        SdrManager sdrManager = WaveApplication.getSdrManager();
        List<SdrDevice> devices = (sdrManager != null) ? sdrManager.getConnectedDevices() : Collections.emptyList();

        Log.d(TAG, "Connected SDR device count: " + devices.size());

        if (deviceAdapter != null) {
            deviceAdapter.setDevices(devices);
        }

        boolean hasDevices = !devices.isEmpty();

        if (recyclerDevices != null) {
            recyclerDevices.setVisibility(hasDevices ? View.VISIBLE : View.GONE);
        }
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(hasDevices ? View.GONE : View.VISIBLE);
        }
        if (tvDeviceCountBadge != null) {
            tvDeviceCountBadge.setVisibility(hasDevices ? View.VISIBLE : View.GONE);
            if (hasDevices) {
                tvDeviceCountBadge.setText(devices.size() + (devices.size() == 1 ? " Device" : " Devices"));
            }
        }
        if (tvHeaderSdrStatus != null) {
            if (hasDevices) {
                String firstDevName = getDeviceDisplayName(null, devices.get(0));
                tvHeaderSdrStatus.setText("Ready: " + firstDevName);
            } else {
                tvHeaderSdrStatus.setText(R.string.no_devices_connected);
            }
        }
    }

    /**
     * Helper to safely extract a human-readable display name for the device.
     */
    @NonNull
    private String getDeviceDisplayName(@Nullable UsbDevice device, @Nullable SdrDevice sdr) {
        if (device != null && device.getProductName() != null && !device.getProductName().trim().isEmpty()) {
            return device.getProductName().trim();
        }
        if (sdr != null && sdr.getDeviceInfo() != null && sdr.getDeviceInfo().getProduct() != null) {
            return sdr.getDeviceInfo().getProduct().trim();
        }
        if (device != null) {
            return "USB Device (" + String.format("%04X:%04X", device.getVendorId(), device.getProductId()) + ")";
        }
        return "SDR Device";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterSdrListener();
    }
}