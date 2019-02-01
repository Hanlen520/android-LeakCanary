package com.example.leakcanary;

import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.leakcanary.config.ConfigActivity;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.internal.CommonUtils;


/**
 * @author hesn
 * @function
 * @date 17-4-7
 * @company 步步高教育电子有限公司
 */

public class MenuActivity extends ABaseActivity {

    private TextView mVersionTv;
    private LinearLayout mNotificationTipLl;

    @Override
    protected int setLayoutId() {
        return R.layout.activity_menu_layout;
    }

    @Override
    protected void initView() {
        mVersionTv = findView(R.id.versionTv);
        mNotificationTipLl = findView(R.id.notificationTipLl);
    }

    @Override
    protected void initData() {
        mVersionTv.setText(TextUtils.concat(
                "LeakCanary版本：", LeakCanary.getVersion()
        ));
    }

    /**
     * 测试
     *
     * @param view
     */
    public void onTest(View view) {
        startActivity(new Intent(this, MainActivity.class));
    }

    /**
     * 配置
     *
     * @param view
     */
    public void onConfig(View view) {
        startActivity(new Intent(this, ConfigActivity.class));
    }

    public void onNotificationSetting(View view) {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        mNotificationTipLl.setVisibility(!CommonUtils.isNotificationEnabled(this)
                ? View.VISIBLE : View.GONE);
        super.onResume();
    }
}
