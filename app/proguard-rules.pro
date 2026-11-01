# OmoAuto R8 rules.
# Compose, AndroidX and Kotlin ship their own consumer rules; only app-specific needs go here.

# Readable stack traces in shared diagnostics.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The foreground service is referenced from the manifest (kept automatically) and through
# the Application interface below.
-keep class * implements com.logicsekai.omoauto.streaming.ProjectionEngineOwner
