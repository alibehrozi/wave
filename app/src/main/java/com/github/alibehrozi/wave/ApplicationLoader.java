package com.github.alibehrozi.wave;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.github.alibehrozi.wave.dataStream.NativeByteBuffer;
import com.github.alibehrozi.wave.dataStream.NativeLoader;

public class ApplicationLoader extends Application {

    public static final String TAG = "ApplicationLoader";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // Load native libraries separately to identify which one fails
            loadNativeLibrary("dataStream");
            loadNativeLibrary("wave");

            // Configure the NativeByteBuffer
            NativeByteBuffer.setJava(false);

        } catch (UnsatisfiedLinkError error) {
            // Log the error with details and rethrow RuntimeException to halt execution
            Log.e(TAG, "Failed to load native libraries. ABI: " + Build.CPU_ABI +
                    ", Lookup folder: " + NativeLoader.getAbiFolder(), error);
            throw new RuntimeException("Unable to load native libraries for ABI: " + Build.CPU_ABI);

        } catch (Exception e) {
            // Log the exception with details for debugging
            Log.e(TAG, "Unexpected error occurred during native library loading", e);
        }
    }

    /**
     * Helper method to load a specific native library.
     *
     * @param libName the name of the library to load
     */
    private void loadNativeLibrary(String libName) throws UnsatisfiedLinkError {
        NativeLoader.initNativeLibs(getApplicationContext(), libName);
        Log.d(TAG, "Native library " + libName + " loaded successfully.");
    }
}