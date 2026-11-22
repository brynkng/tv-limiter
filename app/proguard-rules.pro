# kotlinx.serialization ships its own keep rules; these keep the saved file
# formats readable if a class is only ever reached through its serializer.
-keepclassmembers @kotlinx.serialization.Serializable class dev.tvlimiter.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
