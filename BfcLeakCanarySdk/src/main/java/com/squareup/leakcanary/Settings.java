package com.squareup.leakcanary;

/**
 * @author hesn
 * @function
 * @date 17-4-14
 * @company 步步高教育电子有限公司
 */

public class Settings {

    /**
     * 是否每次初始化都删除所有内存泄漏报告文件
     */
    private boolean deleteFilesLaunch = false;

    /**
     * 是否toast提示内存泄漏报告文件保存路径
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

    /**
     * 是否monkey测试
     * <p>
     * 跑monkey时候请设置为true,避免monkey经常跑到显示内存泄漏信息机界面,效果:<br>
     * 1.如果设置通知栏提示,在monkeyTest下点击通知栏不会跳转到内存泄漏信息界面{@link com.squareup.leakcanary.internal.DisplayLeakActivity};<br>
     * 2.桌面屏蔽内存泄漏信息界面{@link com.squareup.leakcanary.internal.DisplayLeakActivity}入口.<br>
     * <p/>
     * <p>
     * 注意:桌面刷新是不及时的.如果发现app自己的桌面图标消失了,是因为桌面没有即时更新,请到 设置 --> 应用程序 --> 清除桌面数据,图标就可以正常显示了.
     * <p/>
     *
     * @return 默认false提示
     */
    public Settings setMonkeyTest(boolean monkeyTest) {
        this.monkeyTest = monkeyTest;
        return this;
    }
}
