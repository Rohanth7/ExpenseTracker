package com.example.expensetracker.data.privacy

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android's automatic Google backup must not upload the database, settings or app files: payment
 * data only leaves the phone through MyMoney's own backup, which the user starts and controls.
 */
class BackupRulesTest {
    private val privateDomains = setOf("database", "sharedpref", "file", "root")

    private fun excludedDomains(path: String, section: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val parent = doc.getElementsByTagName(section).item(0) as Element
        val excludes = parent.getElementsByTagName("exclude")
        return (0 until excludes.length).map { (excludes.item(it) as Element).getAttribute("domain") }.toSet()
    }

    @Test fun `Android 12+ cloud backup excludes all app data`() =
        assertEquals(privateDomains, excludedDomains("src/main/res/xml/data_extraction_rules.xml", "cloud-backup"))

    @Test fun `Android 11 and older backup excludes all app data`() =
        assertEquals(privateDomains, excludedDomains("src/main/res/xml/backup_rules.xml", "full-backup-content"))
}
