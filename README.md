## 一、说明
该库改造于LeakCanary，在LeakCanary的基础上修改了以下功能：
- 1、解决API版本必须要在21以上才能引用该库的问题；
- 2、解决跑monkey过程中因为误点击LeakCanary界面的Delete按钮删掉内存泄露详情的问题；
- 3、将内存泄露信息保存在磁盘的“leakcanary/应用包名/泄露时间.txt"文件下，方便查看详细的内存泄露信息。

### 升级清单文档
- 文档名称：[UPDATE.md](http://172.28.2.93/bfc/BfcLeakCanary/blob/master/UPDATE.md)

## 二、使用说明：

#### 前置条件
##### 1. 需要动态申请的敏感权限
        <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
        <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
        <uses-permission android:name="android.permission.READ_PHONE_STATE"/>
> 5.0以上系统还需要在代码中动态申请权限,具体请查看Android API

##### 2. 已申请的权限
        <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
        <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
        <uses-permission android:name="android.permission.READ_PHONE_STATE"/>
> 在Bfc-LeakCanary库中的AndroidManifest.xml中已申请以上所有权限

#### 配置
- 0、在project的build.gradle中作如下配置：

        // 引入BFC的网络配置
        apply from: "http://172.28.2.93/bfc/Bfc/raw/master/public-config/newbfc-config.gradle"
        allprojects {
            repositories {
                // 配置BFC各版本的仓库地址
                maven { url bfcBuildConfig.MAVEN_URL }
                // Bfc项目的灰度仓库
                maven { url bfcBuildConfig.MAVEN_RC_URL }
                jcenter()
            }
        }
            
- 1、在主module的build.gradle文件中依赖该库：

        dependencies {
           // 只在debug模式下工作
           debugCompile bfcBuildConfig.deps["bfc-leakcanary"]
           // 下单版本和自动化测试模式下，屏蔽掉leakcanary，因为该库在检查内存泄露问题时会导致应用的性能问题，应谨慎使用
           releaseCompile bfcBuildConfig.deps["bfc-leakcanary-no-op"]
           testCompile bfcBuildConfig.deps["bfc-leakcanary-no-op"]
         }
         
- 2、在主工程的Application类中做初始化：

        public class ExampleApplication extends Application {
        
              @Override
              public void onCreate() {
                super.onCreate();
                initLeakCanary();
              }
              
              private void initLeakCanary(){
                 // leakcanary默认只监控Activity的内存泄露
                 if (LeakCanary.isInAnalyzerProcess(this)) {
                   return;
                 }
                 refWatcher = LeakCanary.install(this);
              }
              
              // 如果要监控APP中某个对象的内存泄露情况，可以通过RefWatcher类实现，需要在Application总对RefWatcher类做初始化操作
              public static RefWatcher getRefWatcher(Context context) {
                  ExampleApplication application = (ExampleApplication) context.getApplicationContext();
                  return application.refWatcher;
              }
              
              private RefWatcher refWatcher;
        }
        
- 3、带参数的初始化

        refWatcher = LeakCanary.install(this, new Settings()
                                .setToastFileSavePath(Config.toastFileSavePath)     //内存泄漏是否toast提示内存泄漏报告文件保存路径,默认 true 提示
                                .setDeleteFilesLaunch(Config.deleteFilesLaunch)     //是否每次初始化时,删除之前的内存泄漏报告文件,默认 false 不删除
                                .setMonkeyTest(Config.monkeyTest)                   //monkey测试模式,默认 false
                        );

> **跑monkey** 时候设置setMonkeyTest(true),可以避免monkey跑到显示内存泄漏信息机界面, **内存泄漏的图标会消失** ,这属于正常现象.
>注意:如果发现app自己的 **桌面图标消失** 了,是桌面刷新不及时,请到 设置 --> 应用程序 --> 清除桌面数据,图标就可以正常显示了.

- 4、将需要监控的对象传给RefWatcher进行监控，比如Fragment（通常是在某个对象生命周期结束时监控）：

        public abstract class BaseFragment extends Fragment {
              @Override
              public void onDestroyView() {
                super.onDestroyView();
                RefWatcher refWatcher = ExampleApplication.getRefWatcher(getActivity());
                refWatcher.watch(this);
              }
        }

#### 可选功能
- LeakCanary 桌面图标名称修改：

    strings.xml

        <string name="leak_canary_display_activity_label">隔壁老王的Leaks</string>

- 删除内存泄漏报告记录文件

        LeakCanary.deleteAllFiles(context);


## 三、更为详细地使用说明请查看leakcanary的[wiki文档](https://github.com/square/leakcanary/wiki/FAQ)

## 四、TODO

- 将内存泄露详情上传到后台；

## 五、依赖
本项目已集成:
- bfc-common
- haha:2.0.3
