package com.github.alibehrozi.wave.models;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provider that furnishes the default RF presets and dashboard tools.
 */
public final class DashboardPresetsProvider {

    private DashboardPresetsProvider() {
        // Utility class
    }

    @NonNull
    public static List<DashboardToolItem> getDefaultPresets() {
        List<DashboardToolItem> presets = new ArrayList<>();

        // 1. ADS-B Radar (Aviation Sky Blue)
        presets.add(new DashboardToolItem(
                "adsb",
                "ADS-B Aircraft",
                "1090 MHz",
                "BW: 2.0 MHz",
                DashboardToolItem.Category.RECEIVE,
                "RX",
                R.drawable.ic_preset_aircraft_24,
                Color.parseColor("#0288D1"),
                "Aviation transponder tracker and flight radar",
                1090e6,
                2e6
        ));

        // 2. Signal Jammer (Hazard Crimson Red)
        presets.add(new DashboardToolItem(
                "jammer",
                "Signal Jammer",
                "1MHz - 6GHz",
                "BW: 20 MHz",
                DashboardToolItem.Category.SECURITY,
                "SEC",
                R.drawable.ic_preset_jammer_24,
                Color.parseColor("#EF4444"),
                "Wideband sweep and RF noise transmitter",
                2.4e9,
                20e6
        ));

        // 3. Spectrum & Waterfall (Spectrum Emerald Green)
        presets.add(new DashboardToolItem(
                "analysis",
                "Signal Analysis",
                "1MHz - 6GHz",
                "BW: 20 MHz",
                DashboardToolItem.Category.ANALYSIS,
                "SCAN",
                R.drawable.ic_perset_signal_24,
                Color.parseColor("#10B981"),
                "Real-time wideband FFT spectrum & waterfall",
                433.92e6,
                10e6
        ));

        // 4. Signal Transmitter (Solar Amber / Orange)
        presets.add(new DashboardToolItem(
                "transmitter",
                "Signal Transmit",
                "1MHz - 6GHz",
                "BW: 200 kHz",
                DashboardToolItem.Category.TRANSMIT,
                "TX",
                R.drawable.ic_preset_fm_transmit_24,
                Color.parseColor("#F59E0B"),
                "Synthesized IQ, FM, AM, and digital transmitter",
                88.5e6,
                2e6
        ));

        // 5. GPS Spoofing (Satellite Cyan)
        presets.add(new DashboardToolItem(
                "gps_spoof",
                "GPS Spoofing",
                "1.575 GHz",
                "BW: 2.6 MHz",
                DashboardToolItem.Category.SECURITY,
                "SEC",
                R.drawable.ic_preset_gps_spoof_24,
                Color.parseColor("#06B6D4"),
                "GPS L1 C/A RF signal simulation",
                1.57542e9,
                2.6e6
        ));

        // 6. AIS Ships (Marine Cobalt Blue)
        presets.add(new DashboardToolItem(
                "ais",
                "AIS Marine",
                "162.025 MHz",
                "BW: 25 kHz",
                DashboardToolItem.Category.RECEIVE,
                "RX",
                R.drawable.ic_preset_marine_24,
                Color.parseColor("#3B82F6"),
                "Marine Automatic Identification System decoder",
                162.025e6,
                2e6
        ));

        // 7. Replay Attack (Cyber Purple)
        presets.add(new DashboardToolItem(
                "replay",
                "Replay Attack",
                "315/433 MHz",
                "BW: 500 kHz",
                DashboardToolItem.Category.SECURITY,
                "SEC",
                R.drawable.ic_preset_remote_24,
                Color.parseColor("#A855F7"),
                "Sub-GHz rolling code and remote signal replay",
                433.92e6,
                2e6
        ));

        // 8. Walkie Talkie / PMR (Radio Mint Green)
        presets.add(new DashboardToolItem(
                "walkie",
                "Walkie Talkie",
                "446.0 MHz",
                "BW: 12.5 kHz",
                DashboardToolItem.Category.RECEIVE,
                "RX",
                R.drawable.ic_preset_walkie_24,
                Color.parseColor("#22C55E"),
                "PMR446 and FRS narrow FM voice receiver",
                446.0e6,
                2e6
        ));

        // 9. FM Broadcast Radio (Broadcast Gold)
        presets.add(new DashboardToolItem(
                "fm_radio",
                "FM Broadcast",
                "88 - 108 MHz",
                "BW: 200 kHz",
                DashboardToolItem.Category.RECEIVE,
                "RX",
                R.drawable.ic_preset_radio_24,
                Color.parseColor("#EAB308"),
                "Commercial wideband FM stereo receiver",
                100.0e6,
                2e6
        ));

        // 10. Drone / RC Monitoring (Electric Magenta)
        presets.add(new DashboardToolItem(
                "drone",
                "Drone / RC",
                "2.4 GHz",
                "BW: 20 MHz",
                DashboardToolItem.Category.ANALYSIS,
                "SCAN",
                R.drawable.ic_preset_drone,
                Color.parseColor("#EC4899"),
                "UAV telemetry and RF controller analyzer",
                2.4e9,
                20e6
        ));

        // 11. Satellite / Weather (Atmospheric Teal)
        presets.add(new DashboardToolItem(
                "satellite",
                "Weather Satellites",
                "137.5 MHz",
                "BW: 40 kHz",
                DashboardToolItem.Category.RECEIVE,
                "RX",
                R.drawable.ic_preset_satellite_24,
                Color.parseColor("#14B8A6"),
                "NOAA & Meteor weather satellite imagery",
                137.5e6,
                2e6
        ));

        // 12. IoT & Sub-GHz (Smart Indigo)
        presets.add(new DashboardToolItem(
                "iot",
                "IoT / Sensors",
                "433 / 868 MHz",
                "BW: 1.0 MHz",
                DashboardToolItem.Category.ANALYSIS,
                "SCAN",
                R.drawable.ic_preset_iot_24,
                Color.parseColor("#6366F1"),
                "Smart meters, weather sensors & ISM packets",
                433.92e6,
                2e6
        ));

        // 13. WiFi RF Testing (Rose Red)
        presets.add(new DashboardToolItem(
                "wifi_jam",
                "WiFi Testing",
                "2.4 GHz",
                "BW: 20 MHz",
                DashboardToolItem.Category.SECURITY,
                "SEC",
                R.drawable.ic_preset_wifi_jam_24,
                Color.parseColor("#F43F5E"),
                "802.11 channel inspection and RF testing",
                2.412e9,
                20e6
        ));

        // 14. Bluetooth RF (Deep Violet)
        presets.add(new DashboardToolItem(
                "bt_jam",
                "Bluetooth RF",
                "2.4 GHz",
                "BW: 2.0 MHz",
                DashboardToolItem.Category.SECURITY,
                "SEC",
                R.drawable.ic_preset_bt_jam_24,
                Color.parseColor("#8B5CF6"),
                "BLE and classic Bluetooth spectrum analysis",
                2.402e9,
                2e6
        ));

        return Collections.unmodifiableList(presets);
    }

    @NonNull
    public static List<DashboardToolItem> filterByCategory(
            @NonNull List<DashboardToolItem> items,
            @NonNull DashboardToolItem.Category category
    ) {
        if (category == DashboardToolItem.Category.ALL) {
            return new ArrayList<>(items);
        }
        List<DashboardToolItem> filtered = new ArrayList<>();
        for (DashboardToolItem item : items) {
            if (item.getCategory() == category) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
