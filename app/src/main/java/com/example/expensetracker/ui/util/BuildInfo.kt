package com.example.expensetracker.ui.util

import android.content.Context
import android.content.pm.ApplicationInfo

/** True for debug builds; developer-only tools are hidden otherwise. */
fun isDebugBuild(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

/** The installed version's name ("1.0"), as the launcher and Play show it. */
fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

/** The Settings footer: the app's own name and real version, e.g. "MyMoney · v1.0". */
fun appFooter(appName: String, versionName: String): String =
    if (versionName.isBlank()) appName else "$appName · v$versionName"
