# UnboundID LDAP SDK - keep all classes for reflection
-keep class com.unboundid.** { *; }
-dontwarn com.unboundid.**

# JDK classes not available on Android runtime
-dontwarn javax.naming.**
-dontwarn javax.security.sasl.**
-dontwarn java.beans.**
-dontwarn javax.net.ssl.**
-dontwarn java.security.**

# Preserve generic signatures used by SDK reflection
-keepattributes Signature,InnerClasses,EnclosingMethod
