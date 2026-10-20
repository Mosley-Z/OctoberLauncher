# V1.0 发布构建混淆规则。
#
# 只保留运行期必须通过类名字符串、反射或序列化框架定位的少数类，
# 其余自有代码交由 R8 混淆与裁剪。每条保留规则对应一处可验证的运行期依赖，
# 新增保留规则时须同时给出依赖位置与失效后果。

# ---------------------------------------------------------------------------
# 一、模块反射入口
#
# OctoberRainApp.installModules 以固定类名字符串逐个执行 Class.forName，
# 再通过 getDeclaredConstructor().newInstance() 实例化，接口调用完成装配。
# 类名或无参构造器被重命名后，模块装载在启动阶段抛出 ClassNotFoundException，
# 桌面只显示空白内容，因此保留类名与无参构造器，成员仍参与混淆与裁剪。
-keep class com.shuiyu.octoberrain.feature.home.HomeModule { public <init>(); }
-keep class com.shuiyu.octoberrain.feature.widgets.WidgetsModule { public <init>(); }
-keep class com.shuiyu.octoberrain.feature.tray.TrayModule { public <init>(); }
-keep class com.shuiyu.octoberrain.feature.settings.SettingsModule { public <init>(); }
-keep class com.shuiyu.octoberrain.feature.content.ContentModule { public <init>(); }
-keep class com.shuiyu.octoberrain.feature.files.FilesModule { public <init>(); }
-keep class com.shuiyu.octoberrain.feature.wereadapp.WereadAppModule { public <init>(); }

# 模块内部类随反射入口保留类名，仅用于日志与异常堆栈对照，成员不保留。
-keepnames class com.shuiyu.octoberrain.feature.home.HomeModule$*
-keepnames class com.shuiyu.octoberrain.feature.widgets.WidgetsModule$*
-keepnames class com.shuiyu.octoberrain.feature.tray.TrayModule$*
-keepnames class com.shuiyu.octoberrain.feature.settings.SettingsModule$*
-keepnames class com.shuiyu.octoberrain.feature.content.ContentModule$*
-keepnames class com.shuiyu.octoberrain.feature.files.FilesModule$*
-keepnames class com.shuiyu.octoberrain.feature.wereadapp.WereadAppModule$*

# ---------------------------------------------------------------------------
# 二、配置序列化
#
# LauncherConfig 及子配置由 kotlinx.serialization 读写 JSON，编解码依赖编译期
# 生成的 *$$serializer 与属性 Companion.serializer()。生成物被裁剪或 Companion
# 被重命名后，配置读写回退为空配置，用户设置全部丢失。
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class com.shuiyu.octoberrain.core.common.** {
    *** Companion;
}
-keepclasseswithmembers class com.shuiyu.octoberrain.core.common.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shuiyu.octoberrain.core.common.**$$serializer { *; }
-dontnote kotlinx.serialization.**

# ---------------------------------------------------------------------------
# 三、组件实现
#
# WidgetCatalog 以工厂方式注册 WidgetProvider，HomeView 通过 WidgetView 接口绘制。
# 组件标识同时写入配置并用于日志定位，实现类与实现成员整体保留，
# 避免组件登记表与运行期绘制的静态可达性在激进优化下被切断。
-keep class * implements com.shuiyu.octoberrain.core.widget.WidgetProvider { *; }
-keep class * implements com.shuiyu.octoberrain.core.widget.WidgetView { *; }

# ---------------------------------------------------------------------------
# 四、微读墨记内核
#
# 内核以 jar 形式提供，宿主通过 KernelHost、DataSource、CardSurface 三类接口调用，
# 内部实现由 CardKernel 按字符串周期名分派。接口与实现类均以类名参与分派与日志，
# 整体保留以避免发布构建裁剪后运行期找不到实现。
-keep class com.inkread.weekread.kernel.** { *; }

