package com.squareup.leakcanary;

/**
 * @author hesn
 * @function
 * @date 17-4-14
 * @company 步步高教育电子有限公司
 */

public class Settings {

    /**
     * 是否每次初始化都删除所有内存溢出报告文件
     */
    private boolean deleteFilesLaunch = false;

    /**
     * 是否toast提示内存溢出报告文件保存路径
     */
    private boolean toastFileSavePath = true;

    /**
     * 是否monkey测试
     */
    private boolean monkeyTest = false;

    public boolean isDeleteFilesLaunch() {
        return deleteFilesLaunch;
    }

    public Settings setDeleteFilesLaunch(boolean deleteFilesLaunch) {
        this.deleteFilesLaunch = deleteFilesLaunch;
        return this;
    }

    public boolean isToastFileSavePath() {
        return toastFileSavePath;
    }

    public Settings setToastFileSavePath(boolean toastFileSavePath) {
        this.toastFileSavePath = toastFileSavePath;
        return this;
    }

    public boolean isMonkeyTest() {
        return monkeyTest;
    }

    public Settings setMonkeyTest(boolean monkeyTest) {
        this.monkeyTest = monkeyTest;
        return this;
    }
}
