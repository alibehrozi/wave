package com.github.alibehrozi.wave.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.github.alibehrozi.wave.R;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDevice;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDeviceInfo;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying connected SDR devices in elevated modern cards.
 */
public class SdrDeviceAdapter extends RecyclerView.Adapter<SdrDeviceAdapter.SdrViewHolder> {

    public interface OnDeviceActionListener {
        void onInfoClick(@NonNull SdrDevice sdr);
        void onSettingsClick(@NonNull SdrDevice sdr);
    }

    private final List<SdrDevice> deviceList = new ArrayList<>();
    @Nullable
    private final OnDeviceActionListener actionListener;

    public SdrDeviceAdapter(@Nullable OnDeviceActionListener listener) {
        this.actionListener = listener;
    }

    public void setDevices(@NonNull List<SdrDevice> newDevices) {
        this.deviceList.clear();
        this.deviceList.addAll(newDevices);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SdrViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sdr_card, parent, false);
        return new SdrViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SdrViewHolder holder, int position) {
        SdrDevice sdr = deviceList.get(position);
        holder.bind(sdr, actionListener);
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    static class SdrViewHolder extends RecyclerView.ViewHolder {
        private final ImageView deviceImage;
        private final TextView cardDuplexText;
        private final TextView cardStatusText;
        private final TextView deviceName;
        private final TextView deviceManufacturer;
        private final TextView cardFreqRangeBadge;
        private final TextView cardSampleRateBadge;
        private final TextView cardInterfaceBadge;
        private final ImageButton btnSettings;
        private final ImageButton btnInfo;

        SdrViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceImage = itemView.findViewById(R.id.card_device_image);
            cardDuplexText = itemView.findViewById(R.id.card_duplex_text);
            cardStatusText = itemView.findViewById(R.id.card_status_text);
            deviceName = itemView.findViewById(R.id.card_device_name);
            deviceManufacturer = itemView.findViewById(R.id.card_device_manufacturer);
            cardFreqRangeBadge = itemView.findViewById(R.id.card_freq_range_badge);
            cardSampleRateBadge = itemView.findViewById(R.id.card_sample_rate_badge);
            cardInterfaceBadge = itemView.findViewById(R.id.card_interface_badge);
            btnSettings = itemView.findViewById(R.id.card_btn_settings);
            btnInfo = itemView.findViewById(R.id.card_btn_info);
        }

        void bind(@NonNull SdrDevice sdr, @Nullable OnDeviceActionListener listener) {
            SdrDeviceInfo info = sdr.getDeviceInfo();

            String name = (info != null && !TextUtils.isEmpty(info.getProduct()))
                    ? info.getProduct()
                    : itemView.getContext().getString(R.string.unknown_device);
            deviceName.setText(name);

            String mfg = (info != null && !TextUtils.isEmpty(info.getManufacturer()))
                    ? info.getManufacturer()
                    : "Great Scott Gadgets";
            deviceManufacturer.setText(mfg);

            // Select matching image banner & metadata based on device product name
            String lowerName = name.toLowerCase();
            if (lowerName.contains("rad1o")) {
                deviceImage.setImageResource(R.drawable.rad1o);
                cardFreqRangeBadge.setText("50M – 4.0GHz");
                cardSampleRateBadge.setText("20 MSPS");
            } else if (lowerName.contains("jawbreaker")) {
                deviceImage.setImageResource(R.drawable.jawbreaker);
                cardFreqRangeBadge.setText("100M – 6.0GHz");
                cardSampleRateBadge.setText("20 MSPS");
            } else if (lowerName.contains("rtl")) {
                deviceImage.setImageResource(R.drawable.rtlsdr);
                cardFreqRangeBadge.setText("500k – 1.7GHz");
                cardSampleRateBadge.setText("3.2 MSPS");
            } else {
                deviceImage.setImageResource(R.drawable.hackrf);
                cardFreqRangeBadge.setText("1M – 6.0GHz");
                cardSampleRateBadge.setText("20 MSPS");
            }

            cardInterfaceBadge.setText("USB 2.0");

            // Duplex Role Badge
            SdrRole role = sdr.getRole();
            if (role == SdrRole.FULL_DUPLEX) {
                cardDuplexText.setText("Full-Duplex (RX/TX)");
            } else if (role == SdrRole.HALF_DUPLEX) {
                cardDuplexText.setText("Half-Duplex (RX/TX)");
            } else if (role == SdrRole.RX_ONLY) {
                cardDuplexText.setText("RX Only");
            } else if (role == SdrRole.TX_ONLY) {
                cardDuplexText.setText("TX Only");
            } else {
                cardDuplexText.setText("SDR Hardware");
            }

            btnInfo.setOnClickListener(v -> {
                if (listener != null) listener.onInfoClick(sdr);
            });

            btnSettings.setOnClickListener(v -> {
                if (listener != null) listener.onSettingsClick(sdr);
            });
        }
    }
}
