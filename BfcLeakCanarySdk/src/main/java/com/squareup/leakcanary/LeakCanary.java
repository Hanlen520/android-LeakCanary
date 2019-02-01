/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.leakcanary;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.squareup.leakcanary.internal.DisplayLeakActivity;
import com.squareup.leakcanary.internal.HeapAnalyzerService;
import com.squareup.leakcanary.version.SDKVersion;

import static android.text.format.Formatter.formatShortFileSize;
import static com.squareup.leakcanary.BuildConfig.GIT_SHA;
import static com.squareup.leakcanary.BuildConfig.LIBRARY_VERSION;
import static com.squareup.leakcanary.internal.LeakCanaryInternals.isInServiceProcess;
import static com.squareup.leakcanary.internal.LeakCanaryInternals.setEnabled;

public final class LeakCanary {

    private static final String TAG = LeakCanary.class.getName();
    private static Settings mSettings;

    /**
     * Creates a {@link RefWatcher} that works out of the box, and starts watching activity
     * references (on ICS+).
     */
    public static RefWatcher install(@NonNull Application application) {
        return install(application, new Settings());
    }

    /**
     * Creates a {@link RefWatcher} that works out of the box, and starts watching activity
     * references (on ICS+).
     */
    public static RefWatcher install(@NonNull Application application, @Nullable Settings settings) {
        // 版本信息
        Log.i(TAG, getVersion());
        if (application == null) {
            throw new NullPointerException("Application is null");
        }
        setSettings(settings);

        //启动清除所有卡顿报告文件
        if(getSettings().isDeleteFilesLaunch()){
            LogWriter.deleteAll(application);
        }

        return refWatcher(application).listenerServiceClass(DisplayLeakService.class)
                .excludedRefs(AndroidExcludedRefs.createAppDefaults().build())
                .buildAndInstall();
    }

    /**
     * Builder to create a customized {@link RefWatcher} with appropriate Android defaults.
     */
    public static AndroidRefWatcherBuilder refWatcher(Context context) {
        return new AndroidRefWatcherBuilder(context);
    }

    public static void enableDisplayLeakActivity(Context context) {
        setEnabled(context, DisplayLeakActivity.class, !getSettings().isMonkeyTest());
    }

    /**
     * If you build a {@link RefWatcher} with a {@link AndroidHeapDumper} that has a custom {@link
     * LeakDirectoryProvider}, then you should also call this method to make sure the activity in
     * charge of displaying leaks can find those on the file system.
     */
    public static void setDisplayLeakActivityDirectoryProvider(
            LeakDirectoryProvider leakDirectoryProvider) {
        DisplayLeakActivity.setLeakDirectoryProvider(leakDirectoryProvider);
    }

    /**
     * Returns a string representation of the result of a heap analysis.
     */
    public static String leakInfo(Context context, HeapDump heapDump, AnalysisResult result,
                                  boolean detailed) {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        PackageInfo packageInfo;
        try {
            packageInfo = packageManager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        String versionName = packageInfo.versionName;
        int versionCode = packageInfo.versionCode;
        String info = "In " + packageName + ":" + versionName + ":" + versionCode + ".\n";
        String sdkVersionInfo = SDKVersion.getLibraryName() + ":" + SDKVersion.getVersionName() + ".\n";

        info += sdkVersionInfo;
        String detailedString = "";
        if (result.leakFound) {
            if (result.excludedLeak) {
                info += "* EXCLUDED LEAK.\n";
            }
            info += "* " + result.className;
            if (!heapDump.referenceName.equals("")) {
                info += " (" + heapDump.referenceName + ")";
            }
            info += " has leaked:\n" + result.leakTrace.toString() + "\n";
            info += "* Retaining: " + formatShortFileSize(context, result.retainedHeapSize) + ".\n";
            if (detailed) {
                detailedString = "\n* Details:\n" + result.leakTrace.toDetailedString();
            }
        } else if (result.failure != null) {
            // We duplicate the library version & Sha information because bug reports often only contain
            // the stacktrace.
            info += "* FAILURE in " + LIBRARY_VERSION + " " + GIT_SHA + ":" + Log.getStackTraceString(
                    result.failure) + "\n";
        } else {
            info += "* NO LEAK FOUND.\n\n";
        }
        if (detailed) {
            detailedString += "* Excluded Refs:\n" + heapDump.excludedRefs;
        }

        info += "* Reference Key: "
                + heapDump.referenceKey
                + "\n"
                + "* Device: "
                + Build.MANUFACTURER
                + " "
                + Build.BRAND
                + " "
                + Build.MODEL
                + " "
                + Build.PRODUCT
                + "\n"
                + "* Android Version: "
                + Build.VERSION.RELEASE
                + " API: "
                + Build.VERSION.SDK_INT
                + " LeakCanary: "
                + LIBRARY_VERSION
                + " "
                + GIT_SHA
                + "\n"
                + "* Durations: watch="
                + heapDump.watchDurationMs
                + "ms, gc="
                + heapDump.gcDurationMs
                + "ms, heap dump="
                + heapDump.heapDumpDurationMs
                + "ms, analysis="
                + result.analysisDurationMs
                + "ms"
                + "\n"
                + detailedString;

        return info;
    }

    /**
     * Whether the current process is the process running the {@link HeapAnalyzerService}, which is
     * a different process than the normal app process.
     */
    public static boolean isInAnalyzerProcess(Context context) {
        return isInServiceProcess(context, HeapAnalyzerService.class);
    }

    /**
     * 删除所有内存泄漏报告文件
     *
     * @param context
     */
    public static void deleteAllFiles(@NonNull Context context) {
        if (context == null) {
            throw new NullPointerException("Context is null");
        }
        LogWriter.deleteAll(context);
    }

    /**
     * 获取 Bfc-LeakCanary 版本信息
     *
     * @return
     */
    public static String getVersion() {
        return TextUtils.concat(
                SDKVersion.getLibraryName(), ", version: ", SDKVersion.getVersionName(),
                "\ncode: ", String.valueOf(SDKVersion.getSDKInt()),
                "\nbuild: ", SDKVersion.getBuildName()
        ).toString();
    }

    /**
     * 获取设置信息
     *
     * @return
     */
    public synchronized static Settings getSettings() {
        if (mSettings == null) {
            mSettings = new Settings();
        }
        return mSettings;
    }

    /**
     * 设置
     *
     * @param settings
     */
    public synchronized static void setSettings(@Nullable Settings settings) {
        mSettings = settings == null ? new Settings() : settings;
    }

    private LeakCanary() {
        throw new AssertionError();
    }
}
