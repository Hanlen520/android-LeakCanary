package com.squareup.leakcanary;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.eebbk.bfc.common.app.ToastUtils;
import com.eebbk.bfc.common.file.FileUtils;
import com.squareup.leakcanary.internal.LeakCanaryInternals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author hesn
 * @function Log writer which runs in standalone thread.
 * @date 17-4-12
 * @company 步步高教育电子有限公司
 */

public class LogWriter {

    private static final Object SAVE_DELETE_LOCK = new Object();
    public static final String FILE_SUFFIX = ".txt";
    private static final String TAG = LogWriter.class.getName();

    /**
     * 获取内存泄露文件保存路径
     *
     * @param context
     * @return
     */
    public static String getLogPath(Context context) {
        String packageName = context == null ? "default" : context.getPackageName();
        return Environment.getExternalStorageDirectory().getPath() + "/leakcanary/" + packageName + "/";
    }

    /**
     * 将内存泄露文件保存到本地
     */
    public static void saveLeakFile2Local(Context context, String leakInfoString) {
        String fileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS'" + FILE_SUFFIX + "'", Locale.US).format(new Date());
        String leakFileName = getLogPath(context) + fileName;
        synchronized (SAVE_DELETE_LOCK) {
            CanaryLog.d("%s", "leakFileName = " + leakFileName);
            File leakFile = new File(leakFileName);
            if (createFileOrExists(leakFile)) {
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(leakFile);
                    ObjectOutputStream oos = new ObjectOutputStream(fos);
                    oos.writeObject(leakInfoString);

                    toastSavePath(context, leakFileName);
                    //通知媒体库更新
                    scanFile(context, leakFileName);
                } catch (IOException e) {
                    CanaryLog.d(e, "Could not save leak analysis result to disk.");
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }
    }

    public static void deleteAll(final Context context) {
        LeakCanaryInternals.executeOnFileIoThread(new Runnable() {
            @Override
            public void run() {
                synchronized (SAVE_DELETE_LOCK) {
                    try {
                        String path = getLogPath(context);
                        Log.i(TAG, "deleteAll() path:" + path);
                        FileUtils.deleteDir(new File(path));
                        new DefaultLeakDirectoryProvider(context).clearLeakDirectory();
                        scanFile(context, path);
                    } catch (Throwable e) {
                        CanaryLog.d("%s", "deleteAll: " + e.toString());
                    }
                }
            }
        });
    }

    /**
     * 创建文件夹<br/>
     * <p>
     * 如果存在,则返回; 如果不存在则创建
     *
     * @param file 文件
     * @return {@code true}: 存在或创建成功<br>{@code false}: 创建失败
     */
    private static boolean createDirOrExists(File file) {
        // 如果存在，是目录则返回true，是文件则返回false，不存在则返回是否创建成功
        return file != null && (file.exists() ? file.isDirectory() : file.mkdirs());
    }


    /**
     * 创建文件
     * <p>
     * 如果文件存在,则返回; 不存在,就创建
     *
     * @param file 需要创建的文件
     * @return {@code true}: 存在或创建成功<br>
     * {@code false}: 创建文件失败
     */
    static boolean createFileOrExists(File file) {
        if (file == null) return false;
        // 如果存在，是文件则返回true，是目录则返回false
        if (file.exists()) return file.isFile();
        if (!createDirOrExists(file.getParentFile())) return false;
        try {
            return file.createNewFile();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 通知媒体库更新
     */
    public static void scanFile(Context context, String filePath) {
        if (context == null) {
            Log.w(TAG, "media scanner scan file fail, context == null");
            return;
        }
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setData(Uri.fromFile(new File(filePath)));
        context.sendBroadcast(scanIntent);
    }

    /**
     * 提示内存泄漏报告文件保存路径
     *
     * @param context
     * @param path
     */
    private static void toastSavePath(Context context, String path) {
        if (context == null || TextUtils.isEmpty(path)) {
            return;
        }

        if (!LeakCanary.getSettings().isToastFileSavePath()) {
            return;
        }

        ToastUtils.getInstance(context).l("LeakCanary file save path:" + path);
    }

    private LogWriter() {
        throw new InstantiationError("Must not instantiate this class");
    }
}
