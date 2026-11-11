# Kash release rules. Compose, Navigation, WorkManager and DataStore ship their own consumer rules.
# Keep WorkManager workers constructible by reflection.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
# Strip debug logging from release builds.
-assumenosideeffects class android.util.Log { public static *** d(...); public static *** v(...); }
