package com.github.alibehrozi.wave.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents an ADS-B tracked aircraft with flight telemetry, ICAO identity, and position.
 */
public class Aircraft {
    private final String id;            // ICAO 24-bit hex identifier (e.g. "A12B4C")
    private String callsign;            // Flight Callsign (e.g. "UAL123")
    private String airline;             // Operator/Airline name
    private String aircraftType;        // Type code (e.g. "B789", "A359")
    private double latitude;
    private double longitude;
    private int altitude;               // Altitude in feet (Pressure / Barometric)
    private int speed;                  // Ground speed in knots
    private int heading;                // Heading track in degrees (0 - 359)
    private int verticalRate;           // Vertical speed in feet/min (+/-)
    private String squawk;              // Mode 3/A transponder squawk code
    private String origin;              // Origin airport IATA code (e.g. "JFK")
    private String destination;         // Destination airport IATA code (e.g. "LAX")
    private double distance;            // Calculated distance from receiver station (nmi)
    private int rssi;                   // Received signal strength (dBm)
    private long lastSeenTimestamp;     // System time of last received message

    public Aircraft(@NonNull String id,
                    @NonNull String callsign,
                    @NonNull String airline,
                    @NonNull String aircraftType,
                    double latitude,
                    double longitude,
                    int altitude,
                    int speed,
                    int heading,
                    int verticalRate,
                    @Nullable String squawk,
                    @Nullable String origin,
                    @Nullable String destination) {
        this.id = id;
        this.callsign = callsign;
        this.airline = airline;
        this.aircraftType = aircraftType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.speed = speed;
        this.heading = heading;
        this.verticalRate = verticalRate;
        this.squawk = squawk != null ? squawk : "1200";
        this.origin = origin != null ? origin : "---";
        this.destination = destination != null ? destination : "---";
        this.distance = 0.0;
        this.rssi = -60;
        this.lastSeenTimestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(@NonNull String callsign) {
        this.callsign = callsign;
    }

    @NonNull
    public String getAirline() {
        return airline;
    }

    public void setAirline(@NonNull String airline) {
        this.airline = airline;
    }

    @NonNull
    public String getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(@NonNull String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getAltitude() {
        return altitude;
    }

    public void setAltitude(int altitude) {
        this.altitude = altitude;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public int getHeading() {
        return heading;
    }

    public void setHeading(int heading) {
        this.heading = heading;
    }

    public int getVerticalRate() {
        return verticalRate;
    }

    public void setVerticalRate(int verticalRate) {
        this.verticalRate = verticalRate;
    }

    @NonNull
    public String getSquawk() {
        return squawk;
    }

    public void setSquawk(@NonNull String squawk) {
        this.squawk = squawk;
    }

    @NonNull
    public String getOrigin() {
        return origin;
    }

    public void setOrigin(@NonNull String origin) {
        this.origin = origin;
    }

    @NonNull
    public String getDestination() {
        return destination;
    }

    public void setDestination(@NonNull String destination) {
        this.destination = destination;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public void setLastSeenTimestamp(long lastSeenTimestamp) {
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    /**
     * Checks if this aircraft is transmitting an emergency transponder squawk.
     * 7700 = General Emergency, 7600 = Radio Failure, 7500 = Hijack.
     */
    public boolean isEmergency() {
        return "7700".equals(squawk) || "7600".equals(squawk) || "7500".equals(squawk);
    }
}
