- 2017-3-16 10:28：

1、将LeakCanary改造成Bfc的一个子库；
2、将leakcanary-watcher、leakcanary-analyzer的代码合并到BfcLeakCanarySdk，方便对代码的管理；
3、代码取自leakcanary的1.6-snapshot版本；
4、解决API版本必须要在21以以上才能引用该库的问题；
5、解决跑monkey过程中因为误点击LeakCanary界面的Delete按钮删掉内存泄露详情的问题；
6、将内存泄露信息保存在磁盘的“leakcanary/应用包名/泄露时间.txt"文件下，方便查看详细的内存泄露信息。

- 2017-3-16 12:53：

1、增加对BFC依赖的配置。

- 2017-3-16 16:15：

1、版本号改为2.2.1-beta；
2、增加长按某一项删除所有内存泄露信息的功能，避免跑monkey时的误触发；
3、增加SDKVersion，初始化和每个泄露信息中都带有版本号，方便跟踪对应的sdk信息。

- 2017-3-16 19:22:

1、修改说明文档中refWatcher没有初始化的问题。

- 2017-3-17 08:33:

1、版本号改为3.0.0；

- 2017-6-7