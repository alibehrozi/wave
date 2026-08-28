package com.github.alibehrozi.wave.microdsp.hardware.sdr.discovery;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.*;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.manager.*;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.drivers.hackrf.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

/**
 * BroadcastReceiver that listens for USB device attachment to start the SDR Discovery Service.
 */
public final class SdrDiscoveryReceiver extends BroadcastReceiver {

    private static final String TAG = "SdrDiscoveryReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice.class);

            if (device != null) {
                Log.i(TAG, String.format("SDR Device Attached: %s (0x%04x:0x%04x)",
                        device.getProductName(), device.getVendorId(), device.getProductId()));

                // Start the SdrDiscoveryService to handle the connection.
                Intent serviceIntent = new Intent(context, SdrDiscoveryService.class);
                serviceIntent.putExtra(UsbManager.EXTRA_DEVICE, device);
                ContextCompat.startForegroundService(context, serviceIntent);
            }
        }
    }
}
