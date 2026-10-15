package com.example.expensetracker.ui.util

import android.content.Context
import android.content.pm.ApplicationInfo

/** True for debug builds; developer-only tools are hidden otherwise. */
fun isDebugBuild(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
