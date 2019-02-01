package com.squareup.leakcanary;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import com.squareup.leakcanary.version.SDKVersion;

/**
 * A no-op version of {@link LeakCanary} that can be used in release builds.
 */
public final class LeakCanary {

  public static RefWatcher install(Application application) {
    return RefWatcher.DISABLED;
  }

  public static RefWatcher install(Application application, Settings settings) {
    return RefWatcher.DISABLED;
  }

  public static boolean isInAnalyzerProcess(Context context) {
    return false;
  }

  public static void deleteAllFiles(Context context) {
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
  public static Settings getSettings() {
    return new Settings();
  }

  /**
   * 设置
   *
   * @param settings
   */
  public static void setSettings(Settings settings) {
  }

  private LeakCanary() {
    throw new AssertionError();
  }
}
