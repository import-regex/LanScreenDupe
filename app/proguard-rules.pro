# Shrink the code but don't rename classes or methods (keeps it readable)
-dontobfuscate

# Ktor & Coroutines
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**

# WebRTC / LiveKit
-keep class org.webrtc.** { *; }
-keep class livekit.org.webrtc.** { *; }
-dontwarn org.webrtc.**