package com.example.leakcanary;

import android.app.Activity;
import android.os.Bundle;

/**
 * @author hesn
 * @function 测试基activity类
 * @date 16-8-24
 * @company 步步高教育电子有限公司
 */

public abstract class ABaseActivity extends Activity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    /**
     * 设置布局文件　layout id
     * @return
     */
    protected abstract int setLayoutId();

    /**
     * 初始化控件
     */
    protected abstract void initView();

    /**
     * 初始化数据
     */
    protected abstract void initData();

    /**
     * 根据id找控件
     * @param id
     * @param <T>
     * @return
     */
    protected <T> T findView(int id){
        return (T)findViewById(id);
    }

    private void init(){
        setContentView(setLayoutId());
        initView();
        initData();
    }

}
