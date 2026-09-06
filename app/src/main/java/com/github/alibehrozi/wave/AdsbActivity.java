package com.github.alibehrozi.wave;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.alibehrozi.wave.adapters.AircraftAdapter;
import com.github.alibehrozi.wave.models.Aircraft;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modern ADS-B Flight Radar Activity.
 * Displays real-time tracked aircraft with OpenStreetMap visualization,
 * telemetry HUD, tactical search/filter chips, and interactive flight inspection.
 */
public class AdsbActivity extends ComponentActivity implements AircraftAdapter.OnAircraftClickListener {

    private static final String TAG = "AdsbActivity";

    // Default station coordinates (e.g. New York Area Station)
    private static final double STATION_LAT = 40.7128;
    private static final double STATION_LON = -74.0060;
    private static final double EARTH_RADIUS_NMI = 3440.065; // Nautical miles

    // Views - Navigation & HUD
    private ImageButton backBtn;
    private TextView tvHudTrackedCount;
    private TextView tvHudMsgRate;
    private TextView tvHudClosestDist;
    private TextView tvHudMaxAlt;
    private TextView tvListCountBadge;

    // Views - Map & Controls
    private View mapCard;
    private int defaultMapBottomMargin = -1;
    private MapView mapView;
    private ImageButton zoomInBtn;
    private ImageButton zoomOutBtn;
    private ImageButton recenterBtn;
    private ImageButton toggleRangeRingsBtn;
    private TextView mapCoordinatesText;

    // Views - Search & Filters
    private View layoutSearchFilterSection;
    private EditText etSearchAircraft;
    private ImageButton btnClearSearch;
    private TextView chipFilterAll;
    private TextView chipFilterCruise;
    private TextView chipFilterLow;
    private TextView chipFilterFast;
    private TextView chipFilterEmergency;

    // Views - RecyclerView & Aircraft Detail Sheet
    private View layoutAircraftListContainer;
    private RecyclerView recyclerAircraft;
    private AircraftAdapter aircraftAdapter;
    private LinearLayout cardAircraftDetails;
    private ImageButton btnCloseDetails;
    private ImageView ivDetailHeading;
    private TextView tvDetailCallsign;
    private TextView tvDetailType;
    private TextView tvDetailIcao;
    private TextView tvDetailAirline;
    private TextView tvDetailOrigin;
    private TextView tvDetailDestination;
    private TextView tvDetailAltitude;
    private TextView tvDetailVrate;
    private TextView tvDetailSpeed;
    private TextView tvDetailSpeedKmh;
    private TextView tvDetailHeading;
    private TextView tvDetailHeadingSub;
    private TextView tvDetailCoords;
    private TextView tvDetailSquawk;
    private TextView tvDetailSquawkSub;
    private TextView tvDetailDistance;
    private View btnTrackOnMap;

    // Data & Overlays
    private final List<Aircraft> aircraftList = new ArrayList<>();
    private final Map<String, Marker> markerMap = new HashMap<>();
    private final List<Polygon> rangeRingOverlays = new ArrayList<>();
    private Polyline flightPathLine = null;
    private Marker selectedMarker = null;
    private Marker userLocationMarker = null;
    private LocationListener activeLocationListener = null;
    private boolean showRangeRings = true;
    private boolean hasAutoCentered = false;
    private int lastKnownAircraftCount = 0;

    // Simulation / Update Loop
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private int simulatedMessageCount = 380;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Initialize osmdroid configuration
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_adsb);

        View rootView = findViewById(R.id.root_adsb_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupMap();
        setupRecyclerView();
        setupSearchAndFilters();
        setupDetailCard();

        // Seed initial flight radar data
        generateSampleFleet();
        startTelemetryLoop();
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        // HUD views
        tvHudTrackedCount = findViewById(R.id.tv_hud_tracked_count);
        tvHudMsgRate = findViewById(R.id.tv_hud_msg_rate);
        tvHudClosestDist = findViewById(R.id.tv_hud_closest_dist);
        tvHudMaxAlt = findViewById(R.id.tv_hud_max_alt);
        tvListCountBadge = findViewById(R.id.tv_list_count_badge);

        // Map views
        mapCard = findViewById(R.id.mapCard);
        if (mapCard != null && mapCard.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            defaultMapBottomMargin = ((ViewGroup.MarginLayoutParams) mapCard.getLayoutParams()).bottomMargin;
        }
        mapView = findViewById(R.id.mapView);
        zoomInBtn = findViewById(R.id.zoomInBtn);
        zoomOutBtn = findViewById(R.id.zoomOutBtn);
        recenterBtn = findViewById(R.id.recenterBtn);
        toggleRangeRingsBtn = findViewById(R.id.toggleRangeRingsBtn);
        mapCoordinatesText = findViewById(R.id.mapCoordinatesText);

        // Search & filter views
        layoutSearchFilterSection = findViewById(R.id.layout_search_filter_section);
        etSearchAircraft = findViewById(R.id.et_search_aircraft);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        chipFilterAll = findViewById(R.id.chip_filter_all);
        chipFilterCruise = findViewById(R.id.chip_filter_cruise);
        chipFilterLow = findViewById(R.id.chip_filter_low);
        chipFilterFast = findViewById(R.id.chip_filter_fast);
        chipFilterEmergency = findViewById(R.id.chip_filter_emergency);

        // RecyclerView & List Container
        layoutAircraftListContainer = findViewById(R.id.layout_aircraft_list_container);
        recyclerAircraft = findViewById(R.id.recyclerAircraft);

        // Detail sheet views
        cardAircraftDetails = findViewById(R.id.cardAircraftDetails);
        btnCloseDetails = findViewById(R.id.btnCloseDetails);
        ivDetailHeading = findViewById(R.id.iv_detail_heading);
        tvDetailCallsign = findViewById(R.id.tv_detail_callsign);
        tvDetailType = findViewById(R.id.tv_detail_type);
        tvDetailIcao = findViewById(R.id.tv_detail_icao);
        tvDetailAirline = findViewById(R.id.tv_detail_airline);
        tvDetailOrigin = findViewById(R.id.tv_detail_origin);
        tvDetailDestination = findViewById(R.id.tv_detail_destination);
        tvDetailAltitude = findViewById(R.id.tv_detail_altitude);
        tvDetailVrate = findViewById(R.id.tv_detail_vrate);
        tvDetailSpeed = findViewById(R.id.tv_detail_speed);
        tvDetailSpeedKmh = findViewById(R.id.tv_detail_speed_kmh);
        tvDetailHeading = findViewById(R.id.tv_detail_heading);
        tvDetailHeadingSub = findViewById(R.id.tv_detail_heading_sub);
        tvDetailCoords = findViewById(R.id.tv_detail_coords);
        tvDetailSquawk = findViewById(R.id.tv_detail_squawk);
        tvDetailSquawkSub = findViewById(R.id.tv_detail_squawk_sub);
        tvDetailDistance = findViewById(R.id.tv_detail_distance);
        btnTrackOnMap = findViewById(R.id.btn_track_on_map);
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(false); // Using custom floating controls
        mapView.setMultiTouchControls(true);

        GeoPoint stationCenter = new GeoPoint(STATION_LAT, STATION_LON);
        mapView.getController().setCenter(stationCenter);
        mapView.getController().setZoom(8.5);

        // Station center coordinate text
        updateStationCoordinateLabel(STATION_LAT, STATION_LON, 100);

        // If location is enabled, try to center on user
        if (isLocationEnabled()) {
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) {
                    Location loc = null;
                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }
                    if (loc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                    if (loc != null) {
                        animateToUserLocation(loc.getLatitude(), loc.getLongitude());
                        hasAutoCentered = true;
                    }
                }
            } catch (SecurityException ignored) {}
        }

        // Floating Map Controls
        zoomInBtn.setOnClickListener(v -> mapView.getController().zoomIn());
        zoomOutBtn.setOnClickListener(v -> mapView.getController().zoomOut());
        recenterBtn.setOnClickListener(v -> moveToUserLocation());

        toggleRangeRingsBtn.setOnClickListener(v -> {
            showRangeRings = !showRangeRings;
            for (Polygon ring : rangeRingOverlays) {
                ring.setEnabled(showRangeRings);
            }
            mapView.invalidate();
            toggleRangeRingsBtn.setAlpha(showRangeRings ? 1.0f : 0.4f);
        });

        // Build radar range rings (25 nmi, 50 nmi, 100 nmi)
        buildRangeRings(stationCenter);
    }

    private void updateStationCoordinateLabel(double lat, double lon, int rangeNmi) {
        char latDir = lat >= 0 ? 'N' : 'S';
        char lonDir = lon >= 0 ? 'E' : 'W';
        mapCoordinatesText.setText(String.format(Locale.getDefault(),
                "%.4f° %c, %.4f° %c • Radar %d nmi",
                Math.abs(lat), latDir, Math.abs(lon), lonDir, rangeNmi));
    }

    private void buildRangeRings(@NonNull GeoPoint center) {
        int[] ringDistancesNmi = {25, 50, 100};
        int ringColor = ContextCompat.getColor(this, R.color.adsb_radar_cyan);
        int strokeColor = Color.argb(90, Color.red(ringColor), Color.green(ringColor), Color.blue(ringColor));

        for (int distNmi : ringDistancesNmi) {
            Polygon circle = new Polygon(mapView);
            double distKm = distNmi * 1.852;
            List<GeoPoint> pts = Polygon.pointsAsCircle(center, distKm * 1000.0);
            circle.setPoints(pts);
            circle.getOutlinePaint().setColor(strokeColor);
            circle.getOutlinePaint().setStrokeWidth(2.0f);
            circle.getFillPaint().setColor(Color.TRANSPARENT);
            circle.setTitle(distNmi + " nmi range");

            mapView.getOverlays().add(circle);
            rangeRingOverlays.add(circle);
        }
    }

    private void setupRecyclerView() {
        recyclerAircraft.setLayoutManager(new LinearLayoutManager(this));
        aircraftAdapter = new AircraftAdapter(this);
        recyclerAircraft.setAdapter(aircraftAdapter);
    }

    private void setupSearchAndFilters() {
        etSearchAircraft.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                aircraftAdapter.setSearchQuery(query);
                updateListCountBadge();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearchAircraft.setText(""));

        chipFilterAll.setOnClickListener(v -> selectFilterChip(AircraftAdapter.FilterType.ALL, chipFilterAll));
        chipFilterCruise.setOnClickListener(v -> selectFilterChip(AircraftAdapter.FilterType.HIGH_ALTITUDE, chipFilterCruise));
        chipFilterLow.setOnClickListener(v -> selectFilterChip(AircraftAdapter.FilterType.LOW_ALTITUDE, chipFilterLow));
        chipFilterFast.setOnClickListener(v -> selectFilterChip(AircraftAdapter.FilterType.FAST, chipFilterFast));
        chipFilterEmergency.setOnClickListener(v -> selectFilterChip(AircraftAdapter.FilterType.EMERGENCY, chipFilterEmergency));
    }

    private void selectFilterChip(@NonNull AircraftAdapter.FilterType filter, @NonNull TextView activeChip) {
        TextView[] chips = {chipFilterAll, chipFilterCruise, chipFilterLow, chipFilterFast, chipFilterEmergency};
        for (TextView chip : chips) {
            boolean isCurrent = chip == activeChip;
            chip.setBackgroundResource(isCurrent ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            chip.setTextColor(ContextCompat.getColor(this, isCurrent ? R.color.chip_text_selected : R.color.chip_text_unselected));
        }

        aircraftAdapter.setFilter(filter);
        updateListCountBadge();
    }

    private void setupDetailCard() {
        btnCloseDetails.setOnClickListener(v -> hideAircraftDetails());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (cardAircraftDetails.getVisibility() == View.VISIBLE) {
                    hideAircraftDetails();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        btnTrackOnMap.setOnClickListener(v -> {
            Aircraft selected = aircraftAdapter.getSelectedAircraft();
            if (selected != null) {
                GeoPoint pos = new GeoPoint(selected.getLatitude(), selected.getLongitude());
                mapView.getController().animateTo(pos);
                mapView.getController().setZoom(10.5);
            }
        });
    }

    @Override
    public void onAircraftClick(@NonNull Aircraft aircraft) {
        aircraftAdapter.setSelectedAircraftId(aircraft.getId());
        showAircraftDetails(aircraft);

        // Center map and select marker
        GeoPoint pos = new GeoPoint(aircraft.getLatitude(), aircraft.getLongitude());
        mapView.getController().animateTo(pos);
        mapView.getController().setZoom(11.0);

        Marker marker = markerMap.get(aircraft.getId());
        if (marker != null) {
            highlightMarker(marker, aircraft);
        }

        drawFlightPathVector(aircraft);
    }

    private void showAircraftDetails(@NonNull Aircraft aircraft) {
        tvDetailCallsign.setText(aircraft.getCallsign());
        tvDetailType.setText(aircraft.getAircraftType());
        tvDetailIcao.setText("ICAO: " + aircraft.getId());
        tvDetailAirline.setText(aircraft.getAirline());
        tvDetailOrigin.setText(aircraft.getOrigin());
        tvDetailDestination.setText(aircraft.getDestination());

        tvDetailAltitude.setText(String.format(Locale.getDefault(), "%,d ft", aircraft.getAltitude()));
        String vrateText = (aircraft.getVerticalRate() >= 0 ? "+" : "") + aircraft.getVerticalRate() + " fpm";
        tvDetailVrate.setText(vrateText);
        tvDetailVrate.setTextColor(aircraft.getVerticalRate() >= 0
                ? ContextCompat.getColor(this, R.color.status_green)
                : ContextCompat.getColor(this, R.color.status_orange));

        tvDetailSpeed.setText(String.format(Locale.getDefault(), "%d kts", aircraft.getSpeed()));
        int kmh = (int) Math.round(aircraft.getSpeed() * 1.852);
        tvDetailSpeedKmh.setText(String.format(Locale.getDefault(), "%d km/h", kmh));

        // Track degree and compass cardinal direction
        tvDetailHeading.setText(String.format(Locale.getDefault(), "%d°", aircraft.getHeading()));
        if (tvDetailHeadingSub != null) {
            tvDetailHeadingSub.setText("Dir: " + getCardinalDirection(aircraft.getHeading()));
        }

        // Coordinates formatted with N/S and E/W on dedicated full-width telemetry bar
        double lat = aircraft.getLatitude();
        double lon = aircraft.getLongitude();
        String latStr = String.format(Locale.getDefault(), "%.3f° %s", Math.abs(lat), lat >= 0 ? "N" : "S");
        String lonStr = String.format(Locale.getDefault(), "%.3f° %s", Math.abs(lon), lon >= 0 ? "E" : "W");
        tvDetailCoords.setText(latStr + " • " + lonStr);

        tvDetailSquawk.setText("SQ " + aircraft.getSquawk());
        if (tvDetailSquawkSub != null) {
            if (aircraft.isEmergency()) {
                tvDetailSquawkSub.setText("ALERT");
                tvDetailSquawkSub.setTextColor(ContextCompat.getColor(this, R.color.adsb_emergency));
            } else {
                tvDetailSquawkSub.setText("Mode-S");
                tvDetailSquawkSub.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            }
        }
        tvDetailDistance.setText(String.format(Locale.getDefault(), "%.1f nmi away", aircraft.getDistance()));

        ivDetailHeading.setRotation(aircraft.getHeading());

        int altitudeColor;
        if (aircraft.isEmergency()) {
            altitudeColor = ContextCompat.getColor(this, R.color.adsb_emergency);
            tvDetailSquawk.setTextColor(altitudeColor);
        } else if (aircraft.getAltitude() >= 30000) {
            altitudeColor = ContextCompat.getColor(this, R.color.adsb_altitude_high);
            tvDetailSquawk.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        } else if (aircraft.getAltitude() >= 15000) {
            altitudeColor = ContextCompat.getColor(this, R.color.adsb_altitude_mid);
            tvDetailSquawk.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        } else {
            altitudeColor = ContextCompat.getColor(this, R.color.adsb_altitude_low);
            tvDetailSquawk.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
        ivDetailHeading.setColorFilter(altitudeColor);

        cardAircraftDetails.setVisibility(View.VISIBLE);
        if (mapCard != null && mapCard.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mapCard.getLayoutParams();
            params.bottomMargin = 0;
            mapCard.setLayoutParams(params);
        }
        if (layoutSearchFilterSection != null) {
            layoutSearchFilterSection.setVisibility(View.GONE);
        }
        if (layoutAircraftListContainer != null) {
            layoutAircraftListContainer.setVisibility(View.GONE);
        }
    }

    private String getCardinalDirection(int heading) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                               "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(((heading % 360) / 22.5)) % 16;
        return directions[index];
    }

    private void hideAircraftDetails() {
        cardAircraftDetails.setVisibility(View.GONE);
        if (mapCard != null && mapCard.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mapCard.getLayoutParams();
            params.bottomMargin = defaultMapBottomMargin >= 0
                    ? defaultMapBottomMargin
                    : (int) (8 * getResources().getDisplayMetrics().density);
            mapCard.setLayoutParams(params);
        }
        if (layoutSearchFilterSection != null) {
            layoutSearchFilterSection.setVisibility(View.VISIBLE);
        }
        if (layoutAircraftListContainer != null) {
            layoutAircraftListContainer.setVisibility(View.VISIBLE);
        }
        aircraftAdapter.setSelectedAircraftId(null);

        if (selectedMarker != null) {
            selectedMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_airplane));
            selectedMarker = null;
        }

        if (flightPathLine != null) {
            mapView.getOverlays().remove(flightPathLine);
            flightPathLine = null;
            mapView.invalidate();
        }

        zoomToAllVisibleAircraft();
    }

    private void highlightMarker(@NonNull Marker marker, @NonNull Aircraft aircraft) {
        if (selectedMarker != null && selectedMarker != marker) {
            selectedMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_airplane));
        }

        selectedMarker = marker;
        selectedMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_airplane_selected));
        selectedMarker.setRotation(aircraft.getHeading());
        mapView.invalidate();
    }

    private void drawFlightPathVector(@NonNull Aircraft aircraft) {
        if (flightPathLine != null) {
            mapView.getOverlays().remove(flightPathLine);
        }

        GeoPoint start = new GeoPoint(aircraft.getLatitude(), aircraft.getLongitude());
        // Projected heading vector ~ 30 nmi ahead
        double distDeg = 0.5;
        double radHeading = Math.toRadians(aircraft.getHeading());
        double endLat = aircraft.getLatitude() + distDeg * Math.cos(radHeading);
        double endLon = aircraft.getLongitude() + distDeg * Math.sin(radHeading);
        GeoPoint end = new GeoPoint(endLat, endLon);

        flightPathLine = new Polyline();
        flightPathLine.setPoints(List.of(start, end));
        flightPathLine.setColor(Color.argb(200, 0, 229, 255));
        flightPathLine.setWidth(4.0f);

        mapView.getOverlays().add(flightPathLine);
        mapView.invalidate();
    }

    private void generateSampleFleet() {
        aircraftList.clear();

        // Realistic transponder contacts around NY/East Coast airspace
        aircraftList.add(new Aircraft("A01123", "UAL123", "United Airlines", "B789",
                STATION_LAT + 0.35, STATION_LON + 0.25, 36000, 485, 275, 0, "1200", "JFK", "LAX"));

        aircraftList.add(new Aircraft("A04561", "DAL456", "Delta Air Lines", "A359",
                STATION_LAT - 0.20, STATION_LON + 0.40, 28000, 440, 195, -1200, "2450", "EWR", "ATL"));

        aircraftList.add(new Aircraft("A07892", "AAL789", "American Airlines", "B738",
                STATION_LAT + 0.15, STATION_LON - 0.30, 12500, 310, 80, 1800, "1400", "LGA", "ORD"));

        aircraftList.add(new Aircraft("A02345", "SWA234", "Southwest Airlines", "B737",
                STATION_LAT - 0.45, STATION_LON - 0.20, 8500, 250, 45, -800, "3100", "BWI", "BOS"));

        aircraftList.add(new Aircraft("A06789", "JBU678", "JetBlue Airways", "A321",
                STATION_LAT + 0.50, STATION_LON - 0.10, 39000, 510, 320, 0, "1200", "FLL", "BOS"));

        aircraftList.add(new Aircraft("A09012", "BAW178", "British Airways", "B77W",
                STATION_LAT + 0.65, STATION_LON + 0.55, 37000, 520, 65, 0, "6402", "JFK", "LHR"));

        aircraftList.add(new Aircraft("A03456", "AFR006", "Air France", "A359",
                STATION_LAT + 0.42, STATION_LON + 0.60, 35000, 495, 70, 0, "7011", "JFK", "CDG"));

        aircraftList.add(new Aircraft("A05678", "FDX567", "FedEx Express", "MD11",
                STATION_LAT - 0.32, STATION_LON + 0.12, 18000, 360, 260, 600, "1200", "EWR", "MEM"));

        aircraftList.add(new Aircraft("A09999", "N999EM", "Air Ambulance", "C56X",
                STATION_LAT + 0.08, STATION_LON + 0.05, 4500, 180, 15, -400, "7700", "HPN", "TEB"));

        aircraftList.add(new Aircraft("A01111", "UAE202", "Emirates", "A388",
                STATION_LAT + 0.70, STATION_LON + 0.35, 41000, 540, 55, 0, "4201", "JFK", "DXB"));

        updateCalculatedDistances();
        refreshOverlays();
        updateHudTelemetry();
        aircraftAdapter.setAircraftList(aircraftList);
        updateListCountBadge();
        if (!isLocationEnabled()) {
            zoomToAllVisibleAircraft();
        }
    }

    private void updateCalculatedDistances() {
        for (Aircraft a : aircraftList) {
            double distNmi = calculateDistanceNmi(STATION_LAT, STATION_LON, a.getLatitude(), a.getLongitude());
            a.setDistance(distNmi);
        }
    }

    private double calculateDistanceNmi(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_NMI * c;
    }

    private void refreshOverlays() {
        // Clear old markers
        for (Marker m : markerMap.values()) {
            mapView.getOverlays().remove(m);
        }
        markerMap.clear();

        Drawable defaultPlane = ContextCompat.getDrawable(this, R.drawable.ic_airplane);

        for (Aircraft aircraft : aircraftList) {
            GeoPoint position = new GeoPoint(aircraft.getLatitude(), aircraft.getLongitude());
            Marker marker = new Marker(mapView);
            marker.setPosition(position);
            marker.setTitle(aircraft.getCallsign());
            marker.setSnippet(String.format(Locale.getDefault(), "Alt: %,d ft • Speed: %d kts", aircraft.getAltitude(), aircraft.getSpeed()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setIcon(defaultPlane);
            marker.setRotation(aircraft.getHeading());

            marker.setOnMarkerClickListener((m, mv) -> {
                onAircraftClick(aircraft);
                return true;
            });

            mapView.getOverlays().add(marker);
            markerMap.put(aircraft.getId(), marker);
        }

        mapView.invalidate();

        boolean isShowingDetails = (cardAircraftDetails != null && cardAircraftDetails.getVisibility() == View.VISIBLE);
        boolean isNewAircraftDetected = aircraftList.size() > lastKnownAircraftCount;
        lastKnownAircraftCount = aircraftList.size();

        if (!isShowingDetails) {
            if (!hasAutoCentered && !isLocationEnabled()) {
                zoomToAllVisibleAircraft();
            } else if (isNewAircraftDetected && !isLocationEnabled()) {
                zoomToAllVisibleAircraft();
            }
        }
    }

    private void updateHudTelemetry() {
        tvHudTrackedCount.setText(String.valueOf(aircraftList.size()));

        // Simulated message rate
        tvHudMsgRate.setText(String.valueOf(simulatedMessageCount));

        // Find closest aircraft and max altitude
        double closestDist = Double.MAX_VALUE;
        int maxAlt = 0;

        for (Aircraft a : aircraftList) {
            if (a.getDistance() < closestDist) {
                closestDist = a.getDistance();
            }
            if (a.getAltitude() > maxAlt) {
                maxAlt = a.getAltitude();
            }
        }

        if (closestDist < Double.MAX_VALUE) {
            tvHudClosestDist.setText(String.format(Locale.getDefault(), "%.1f", closestDist));
        } else {
            tvHudClosestDist.setText("--.-");
        }

        if (maxAlt > 0) {
            int altK = Math.round((float) maxAlt / 1000f);
            tvHudMaxAlt.setText(altK + "k");
        } else {
            tvHudMaxAlt.setText("--k");
        }
    }

    private void updateListCountBadge() {
        int count = aircraftAdapter.getItemCount();
        tvListCountBadge.setText(count + (count == 1 ? " Aircraft" : " Aircraft"));
    }

    private void startTelemetryLoop() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                // Slightly advance aircraft positions along headings to simulate live tracking
                for (Aircraft a : aircraftList) {
                    double speedDegrees = (a.getSpeed() / 3600.0) * (2.0 / 60.0); // 2-second motion delta
                    double rad = Math.toRadians(a.getHeading());
                    a.setLatitude(a.getLatitude() + speedDegrees * Math.cos(rad));
                    a.setLongitude(a.getLongitude() + speedDegrees * Math.sin(rad));

                    // Update marker position
                    Marker m = markerMap.get(a.getId());
                    if (m != null) {
                        m.setPosition(new GeoPoint(a.getLatitude(), a.getLongitude()));
                    }
                }

                // Random fluctuation in simulated message rate
                simulatedMessageCount = 380 + (int) (Math.random() * 90 - 45);

                updateCalculatedDistances();
                updateHudTelemetry();
                aircraftAdapter.notifyDataSetChanged();

                // If an aircraft is currently selected, update its path and sheet values
                Aircraft selected = aircraftAdapter.getSelectedAircraft();
                if (selected != null) {
                    showAircraftDetails(selected);
                    drawFlightPathVector(selected);
                }

                mapView.invalidate();
                updateHandler.postDelayed(this, 2000); // 2-second live update loop
            }
        };

        updateHandler.postDelayed(updateRunnable, 2000);
    }

    private boolean isLocationEnabled() {
        if (!PermissionManager.getInstance().hasLocationPermission(this)) {
            return false;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void zoomToAllVisibleAircraft() {
        if (aircraftList.isEmpty()) {
            return;
        }

        // If a specific aircraft is currently shown in the detail card, do not zoom out
        if (cardAircraftDetails != null && cardAircraftDetails.getVisibility() == View.VISIBLE) {
            return;
        }

        if (aircraftList.size() == 1) {
            Aircraft single = aircraftList.get(0);
            GeoPoint pos = new GeoPoint(single.getLatitude(), single.getLongitude());
            mapView.getController().animateTo(pos);
            mapView.getController().setZoom(10.5);
            updateStationCoordinateLabel(single.getLatitude(), single.getLongitude(), 100);
            hasAutoCentered = true;
            return;
        }

        double minLat = 90.0, maxLat = -90.0;
        double minLon = 180.0, maxLon = -180.0;
        for (Aircraft a : aircraftList) {
            minLat = Math.min(minLat, a.getLatitude());
            maxLat = Math.max(maxLat, a.getLatitude());
            minLon = Math.min(minLon, a.getLongitude());
            maxLon = Math.max(maxLon, a.getLongitude());
        }

        double latSpan = maxLat - minLat;
        double lonSpan = maxLon - minLon;
        double latPad = Math.max(latSpan * 0.20, 0.05);
        double lonPad = Math.max(lonSpan * 0.20, 0.05);

        BoundingBox box = new BoundingBox(
                Math.min(90.0, maxLat + latPad),
                Math.min(180.0, maxLon + lonPad),
                Math.max(-90.0, minLat - latPad),
                Math.max(-180.0, minLon - lonPad)
        );

        double centerLat = (maxLat + minLat) / 2.0;
        double centerLon = (maxLon + minLon) / 2.0;
        final GeoPoint fallbackCenter = new GeoPoint(centerLat, centerLon);

        Runnable zoomAction = () -> {
            try {
                mapView.zoomToBoundingBox(box, true, 80);
            } catch (Exception e) {
                mapView.getController().animateTo(fallbackCenter);
            }
        };

        if (mapView.getWidth() > 0 && mapView.getHeight() > 0) {
            zoomAction.run();
        } else {
            mapView.post(zoomAction);
        }

        updateStationCoordinateLabel(centerLat, centerLon, 100);
        hasAutoCentered = true;
    }

    private void moveToUserLocation() {
        if (!PermissionManager.getInstance().hasLocationPermission(this)) {
            PermissionManager.getInstance().requestLocationPermission(this);
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Toast.makeText(this, "Location service unavailable. Zooming to all visible aircraft.", Toast.LENGTH_SHORT).show();
            zoomToAllVisibleAircraft();
            return;
        }

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(this, "Location is off. Zooming to all visible aircraft.", Toast.LENGTH_SHORT).show();
            zoomToAllVisibleAircraft();
            return;
        }

        Location bestLocation = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException checking last known location", e);
        }

        if (bestLocation != null) {
            animateToUserLocation(bestLocation.getLatitude(), bestLocation.getLongitude());
        } else {
            Toast.makeText(this, "Acquiring GPS location...", Toast.LENGTH_SHORT).show();
            try {
                if (activeLocationListener != null) {
                    locationManager.removeUpdates(activeLocationListener);
                }
                activeLocationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        animateToUserLocation(location.getLatitude(), location.getLongitude());
                        try {
                            locationManager.removeUpdates(this);
                        } catch (SecurityException ignored) {}
                        activeLocationListener = null;
                    }

                    @Override
                    public void onProviderEnabled(@NonNull String provider) {}

                    @Override
                    public void onProviderDisabled(@NonNull String provider) {}
                };

                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 1000L, 10f, activeLocationListener, Looper.getMainLooper());
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, 1000L, 10f, activeLocationListener, Looper.getMainLooper());
                } else {
                    zoomToAllVisibleAircraft();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException requesting location updates", e);
                zoomToAllVisibleAircraft();
            }
        }
    }

    private void animateToUserLocation(double lat, double lon) {
        GeoPoint userPoint = new GeoPoint(lat, lon);
        mapView.getController().animateTo(userPoint);
        mapView.getController().setZoom(11.0);

        if (userLocationMarker == null) {
            userLocationMarker = new Marker(mapView);
            Drawable icon = ContextCompat.getDrawable(this, R.drawable.ic_my_location);
            if (icon != null) {
                userLocationMarker.setIcon(icon);
            }
            userLocationMarker.setTitle("My Location");
            userLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            mapView.getOverlays().add(userLocationMarker);
        }
        userLocationMarker.setPosition(userPoint);
        updateStationCoordinateLabel(lat, lon, 100);
        hasAutoCentered = true;
        mapView.invalidate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQUEST_CODE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                moveToUserLocation();
            } else {
                Toast.makeText(this, "Location permission not granted. Zooming to all visible aircraft.", Toast.LENGTH_SHORT).show();
                zoomToAllVisibleAircraft();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateHandler.removeCallbacks(updateRunnable);
        if (activeLocationListener != null) {
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) {
                    lm.removeUpdates(activeLocationListener);
                }
            } catch (SecurityException ignored) {}
            activeLocationListener = null;
        }
        if (mapView != null) {
            mapView.getOverlays().clear();
        }
    }
}
