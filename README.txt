星海 · 纯自研影视客户端（v2.0.0）
=================================

这是一套从零写的安卓影视客户端，目标是复刻「影视仓 3.3.8」这一系的界面与交互：
底部五项导航、首页分类+筛选+海报网格、详情页线路/选集/相关推荐、
播放页的全屏控制层与各个面板、搜索页、接口管理、我的页。

整份代码零第三方依赖：不引 androidx，不引任何 UI 库、播放器 SDK、广告 SDK。
界面容器、网格、标签条、面板、字幕渲染、HLS 代理、图片加载都是自己实现的。


一、目录结构
------------
app/src/main/AndroidManifest.xml   清单
app/src/main/java/com/zaka/xinghai/
    core/App.java                  Application：线程池、全局上下文
    util/U.java                    单位换算、格式化、文本清洗等
    util/Img.java                  自研图片加载（内存 LRU + 磁盘缓存 + 并发限流）
    store/Store.java               本地存储：站点 / 历史 / 收藏 / 各项设置
    model/                         Site Cate Filter Vod Record
    net/Http.java                  网络层：连接复用、gzip/deflate 兜底、多地址并发竞争
    net/Cms.java                   采集解析：分类 / 列表 / 详情 / 搜索（JSON + XML）
    net/Cfg.java                   接口订阅配置解析（TVBox 那套 json / xml）
    net/AdFilter.java              去广告引擎（四层）
    net/HlsProxy.java              本地 HLS 代理 + 分片缓存 + 网速统计
    net/M3u.java                   直播源解析
    subtitle/                      字幕：Cue / SubParse / SubStore / SubSearch
    view/                          Poster SubtitleView StarBar FlowLayout Tabs Sheet
                                   VodInfoView VodParts
    player/XPlayer.java            播放内核封装（MediaPlayer + SurfaceView）
    adapter/VodAdapter.java        海报网格适配器
    ui/                            Splash Main Detail Play Search Site Web
    ui/Page.java + ui/page/        五个页面
    ui/dialog/                     IntroDialog ListSheet InputDialog FilterSheet SubtitleSheet
app/src/main/res/layout/           18 个布局
app/src/main/res/drawable/         92 个形状 / 矢量图标（脚本生成，见 build/gen_res.py）
build/                             命令行构建、验证与测试脚本
test/JvmCheck.java                 解析层真实数据自测
keys/                              签名密钥（自签名，PKCS12）


二、怎么编译
------------
方式一，命令行（不需要 Gradle / Android Studio）：
    export ANDROID_SDK_ROOT=/path/to/android-sdk     # 需要 build-tools 与 platforms
    export JDK_HOME=/path/to/jdk                     # 可选，默认用 PATH 里的
    bash build/build.sh --sign
产物在 build/out/xinghai-signed.apk。

方式二，Android Studio：
    直接打开本目录（Gradle 工程已配好），Run 即可。
    namespace 与命令行共用一个 package，两边源码同一份，不用改。

重新生成图标与形状资源：python3 build/gen_res.py


三、自测与验证
--------------
bash build/run_tests.sh     解析层 / 去广告 / 字幕解析的真实数据自测
                            （夹具在 build/fixtures，真接口抓下来的返回 + 一条真 ASS 字幕 + 真 m3u8）
bash build/verify.sh        产物静态校验：包信息、签名、对齐、dex 完整性、
                            源码类与 dex 对照、清单组件一致性、布局与代码 ID 交叉检查、
                            布局内自定义控件类是否真在 dex


四、实现要点
------------
1. 分类兜底：不少采集源直接请求 ac=list 返回的是数据而不是分类，
   分类得从 ac=list&t=1 的 class 字段拿；拿不到就从列表数据的 type_id 聚合，
   保证任何源都有分类可用。

2. 去广告四层：
   - 线路 / 剧集名的推广条目清洗（判定保守，全判广告时至少留一条，不至于整片看不了）
   - 播放地址上的推广 / 统计参数剥离（保留 t / pg 这类采集参数）
   - HLS 播放列表里的插播广告段：SCTE-35 的 CUE-OUT / CUE-IN 区间、广告域分片整段剔除，
     剔的时候连 #EXTINF 一起带走，不留孤儿标签
   - 中转跳转页解开成真实地址

3. 加速：
   - 连接复用、分级超时
   - 分类探测与搜索改并发抢第一个有效响应
   - 分片本地缓存 + 首片预取（清洗后的播放列表把分片指回本地代理，缓存才真正生效）
   - 解析结果缓存
   - 起播超时自动换线，播放出错自动换线；换不动就如实说明

4. 字幕：搜索 → 下载 → 转 UTF-8 → 解析（SRT / ASS / VTT）→ 自绘渲染，
   支持整体时间轴偏移（±0.1s / ±1s / ±5s，毫秒级，改完立刻生效，不用重新加载），
   支持开关、字号、本地缓存。关掉字幕就不再自动匹配。

5. 播放控制：倍速、画面比例（自适应 / 16:9 / 4:3 / 铺满 / 原始）、
   手势（左亮度 / 右音量 / 横滑快进 / 单击显隐控制层 / 双击暂停）、锁屏、
   片头片尾跳过、进度记忆续播、小窗（Android 8+）。


五、没做的部分（如实说明，不糊弄）
----------------------------------
这些需要第三方商业 SDK 或额外数据源，纯自研工程里不引：
  * 弹幕（需要弹幕源接入；控制条上的按钮点了会说明，不会假动作）
  * DLNA 投屏设备发现、网盘播放、短剧三方源、推送、广告位、下载管理器
  * 模仿参照里那些依赖商业 SDK 的商店 / 会员 / 扫码登录功能
  * 音轨信息能读出来，但这条内核不保证能切换音轨


六、已知环境限制
----------------
构建环境里没有虚拟化，起不了安卓模拟器，所以「真机点一遍」这步没法在这里完成。
静态校验（包结构 / 签名 / 对齐 / dex / 组件 / 布局与代码 id / 自定义控件类）全部通过，
解析层与去广告、字幕解析是用真实数据跑过的。装上去如有异常，
把现象和片名发过来，另外闪退的话抓一份带 FATAL EXCEPTION 的 logcat。

六、仓库与包名
------------
GitHub：https://github.com/yanchenwuxi/Xinghai
应用显示名：星海
Java 包名 / applicationId：com.zaka.xinghai
