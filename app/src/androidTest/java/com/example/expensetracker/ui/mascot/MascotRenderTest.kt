package com.example.expensetracker.ui.mascot

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.expensetracker.ui.theme.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** She is drawn in code, so nothing but a look at the pixels says she still draws at all. */
@RunWith(AndroidJUnit4::class)
class MascotRenderTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(tag: String): Bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

    @Test fun everyMoodDrawsThePotWithItsOwnFace() {
        compose.setContent {
            Column(Modifier.background(Canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.testTag("pots"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MascotMood.entries.forEach { Mascot(it, Modifier.width(64.dp).testTag("pot-$it")) }
                }
                Row(Modifier.testTag("faces"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MascotMood.entries.forEach { MascotFace(it, Modifier.width(34.dp).testTag("face-$it")) }
                }
            }
        }
        val pots = MascotMood.entries.associateWith { capture("pot-$it") }
        val faces = MascotMood.entries.associateWith { capture("face-$it") }

        // Low on her left side there is only clay: the same colour in every mood.
        for ((mood, pot) in pots) {
            assertEquals("$mood body", 0xFFCF6E44.toInt(), pot.getPixel(pot.width * 30 / 120, pot.height * 88 / 124))
        }
        for (set in listOf(pots, faces)) {
            val moods = set.keys.toList()
            for (i in moods.indices) for (j in i + 1 until moods.size) {
                assertFalse("${moods[i]} and ${moods[j]} look the same", set.getValue(moods[i]).sameAs(set.getValue(moods[j])))
            }
        }

        // Left on the device for a person to look at: adb exec-out run-as <package> cat cache/mascot/pots.png
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mascot").apply { mkdirs() }
        for (tag in listOf("pots", "faces")) File(dir, "$tag.png").outputStream().use { capture(tag).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
