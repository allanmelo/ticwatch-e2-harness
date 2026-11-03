# Guava / OkHttp: anotações opcionais ausentes no classpath Android.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tiles usa protobuf-lite, que lê campos por reflexão.
-keepclassmembers class * extends androidx.wear.tiles.protobuf.GeneratedMessageLite { <fields>; }
-keepclassmembers class * extends androidx.wear.protolayout.protobuf.GeneratedMessageLite { <fields>; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
