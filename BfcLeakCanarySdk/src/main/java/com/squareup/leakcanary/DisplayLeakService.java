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

import android.app.PendingIntent;
import android.os.SystemClock;

import com.squareup.leakcanary.internal.DisplayLeakActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.text.format.Formatter.formatShortFileSize;
import static com.squareup.leakcanary.LeakCanary.leakInfo;
import static com.squareup.leakcanary.internal.LeakCanaryInternals.classSimpleName;
import static com.squareup.leakcanary.internal.LeakCanaryInternals.showNotification;

/**
 * Logs leak analysis results, and then shows a notification which will start {@link
 * DisplayLeakActivity}.
 * <p>
 * You can extend this class and override {@link #afterDefaultHandling(HeapDump, AnalysisResult,
 * String)} to add custom behavior, e.g. uploading the heap dump.
 */
public class DisplayLeakService extends AbstractAnalysisResultService{

    /**
     * 1、将泄露信息转换为字符串并在log中打印出来；
     * 2、将内存泄露信息保存为.result后缀格式的文件；
     * 3、弹通知通报内存泄露详情；
     * 4、提供dummy方法，允许自定义对内存泄露信息的操作，比如上传到服务器
     */
    @Override
    protected final void onHeapAnalyzed(HeapDump heapDump, AnalysisResult result){
        // 将内存泄露信息转换为可理解的字符串
        String leakInfo = leakInfo(this, heapDump, result, true);
        // 打印字符串信息
        CanaryLog.d("%s", leakInfo);

        // 将内存泄露文件保存到本地
        LogWriter.saveLeakFile2Local(getApplicationContext(), leakInfo);

        boolean resultSaved = false;
        boolean shouldSaveResult = result.leakFound || result.failure != null;
        if(shouldSaveResult){
            // 重命名内存快照文件
            heapDump = renameHeapdump(heapDump);
            // 保存为后缀为.result格式的文件
            resultSaved = saveResult(heapDump, result);
        }

        PendingIntent pendingIntent;
        String contentTitle;
        String contentText;

        if(!shouldSaveResult){
            contentTitle = getString(R.string.leak_canary_no_leak_title);
            contentText = getString(R.string.leak_canary_no_leak_text);
            pendingIntent = null;
        }else if(resultSaved){
            pendingIntent = DisplayLeakActivity.createPendingIntent(this, heapDump.referenceKey);
            if(result.failure == null){
                String size = formatShortFileSize(this, result.retainedHeapSize);
                String className = classSimpleName(result.className);
                if(result.excludedLeak){
                    contentTitle = getString(R.string.leak_canary_leak_excluded, className, size);
                }else{
                    contentTitle = getString(R.string.leak_canary_class_has_leaked, className, size);
                }
            }else{
                contentTitle = getString(R.string.leak_canary_analysis_failed);
            }
            contentText = getString(R.string.leak_canary_notification_message);
        }else{
            contentTitle = getString(R.string.leak_canary_could_not_save_title);
            contentText = getString(R.string.leak_canary_could_not_save_text);
            pendingIntent = null;
        }

        // New notification id every second.
        int notificationId = (int) (SystemClock.uptimeMillis()/1000);
        // 弹通知，报告内存泄露详情
        showNotification(this, contentTitle, contentText, pendingIntent, notificationId);
        // 在这里可以针对内存泄露做进一步处理，比如将内存泄露信息上传到服务器
        afterDefaultHandling(heapDump, result, leakInfo);
    }

    private boolean saveResult(HeapDump heapDump, AnalysisResult result){
        File resultFile = new File(heapDump.heapDumpFile.getParentFile(), heapDump.heapDumpFile.getName() + ".result");
        FileOutputStream fos = null;
        try{
            fos = new FileOutputStream(resultFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(heapDump);
            oos.writeObject(result);
            return true;
        }catch(IOException e){
            CanaryLog.d(e, "Could not save leak analysis result to disk.");
        }finally{
            if(fos != null){
                try{
                    fos.close();
                }catch(IOException ignored){
                }
            }
        }
        return false;
    }

    private HeapDump renameHeapdump(HeapDump heapDump){
        String fileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS'.hprof'", Locale.US).format(new Date());

        File newFile = new File(heapDump.heapDumpFile.getParent(), fileName);
        boolean renamed = heapDump.heapDumpFile.renameTo(newFile);
        if(!renamed){
            CanaryLog.d("Could not rename heap dump file %s to %s", heapDump.heapDumpFile.getPath(),
                    newFile.getPath());
        }
        return new HeapDump(newFile, heapDump.referenceKey, heapDump.referenceName,
                heapDump.excludedRefs, heapDump.watchDurationMs, heapDump.gcDurationMs,
                heapDump.heapDumpDurationMs);
    }

    /**
     * You can override this method and do a blocking call to a server to upload the leak trace and
     * the heap dump. Don't forget to check {@link AnalysisResult#leakFound} and {@link
     * AnalysisResult#excludedLeak} first.
     */
    protected void afterDefaultHandling(HeapDump heapDump, AnalysisResult result, String leakInfo){
    }
}
