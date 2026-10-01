# MathClock has no reflection-based serialization. Room and Compose ship their own consumer rules.
# Keep line numbers for readable crash traces when R8 is enabled (mapping.txt is preserved by CI).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
