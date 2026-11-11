# ── R8 保活规则（2026-11-10 打开 release 混淆时加的）──

# kotlinx.serialization：@Serializable 类靠生成的 serializer，反射找 Companion
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class cn.dkarbon.enroute.data.** {
    *** Companion;
}
-keepclasseswithmembers class cn.dkarbon.enroute.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager 用反射实例化 Worker
-keep class cn.dkarbon.enroute.data.ReminderWorker { *; }
-keep class cn.dkarbon.enroute.data.DailyRescheduleWorker { *; }

# Glance 小组件 Receiver 由系统按名字启动
-keep class cn.dkarbon.enroute.widget.TodayWidgetReceiver { *; }
