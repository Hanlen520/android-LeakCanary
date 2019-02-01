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
package com.example.leakcanary;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

import com.eebbk.bfc.common.app.SharedPreferenceUtils;
import com.eebbk.bfc.common.app.ToastUtils;
import com.example.leakcanary.config.Config;
import com.github.moduth.blockcanary.BlockCanary;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;
import com.squareup.leakcanary.Settings;

public class ExampleApplication extends Application {
    private RefWatcher refWatcher;
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        initStrictMode();
        initLeakCanary();
        BlockCanary.install(this, new AppContext());
    }

    private void initStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .penaltyDialog()
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
    }

    private void initLeakCanary() {
        // leakcanary默认只监控Activity的内存泄露
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        ExecutorsUtils.execute(new Runnable() {
            @Override
            public void run() {
                Config.toastFileSavePath = SharedPreferenceUtils.getInstance(context).get(Config.KYE_TOAST_FILE, true);
                Config.deleteFilesLaunch = SharedPreferenceUtils.getInstance(context).get(Config.KYE_DLELETE_FILE, false);
                Config.monkeyTest = SharedPreferenceUtils.getInstance(context).get(Config.KYE_MONKEY_TEST, false);
                refWatcher = LeakCanary.install(ExampleApplication.this, new Settings()
                        .setToastFileSavePath(Config.toastFileSavePath)
                        .setDeleteFilesLaunch(Config.deleteFilesLaunch)
                        .setMonkeyTest(Config.monkeyTest)
                );
                ToastUtils.getInstance(context).s("LeakCanary install.");
            }
        });
    }

    public static Context getContext() {
        return context;
    }

    // 如果要监控APP中某个对象的内存泄露情况，可以通过RefWatcher类实现，需要在Application总对RefWatcher类做初始化操作
    public static RefWatcher getRefWatcher(Context context) {
        ExampleApplication application = (ExampleApplication) context.getApplicationContext();
        return application.refWatcher;
    }
}
