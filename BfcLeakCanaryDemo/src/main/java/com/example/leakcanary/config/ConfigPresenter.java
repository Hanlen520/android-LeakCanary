package com.example.leakcanary.config;

import android.content.Context;

import com.eebbk.bfc.common.app.SharedPreferenceUtils;

/**
 * @author hesn
 * @function
 * @date 17-4-7
 * @company 步步高教育电子有限公司
 */

public class ConfigPresenter {
    /**
     * toast提示卡顿报告文件保存路径
     *
     * @return
     */
    public boolean enableToastFileSavePath() {
        return Config.toastFileSavePath;
    }

    /**
     * 每次初始化都删除所有卡顿报告文件
     *
     * @return
     */
    public boolean enableDeleteFilesLaunch() {
        return Config.deleteFilesLaunch;
    }

    /**
     * monkey测试
     *
     * @return
     */
    public boolean enableMonkeyTest() {
        return Config.monkeyTest;
    }

    public void save(Context context, boolean... switchs) {
        Config.toastFileSavePath = switchs[0];
        Config.deleteFilesLaunch = switchs[1];
        Config.monkeyTest = switchs[2];
        SharedPreferenceUtils.getInstance(context).put(Config.KYE_TOAST_FILE,Config.toastFileSavePath);
        SharedPreferenceUtils.getInstance(context).put(Config.KYE_DLELETE_FILE,Config.deleteFilesLaunch);
        SharedPreferenceUtils.getInstance(context).put(Config.KYE_MONKEY_TEST,Config.monkeyTest);
    }
}
