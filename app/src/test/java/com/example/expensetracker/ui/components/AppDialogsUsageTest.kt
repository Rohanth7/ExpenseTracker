package com.example.expensetracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * App code must open dialogs through AppDialogs.kt, which waits for unlock before creating one.
 * A plain dialog opened after the lock (a restore preview, say) draws over it and takes taps.
 * The lock window itself is the one exception, marked with [ALLOWED].
 */
class AppDialogsUsageTest {
    private val names = "AlertDialog|BasicAlertDialog|DatePickerDialog|TimePickerDialog|ModalBottomSheet|Dialog|" +
        "BottomSheetDialog|DialogFragment|MaterialAlertDialogBuilder"
    // A call (also fully qualified, or with a line break before "("), the View-system builder, or an
    // import that renames one of them.
    private val direct = Regex("""(?<!\w)($names)\s*\(|(?<!\w)AlertDialog\s*\.\s*Builder|^import\s+\S*\.($names)\s+as\s""", RegexOption.MULTILINE)

    /** Kotlin and Java files in every non-test source set (main, debug, release, …). */
    private fun appSources(): List<File> =
        (File("src").listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith("test") && !it.name.startsWith("androidTest") }
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" || it.extension == "java" }.toList() }

    private fun offenders(name: String, text: String): List<String> {
        val lines = text.lines()
        return direct.findAll(text).mapNotNull { match ->
            val lineNo = text.substring(0, match.range.first).count { it == '\n' }
            val line = lines[lineNo]
            val code = line.trimStart()
            if (code.startsWith("//") || code.startsWith("*") || line.contains(ALLOWED)) null else "$name:${lineNo + 1}"
        }.toList()
    }

    @Test
    fun appCodeUsesTheLockAwareDialogs() {
        val sources = appSources()
        assertTrue("run from the app module", sources.any { it.name == "MainActivity.kt" })
        val found = sources.filter { it.name != "AppDialogs.kt" }.flatMap { offenders(it.name, it.readText()) }
        assertEquals("Use AppAlertDialog / AppDatePickerDialog / AppModalBottomSheet", emptyList<String>(), found)
    }

    @Test
    fun theCheckSeesEveryWayOfWritingADialog() {
        val code = """
            AlertDialog(onDismissRequest = {})
            androidx.compose.material3.AlertDialog(onDismissRequest = {})
            Dialog(onDismissRequest = {}) { }
            ModalBottomSheet
                (onDismissRequest = {}) { }
            AlertDialog.Builder(context).show()
            class Picker : DialogFragment()
            import androidx.compose.material3.AlertDialog as Confirm
            AppAlertDialog(onDismissRequest = {})
            RestoreConfirmDialog(pending)
            val p = DialogProperties()
            // AlertDialog( in a comment
            Dialog(onDismissRequest = {}) { }  $ALLOWED
        """.trimIndent()
        assertEquals(listOf("x:1", "x:2", "x:3", "x:4", "x:6", "x:7", "x:8"), offenders("x", code))
    }

    companion object { const val ALLOWED = "// the lock window itself" }
}
