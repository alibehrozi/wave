package com.github.alibehrozi.wave;

import static com.github.alibehrozi.wave.Hackrf.open;
import static com.github.alibehrozi.wave.Hackrf.setFrequency;
import static com.github.alibehrozi.wave.Hackrf.setLNAGain;
import static com.github.alibehrozi.wave.Hackrf.setSampleRate;
import static com.github.alibehrozi.wave.Hackrf.setVGAGain;
import static com.github.alibehrozi.wave.Hackrf.startRx;

import com.github.alibehrozi.wave.Hackrf.hackrf_error;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";
    public static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Register USB receiver
        registerUsbReceiver();

        findViewById(R.id.connect).setOnClickListener(view -> {
            // Check for already connected devices
            detectConnectedDevices();
        });
    }

    /**
     * Sets up the HackRF device if the connection is established.
     *
     * @param fileDescriptor The file descriptor of the HackRF device.
     */
    private void setupHackRF(int fileDescriptor) {
        // Open HackRF device
        int status = open(fileDescriptor);
        hackrf_error error = hackrf_error.fromCode(status);

        if (error != hackrf_error.HACKRF_SUCCESS) {
            Log.e(TAG, "Failed to open HackRF device: " + error.getDescription());
            return;
        }

        Log.d(TAG, "HackRF device opened successfully.");

        // Set the desired frequency
        status = setFrequency(144500000);
        error = hackrf_error.fromCode(status);

        if (error != hackrf_error.HACKRF_SUCCESS) {
            Log.e(TAG, "Failed to set frequency: " + error.getDescription());
            return;
        }

        Log.d(TAG, "Frequency set to 144.5 MHz.");

        // Set the sample rate
        status = setSampleRate(10000000);
        error = hackrf_error.fromCode(status);

        if (error != hackrf_error.HACKRF_SUCCESS) {
            Log.e(TAG, "Failed to set sample rate: " + error.getDescription());
            return;
        }

        Log.d(TAG, "Sample rate set to 10 MHz.");

        // Set LNA gain
        status = setLNAGain(16);
        error = hackrf_error.fromCode(status);

        if (error != hackrf_error.HACKRF_SUCCESS) {
            Log.e(TAG, "Failed to set LNA gain: " + error.getDescription());
            return;
        }

        Log.d(TAG, "LNA gain set to 16.");

        // Set VGA gain
        status = setVGAGain(8);
        error = hackrf_error.fromCode(status);

        if (error != hackrf_error.HACKRF_SUCCESS) {
            Log.e(TAG, "Failed to set VGA gain: " + error.getDescription());
            return;
        }

        Log.d(TAG, "VGA gain set to 8.");

        // Start receiving data
        status = startRx();
        error = hackrf_error.fromCode(status);

        if (error != hackrf_error.HACKRF_SUCCESS) {
            Log.e(TAG, "Failed to start receiving data: " + error.getDescription());
            return;
        }

        Log.d(TAG, "Receiving started.");
    }

    /**
     * Register the USB broadcast receiver
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerUsbReceiver() {
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(ACTION_USB_PERMISSION);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbBroadcastReceiver, usbFilter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(usbBroadcastReceiver, usbFilter);
        }
    }

    private void detectConnectedDevices() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            Log.e(TAG, "Failed to get USB service");
            return;
        }

        // Get connected devices
        Map<String, UsbDevice> deviceList = usbManager.getDeviceList();

        // If no devices are connected, log and return
        if (deviceList.isEmpty()) {
            Log.d(TAG, "No USB devices connected");
            return;
        }

        for (UsbDevice device : deviceList.values()) {
            Log.d(TAG, "Connected USB device: " + device.getDeviceName());

            // Check for permission, and request if necessary
            if (!usbManager.hasPermission(device)) {
                Log.d(TAG, "Requesting permission for device: " + device.getDeviceName());
                requestUsbPermission(device);
            } else {
                Log.d(TAG, "Permission already granted for device: " + device.getDeviceName());
                // Handle connection if permission already exists
                ConnectDevice(device);
            }
        }
    }

    /**
     * A BroadcastReceiver that listens for USB device events.
     */
    private final BroadcastReceiver usbBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action == null) {
                // Handle the NullPointerException gracefully
                Log.e(TAG, "Action is null");
                return;
            }

            switch (action) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    // Handle USB device attached event
                    handleUsbDeviceAttached(intent);
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    // Handle USB device detached event
                    handleUsbDeviceDetached(intent);
                    break;
                case ACTION_USB_PERMISSION:
                    // Handle USB permission request result
                    handleUsbPermission(intent);
                    break;

                default:
                    Log.w(TAG, "Unhandled action: " + action);
                    break;
            }
        }
    };

    /**
     * Handle USB device attached event
     *
     * @param usbIntent the intent that triggered the receiver
     */
    private void handleUsbDeviceAttached(@NonNull Intent usbIntent) {
        UsbDevice usbDevice = usbIntent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (usbDevice == null) {
            Log.e(TAG, "Failed to get USB device");
            return;
        }

        // Log detailed information about the attached USB device
        Log.d(TAG, "USB device attached: " + usbDevice.getDeviceName() +
                ", Vendor ID: " + usbDevice.getVendorId() +
                ", Product ID: " + usbDevice.getProductId());

        // Request permission for the attached device
        requestUsbPermission(usbDevice);
    }

    /**
     * Handle USB device detached event
     *
     * @param usbIntent the intent that triggered the receiver
     */
    private void handleUsbDeviceDetached(@NonNull Intent usbIntent) {
        UsbDevice usbDevice = usbIntent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (usbDevice == null) {
            Log.e(TAG, "Failed to get USB device");
            Log.d(TAG, "USB accessory detached");
            return;
        }

        Log.d(TAG, "USB accessory detached: " +
                usbDevice.getDeviceName());

        // TODO : stop running processes
    }

    /**
     * Handle USB permission request result
     *
     * @param intent incoming intent
     */
    private void handleUsbPermission(Intent intent) {
        synchronized (this) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device == null) {
                Log.w(TAG, "No device found ");
                return;
            }

            boolean permissionGranted = intent.getBooleanExtra(
                    UsbManager.EXTRA_PERMISSION_GRANTED,
                    false);

            if (!permissionGranted) {
                Log.w(TAG, "USB permission denied for device: " +
                        device.getDeviceName());
                return;
            }

            Log.d(TAG, "USB permission granted for device: " +
                    device.getDeviceName());

            ConnectDevice(device);
        }
    }

    /**
     * Request USB permission for the attached device
     *
     * @param usbDevice the attached device
     */
    private void requestUsbPermission(UsbDevice usbDevice) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            Log.e(TAG, "Failed to get USB service");
            return;
        }

        if (usbManager.hasPermission(usbDevice)) {
            Log.d(TAG, "USB permission already granted for device: " +
                    usbDevice.getDeviceName());
            return;
        }

        Log.d(TAG, "Requesting USB permission for device: " +
                usbDevice.getDeviceName());
        usbManager.requestPermission(usbDevice,
                PendingIntent.getBroadcast(
                        this,
                        0,
                        new Intent(ACTION_USB_PERMISSION),
                        PendingIntent.FLAG_IMMUTABLE)
        );
    }

    /**
     * Connect to the USB device
     *
     * @param device the USB device to connect
     */
    private void ConnectDevice(UsbDevice device) {

        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            Log.e(TAG, "Failed to get USB service");
            return;
        }
        if (!usbManager.hasPermission(device)) {
            Log.w(TAG, "USB permission not granted for device: " +
                    device.getDeviceName());
            return;
        }

        UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(device);
        if (usbDeviceConnection == null) {
            Log.e(TAG, "Failed to open USB device: " +
                    device.getDeviceName());
            return;
        }
        int fileDescriptor = usbDeviceConnection.getFileDescriptor();
        Log.d(TAG, "Connected to USB device: " + device.getDeviceName() + ", File descriptor: " + fileDescriptor);

        // TODO : handle opening usb device

        setupHackRF(fileDescriptor);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister USB receiver to avoid leaks
        unregisterReceiver(usbBroadcastReceiver);
    }
}