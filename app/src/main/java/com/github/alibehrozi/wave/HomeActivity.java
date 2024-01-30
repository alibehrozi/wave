package com.github.alibehrozi.wave;

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

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";
    public static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setContentView(R.layout.activity_home);

        registerUsbReceiver();
    }

    /**
     * Register the USB broadcast receiver
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerUsbReceiver() {
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(ACTION_USB_PERMISSION);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbBroadcastReceiver, usbFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbBroadcastReceiver, usbFilter);
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
                case UsbManager.ACTION_USB_ACCESSORY_DETACHED:
                    // Handle USB device detached event
                    handleUsbAccessoryDetached(intent);
                    break;
                case ACTION_USB_PERMISSION:
                    // Handle USB permission request result
                    handleUsbPermission(intent);
                    break;
            }
        }
    };

    /**
     * Handle USB device attached event
     *
     * @param usbIntent the intent that triggered the receiver
     */
    private void handleUsbDeviceAttached(Intent usbIntent) {
        UsbDevice usbDevice = usbIntent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (usbDevice == null) {
            Log.e(TAG, "Failed to get USB device");
            return;
        }
        Log.d(TAG, "USB device attached: " + usbDevice.getDeviceName());
        requestUsbPermission(usbDevice);
    }

    /**
     * Handle USB accessory detached event
     *
     * @param usbIntent the intent that triggered the receiver
     */
    private void handleUsbAccessoryDetached(Intent usbIntent) {
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
            if (device == null)
                return;

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
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}