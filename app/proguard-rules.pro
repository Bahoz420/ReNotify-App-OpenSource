# Keep file names and line numbers in stack traces, so a crash reported to
# our own telemetry can be read back with the mapping file
# (app/build/outputs/mapping/release/mapping.txt, kept per release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
