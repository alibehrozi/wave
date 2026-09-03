package com.github.alibehrozi.wave.models;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

/**
 * Data model representing an RF tool, preset, or frequency application displayed on the dashboard.
 */
public class DashboardToolItem {

    public enum Category {
        ALL("All"),
        RECEIVE("Receive"),
        TRANSMIT("Transmit"),
        ANALYSIS("Analysis"),
        SECURITY("Security");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final String id;
    private final String name;
    private final String frequency;
    private final String bandwidth;
    private final Category category;
    private final String tagText;
    private final @DrawableRes int iconRes;
    private final int accentColor;
    private final String description;
    private final double centerFreqHz;
    private final double sampleRateHz;

    public DashboardToolItem(
            @NonNull String id,
            @NonNull String name,
            @NonNull String frequency,
            @NonNull String bandwidth,
            @NonNull Category category,
            @NonNull String tagText,
            @DrawableRes int iconRes,
            int accentColor,
            @NonNull String description,
            double centerFreqHz,
            double sampleRateHz
    ) {
        this.id = id;
        this.name = name;
        this.frequency = frequency;
        this.bandwidth = bandwidth;
        this.category = category;
        this.tagText = tagText;
        this.iconRes = iconRes;
        this.accentColor = accentColor;
        this.description = description;
        this.centerFreqHz = centerFreqHz;
        this.sampleRateHz = sampleRateHz;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getFrequency() {
        return frequency;
    }

    @NonNull
    public String getBandwidth() {
        return bandwidth;
    }

    @NonNull
    public Category getCategory() {
        return category;
    }

    @NonNull
    public String getTagText() {
        return tagText;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    public int getAccentColor() {
        return accentColor;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    public double getCenterFreqHz() {
        return centerFreqHz;
    }

    public double getSampleRateHz() {
        return sampleRateHz;
    }
}
