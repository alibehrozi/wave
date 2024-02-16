package com.github.alibehrozi.wave;

import android.app.Application;
import android.os.Build;

import com.github.alibehrozi.wave.dataStream.NativeByteBuffer;
import com.github.alibehrozi.wave.dataStream.NativeLoader;

public class ApplicationLoader extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            NativeLoader.initNativeLibs(getApplicationContext(), "dataStream");
            NativeLoader.initNativeLibs(getApplicationContext(), "wave");
            NativeByteBuffer.setJava(false);
        } catch (UnsatisfiedLinkError error) {
            throw new RuntimeException("can't load native libraries " +
                    Build.CPU_ABI + " lookup folder " + NativeLoader.getAbiFolder());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}