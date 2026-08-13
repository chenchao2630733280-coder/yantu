# 拾旅 Android 混淆规则
# 当前 release 未开启 minify。kotlinx.serialization 相关规则保留，便于后续开启时使用。
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { companion object; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}