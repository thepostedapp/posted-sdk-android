# Detour SDK consumer ProGuard rules
# These rules are automatically included when consumers enable R8/ProGuard.

# Keep Gson model classes used for API serialization/deserialization.
# Gson uses reflection to access @SerializedName fields at runtime.
-keep class com.swmansion.detour.models.ProbabilisticFingerprint { *; }
-keep class com.swmansion.detour.models.DeterministicFingerprint { *; }
-keep class com.swmansion.detour.models.LocaleInfo { *; }
-keep class com.swmansion.detour.models.LinkMatchResponse { *; }
-keep class com.swmansion.detour.models.ShortLinkResponse { *; }
