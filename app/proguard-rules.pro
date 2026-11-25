# Gson reads and writes these classes by reflection, using their field names as the JSON keys.
# Backups, message rules and the setup restore note are stored that way, so renaming or removing
# a field here would make files written by another build unreadable. Add any new class that goes
# through Gson to this list.
-keep class com.example.expensetracker.data.db.entity.** { <fields>; <init>(...); }
-keep class com.example.expensetracker.data.backup.BackupData { <fields>; <init>(...); }
-keep class com.example.expensetracker.data.backup.BackupSettings { <fields>; <init>(...); }
-keep class com.example.expensetracker.data.recurring.BillMonth { <fields>; <init>(...); }
-keep class com.example.expensetracker.sms.ParsingRule { <fields>; <init>(...); }
-keep class com.example.expensetracker.sms.ParsingRule$Kind { *; }
-keep class com.example.expensetracker.ui.screens.onboarding.RestoredInfo { <fields>; <init>(...); }

# Gson 2.10 ships no R8 rules. TypeToken subclasses carry the list's element type in their
# generic signature.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken

# Readable stack traces in Play Console's crash reports (R8's mapping file is uploaded with the build).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
