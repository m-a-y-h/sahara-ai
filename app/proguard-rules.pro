# ───────────────────────────────────────────────────────────────────────────
#  SAHARA release (R8) keep rules
#
#  Debug ships with minify OFF, so this file only matters for `release`
#  (isMinifyEnabled = true, isShrinkResources = true). Without these keeps R8
#  renames/strips the reflection-driven (de)serialization paths and the app
#  compiles fine but breaks at RUNTIME — null fields off the network, empty
#  Firebase reads. The app's own code is a tiny slice of the dex (libraries
#  dominate), so keeping the model + remote layers whole costs almost nothing
#  in size while removing the main release-crash risk.
#
#  After changing these, build a release APK and smoke-test on a device:
#  chat, lens scan, voice analyze, the community feed, and sign-in.
# ───────────────────────────────────────────────────────────────────────────

# --- App data models -------------------------------------------------------
# (De)serialized by Gson (manual toJson/fromJson) and by Firebase
# RTDB/Firestore reflection. Keep the no-arg constructors + field names Gson
# and Firebase match JSON keys against. FirestoreMessage etc. use @PropertyName.
-keep class pk.edu.ucp.saharaai.data.model.** { *; }
-keepclassmembers class pk.edu.ucp.saharaai.data.model.** {
    <init>(...);
    <fields>;
}

# --- Network DTOs next to the OkHttp clients -------------------------------
# Parsed via gson.fromJson(raw, XxxResponse::class.java) / toJson(request).
# These have no @SerializedName, so Gson relies on the raw field names — keep
# the whole remote layer's members so the JSON keys survive obfuscation.
-keep class pk.edu.ucp.saharaai.data.remote.** { *; }

# --- Gson internals --------------------------------------------------------
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# --- Firebase RTDB / Firestore property mapping ----------------------------
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class pk.edu.ucp.saharaai.** {
    @com.google.firebase.database.PropertyName <methods>;
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.PropertyName <fields>;
}

# --- Enums (Gson serializes them by name) ----------------------------------
-keepclassmembers enum pk.edu.ucp.saharaai.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Third-party safety nets (most ship consumer rules) --------------------
# STOMP-over-WebSocket lib is old and has no consumer rules of its own.
-keep class ua.naiksoftware.stomp.** { *; }
-dontwarn ua.naiksoftware.stomp.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn javax.annotation.**
