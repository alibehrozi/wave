package com.github.alibehrozi.wave.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.github.alibehrozi.wave.R;
import com.github.alibehrozi.wave.models.Aircraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AircraftAdapter extends RecyclerView.Adapter<AircraftAdapter.AircraftViewHolder> {

    public interface OnAircraftClickListener {
        void onAircraftClick(@NonNull Aircraft aircraft);
    }

    public enum FilterType {
        ALL,
        HIGH_ALTITUDE,   // > 30,000 ft
        LOW_ALTITUDE,    // < 15,000 ft
        FAST,            // > 400 kts
        EMERGENCY        // 7700 / 7600 / 7500
    }

    private final List<Aircraft> masterList = new ArrayList<>();
    private final List<Aircraft> displayedList = new ArrayList<>();
    private final OnAircraftClickListener listener;

    private String searchQuery = "";
    private FilterType currentFilter = FilterType.ALL;
    private String selectedAircraftId = null;

    public AircraftAdapter(@NonNull OnAircraftClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public AircraftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_aircraft, parent, false);
        return new AircraftViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AircraftViewHolder holder, int position) {
        Aircraft aircraft = displayedList.get(position);
        boolean isSelected = aircraft.getId().equals(selectedAircraftId);
        holder.bind(aircraft, isSelected, listener);
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    /**
     * Updates full list of tracked aircraft and applies active search / filters.
     */
    public void setAircraftList(@NonNull List<Aircraft> newList) {
        masterList.clear();
        masterList.addAll(newList);
        applyFilterAndSearch();
    }

    public void setSearchQuery(@NonNull String query) {
        this.searchQuery = query.trim().toLowerCase(Locale.ROOT);
        applyFilterAndSearch();
    }

    public void setFilter(@NonNull FilterType filter) {
        this.currentFilter = filter;
        applyFilterAndSearch();
    }

    public void setSelectedAircraftId(@Nullable String id) {
        this.selectedAircraftId = id;
        notifyDataSetChanged();
    }

    @Nullable
    public Aircraft getSelectedAircraft() {
        if (selectedAircraftId == null) return null;
        for (Aircraft a : masterList) {
            if (a.getId().equals(selectedAircraftId)) return a;
        }
        return null;
    }

    private void applyFilterAndSearch() {
        displayedList.clear();
        for (Aircraft aircraft : masterList) {
            // Category Filter Check
            boolean passesFilter = true;
            switch (currentFilter) {
                case HIGH_ALTITUDE:
                    passesFilter = aircraft.getAltitude() >= 30000;
                    break;
                case LOW_ALTITUDE:
                    passesFilter = aircraft.getAltitude() < 15000;
                    break;
                case FAST:
                    passesFilter = aircraft.getSpeed() >= 400;
                    break;
                case EMERGENCY:
                    passesFilter = aircraft.isEmergency();
                    break;
                case ALL:
                default:
                    passesFilter = true;
                    break;
            }

            if (!passesFilter) continue;

            // Search Query Check
            if (!searchQuery.isEmpty()) {
                boolean matchesCallsign = aircraft.getCallsign().toLowerCase(Locale.ROOT).contains(searchQuery);
                boolean matchesAirline = aircraft.getAirline().toLowerCase(Locale.ROOT).contains(searchQuery);
                boolean matchesType = aircraft.getAircraftType().toLowerCase(Locale.ROOT).contains(searchQuery);
                boolean matchesRoute = aircraft.getOrigin().toLowerCase(Locale.ROOT).contains(searchQuery)
                        || aircraft.getDestination().toLowerCase(Locale.ROOT).contains(searchQuery);
                boolean matchesSquawk = aircraft.getSquawk().contains(searchQuery);

                if (!matchesCallsign && !matchesAirline && !matchesType && !matchesRoute && !matchesSquawk) {
                    continue;
                }
            }

            displayedList.add(aircraft);
        }

        // Sort by distance ascending by default
        Collections.sort(displayedList, (a1, a2) -> Double.compare(a1.getDistance(), a2.getDistance()));
        notifyDataSetChanged();
    }

    static class AircraftViewHolder extends RecyclerView.ViewHolder {
        private final View rootLayout;
        private final FrameLayout containerIcon;
        private final ImageView ivAircraftHeading;
        private final TextView tvCallsign;
        private final TextView tvAircraftType;
        private final TextView tvAirline;
        private final TextView tvRoute;
        private final ImageView ivVerticalRate;
        private final TextView tvAltitude;
        private final TextView tvSpeed;
        private final TextView tvHeading;
        private final TextView tvDistance;
        private final TextView tvSquawk;

        public AircraftViewHolder(@NonNull View itemView) {
            super(itemView);
            rootLayout = itemView.findViewById(R.id.layout_aircraft_item);
            containerIcon = itemView.findViewById(R.id.container_icon);
            ivAircraftHeading = itemView.findViewById(R.id.iv_aircraft_heading);
            tvCallsign = itemView.findViewById(R.id.tv_callsign);
            tvAircraftType = itemView.findViewById(R.id.tv_aircraft_type);
            tvAirline = itemView.findViewById(R.id.tv_airline);
            tvRoute = itemView.findViewById(R.id.tv_route);
            ivVerticalRate = itemView.findViewById(R.id.iv_vertical_rate);
            tvAltitude = itemView.findViewById(R.id.tv_altitude);
            tvSpeed = itemView.findViewById(R.id.tv_speed);
            tvHeading = itemView.findViewById(R.id.tv_heading);
            tvDistance = itemView.findViewById(R.id.tv_distance);
            tvSquawk = itemView.findViewById(R.id.tv_squawk);
        }

        public void bind(@NonNull Aircraft aircraft, boolean isSelected, @Nullable OnAircraftClickListener listener) {
            Context context = itemView.getContext();

            rootLayout.setSelected(isSelected);

            tvCallsign.setText(aircraft.getCallsign());
            tvAircraftType.setText(aircraft.getAircraftType());
            tvAirline.setText(aircraft.getAirline());
            tvRoute.setText(String.format("%s ➔ %s", aircraft.getOrigin(), aircraft.getDestination()));
            tvAltitude.setText(String.format(Locale.getDefault(), "%,d ft", aircraft.getAltitude()));
            tvSpeed.setText(String.format(Locale.getDefault(), "%d kts", aircraft.getSpeed()));
            tvHeading.setText(String.format(Locale.getDefault(), "%d°", aircraft.getHeading()));
            tvDistance.setText(String.format(Locale.getDefault(), "%.1f nmi", aircraft.getDistance()));
            tvSquawk.setText("SQ " + aircraft.getSquawk());

            // Rotate aircraft silhouette to heading
            ivAircraftHeading.setRotation(aircraft.getHeading());

            // Altitude color styling
            int altitudeColor;
            if (aircraft.isEmergency()) {
                altitudeColor = ContextCompat.getColor(context, R.color.adsb_emergency);
                tvSquawk.setTextColor(altitudeColor);
            } else if (aircraft.getAltitude() >= 30000) {
                altitudeColor = ContextCompat.getColor(context, R.color.adsb_altitude_high);
                tvSquawk.setTextColor(ContextCompat.getColor(context, R.color.text_muted));
            } else if (aircraft.getAltitude() >= 15000) {
                altitudeColor = ContextCompat.getColor(context, R.color.adsb_altitude_mid);
                tvSquawk.setTextColor(ContextCompat.getColor(context, R.color.text_muted));
            } else {
                altitudeColor = ContextCompat.getColor(context, R.color.adsb_altitude_low);
                tvSquawk.setTextColor(ContextCompat.getColor(context, R.color.text_muted));
            }
            ivAircraftHeading.setColorFilter(altitudeColor);

            // Vertical rate indicator
            if (aircraft.getVerticalRate() > 200) {
                ivVerticalRate.setImageResource(R.drawable.ic_climb);
                ivVerticalRate.setVisibility(View.VISIBLE);
            } else if (aircraft.getVerticalRate() < -200) {
                ivVerticalRate.setImageResource(R.drawable.ic_descend);
                ivVerticalRate.setVisibility(View.VISIBLE);
            } else {
                ivVerticalRate.setImageResource(R.drawable.ic_level);
                ivVerticalRate.setVisibility(View.VISIBLE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAircraftClick(aircraft);
                }
            });
        }
    }
}
