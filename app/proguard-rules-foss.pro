# FOSS variant: Play Core and the Cast framework are Play-flavor dependencies, so the
# FOSS base APK contains no GMS references of its own. `:cast` is still declared in
# `dynamicFeatures` (AGP has no per-flavor form of that setting), so keep suppressing
# R8 warnings for the classes it would otherwise pull into the reference graph.
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.play.core.**
