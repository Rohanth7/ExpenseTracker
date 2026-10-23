package com.example.expensetracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Screens must open dialogs through AppDialogs.kt, which hides them while the app is locked.
 * A plain Material dialog opened after the lock (a restore preview, say) draws over it.
 */
class AppDialogsUsageTest {
    private val direct = Regex("""(?<![\w.])(AlertDialog|BasicAlertDialog|DatePickerDialog|TimePickerDialog|ModalBottomSheet)\(""")

    @Test
    fun screensUseTheLockAwareDialogs() {
        val sources = File("src/main/java").walk().filter { it.extension == "kt" }.toList()
        check(sources.isNotEmpty()) { "run from the app module" }
        val offenders = sources
            .filter { it.name != "AppDialogs.kt" && it.name != "MainActivity.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (direct.containsMatchIn(line) && !line.trimStart().startsWith("//")) "${file.name}:${i + 1}" else null
                }
            }
        assertEquals("Use AppAlertDialog / AppDatePickerDialog / AppModalBottomSheet", emptyList<String>(), offenders)
    }
}
