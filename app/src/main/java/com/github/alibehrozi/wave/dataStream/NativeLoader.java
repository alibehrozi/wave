package com.github.alibehrozi.wave.dataStream;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NativeLoader {

    private static final String TAG = "NativeLoader";

    public static StringBuilder log = new StringBuilder();

    private static File getNativeLibraryDir(Context context) {
        File f = null;
        if (context != null) {
            try {
                f = new File((String) ApplicationInfo.class.getField("nativeLibraryDir").get(context.getApplicationInfo()));
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (f == null) {
            f = new File(context.getApplicationInfo().dataDir, "lib");
        }
        if (f.isDirectory()) {
            return f;
        }
        return null;
    }

    @SuppressLint({"UnsafeDynamicallyLoadedCode", "SetWorldReadable"})
    private static boolean loadFromZip(Context context, File destDir, File destLocalFile, String folder, String LIB_NAME) {
        try {
            for (File file : destDir.listFiles()) {
                file.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        String LIB_SO_NAME = "lib" + LIB_NAME + ".so";

        ZipFile zipFile = null;
        InputStream stream = null;
        try {
            zipFile = new ZipFile(context.getApplicationInfo().sourceDir);
            ZipEntry entry = zipFile.getEntry("lib/" + folder + "/" + LIB_SO_NAME);
            if (entry == null) {
                throw new Exception("Unable to find file in apk:" + "lib/" + folder + "/" + LIB_NAME);
            }
            stream = zipFile.getInputStream(entry);

            OutputStream out = Files.newOutputStream(destLocalFile.toPath());
            byte[] buf = new byte[4096];
            int len;
            while ((len = stream.read(buf)) > 0) {
                Thread.yield();
                out.write(buf, 0, len);
            }
            out.close();

            destLocalFile.setReadable(true, false);
            destLocalFile.setExecutable(true, false);
            destLocalFile.setWritable(true);

            try {
                System.load(destLocalFile.getAbsolutePath());
            } catch (Error e) {
                Log.e(TAG, e.getMessage());
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return false;
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static synchronized void initNativeLibs(Context context, String LIB_NAME) {

        try {
            try {
                System.loadLibrary(LIB_NAME);
                Log.d(TAG, "loaded normal lib");
                return;
            } catch (Error e) {
                Log.e(TAG, e.getMessage());
                log.append("128: ").append(e).append("\n");
            }

            String folder = getAbiFolder();

            /*File destFile = getNativeLibraryDir(context);
            if (destFile != null) {
                destFile = new File(destFile, LIB_SO_NAME);
                if (destFile.exists()) {
                    try {
                        System.loadLibrary(LIB_NAME);
                        nativeLoaded = true;
                        return;
                    } catch (Error e) {
                        Log.e(TAG, e);
                    }
                }
            }*/

            File destDir = new File(context.getFilesDir(), "lib");
            destDir.mkdirs();

            String LOCALE_LIB_SO_NAME = "lib" + LIB_NAME + "loc.so";
            File destLocalFile = new File(destDir, LOCALE_LIB_SO_NAME);
            if (destLocalFile.exists()) {
                try {

                    Log.d(TAG, "Load local lib");

                    System.load(destLocalFile.getAbsolutePath());
                    return;
                } catch (Error e) {
                    log.append(e).append("\n");
                    Log.e(TAG, e.getMessage());
                }
                destLocalFile.delete();
            }
            Log.e(TAG, "Library not found, arch = " + folder);
            log.append("Library not found, arch = " + folder).append("\n");

            if (loadFromZip(context, destDir, destLocalFile, folder, LIB_NAME)) {
                return;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            log.append("176: ").append(e).append("\n");
        }

        try {
            System.loadLibrary(LIB_NAME);
        } catch (Error e) {
            Log.e(TAG, e.getMessage());
            log.append("184: ").append(e).append("\n");
        }
    }

    public static String getAbiFolder() {
        String folder;
        try {
            String str = Build.CPU_ABI;
            if (Build.CPU_ABI.equalsIgnoreCase("x86_64")) {
                folder = "x86_64";
            } else if (Build.CPU_ABI.equalsIgnoreCase("arm64-v8a")) {
                folder = "arm64-v8a";
            } else if (Build.CPU_ABI.equalsIgnoreCase("armeabi-v7a")) {
                folder = "armeabi-v7a";
            } else if (Build.CPU_ABI.equalsIgnoreCase("armeabi")) {
                folder = "armeabi";
            } else if (Build.CPU_ABI.equalsIgnoreCase("x86")) {
                folder = "x86";
            } else if (Build.CPU_ABI.equalsIgnoreCase("mips")) {
                folder = "mips";
            } else {
                folder = "armeabi";
                Log.e(TAG, "Unsupported arch: " + Build.CPU_ABI);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            folder = "armeabi";
        }

        String javaArch = System.getProperty("os.arch");
        if (javaArch != null && javaArch.contains("686")) {
            folder = "x86";
        }
        return folder;
    }
}