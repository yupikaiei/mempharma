# MemPharma ProGuard rules.
# The app keeps almost everything; these rules guard against aggressive
# minification of reflection-based libraries (Room / Hilt handle their own).

# Keep line numbers for readable stack traces in release crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
