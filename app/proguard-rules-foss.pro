# FOSS variant: Cast dynamic feature module references Google Play Services
# classes that are not available in the FOSS build. Suppress R8 errors for
# these missing classes — the cast module is non-functional without GMS.
-dontwarn com.google.android.gms.**
