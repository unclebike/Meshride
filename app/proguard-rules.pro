# Karoo-ext
-keep class io.hammerhead.karooext.** { *; }

# Protobuf
-keep class com.google.protobuf.** { *; }

# Meshtastic protos
-keep class com.unclebike.meshride.ble.MeshtasticProtos** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase

# Hilt
-keep class dagger.hilt.** { *; }
