package com.github.alibehrozi.wave.models;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * Model representing a saved frequency channel / bookmark in the Walkie Talkie.
 */
public class ChannelBookmark {

    private final String id;
    private String name;
    private double frequencyHz;
    private double bandwidthHz;
    private String mode; // e.g. "NFM"
    private double ctcssHz; // 0.0 if disabled
    private int squelchLevel;
    private long timestamp;

    public ChannelBookmark(
            @NonNull String id,
            @NonNull String name,
            double frequencyHz,
            double bandwidthHz,
            @NonNull String mode,
            double ctcssHz,
            int squelchLevel,
            long timestamp
    ) {
        this.id = id;
        this.name = name;
        this.frequencyHz = frequencyHz;
        this.bandwidthHz = bandwidthHz;
        this.mode = mode;
        this.ctcssHz = ctcssHz;
        this.squelchLevel = squelchLevel;
        this.timestamp = timestamp;
    }

    public ChannelBookmark(@NonNull String name, double frequencyHz) {
        this(
                String.valueOf(System.currentTimeMillis()) + "_" + (int) (Math.random() * 1000),
                name,
                frequencyHz,
                12500.0,
                "NFM",
                0.0,
                5,
                System.currentTimeMillis()
        );
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public double getFrequencyHz() {
        return frequencyHz;
    }

    public void setFrequencyHz(double frequencyHz) {
        this.frequencyHz = frequencyHz;
    }

    public double getBandwidthHz() {
        return bandwidthHz;
    }

    public void setBandwidthHz(double bandwidthHz) {
        this.bandwidthHz = bandwidthHz;
    }

    @NonNull
    public String getMode() {
        return mode;
    }

    public void setMode(@NonNull String mode) {
        this.mode = mode;
    }

    public double getCtcssHz() {
        return ctcssHz;
    }

    public void setCtcssHz(double ctcssHz) {
        this.ctcssHz = ctcssHz;
    }

    public int getSquelchLevel() {
        return squelchLevel;
    }

    public void setSquelchLevel(int squelchLevel) {
        this.squelchLevel = squelchLevel;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getFormattedFrequency() {
        double mhz = frequencyHz / 1e6;
        return String.format(java.util.Locale.US, "%.4f MHz", mhz);
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("frequencyHz", frequencyHz);
        obj.put("bandwidthHz", bandwidthHz);
        obj.put("mode", mode);
        obj.put("ctcssHz", ctcssHz);
        obj.put("squelchLevel", squelchLevel);
        obj.put("timestamp", timestamp);
        return obj;
    }

    @NonNull
    public static ChannelBookmark fromJson(@NonNull JSONObject obj) throws JSONException {
        return new ChannelBookmark(
                obj.getString("id"),
                obj.getString("name"),
                obj.getDouble("frequencyHz"),
                obj.optDouble("bandwidthHz", 12500.0),
                obj.optString("mode", "NFM"),
                obj.optDouble("ctcssHz", 0.0),
                obj.optInt("squelchLevel", 5),
                obj.optLong("timestamp", System.currentTimeMillis())
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelBookmark that = (ChannelBookmark) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
