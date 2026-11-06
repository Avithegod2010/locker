# BouncyCastle's optional LDAP certificate-store code references javax.naming,
# which is not part of Android. Locker only uses its Argon2 implementation.
-dontwarn javax.naming.**

# Readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
