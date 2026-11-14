# R8 shrinks and optimizes; names stay readable (the code is public, and crash traces stay clear).
-dontobfuscate

# Zmanim and the Hebrew calendar: plain Java with no reflection, kept whole to be safe.
-keep class com.kosherjava.zmanim.** { *; }
