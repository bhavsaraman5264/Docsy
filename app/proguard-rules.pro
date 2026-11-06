# Debug-level logs release build se poori tarah hata do. Lambda bhi saath me hat jata hai.
-assumenosideeffects class app.kholo.reader.core.log.AppLog {
    public final void d(...);
}

-dontwarn com.gemalto.jp2.**
-dontwarn javax.naming.**
-dontwarn org.bouncycastle.**

# Crash report me sahi line number aaye. Asli file ka naam APK me nahi jata.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
