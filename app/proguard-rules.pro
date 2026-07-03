# Keep Room generated code paths intact.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn org.xmlpull.v1.**

# Hilt / Dagger generated components rely on reflection-free codegen; defaults suffice.
