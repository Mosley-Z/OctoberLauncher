package com.shuiyu.octoberrain.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class IconOverrideSwapTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun invalidImageDoesNotDestroyTheExistingOverride() {
        val old = oldIcon()
        var published = false
        val swap =
            swap(valid = false) {
                published = true
                true
            }
        assertFalse(swap.execute(old.name, old.path))
        assertEquals("old", old.readText())
        assertFalse(published)
        assertEquals(listOf(old.name), temporary.root.list()!!.toList())
    }

    @Test fun interruptedCopyClosesInputAndKeepsTheOldFile() {
        val old = oldIcon()
        var closed = false
        val source =
            object : InputStream() {
                private var reads = 0

                override fun read(): Int = if (reads++ < 5) 'x'.code else throw IOException("interrupted")

                override fun close() {
                    closed = true
                }
            }
        val swap = IconOverrideSwap(temporary.root, { source }, { true }, { error("must not publish") })
        assertFalse(swap.execute(old.name, old.path))
        assertTrue(closed)
        assertEquals("old", old.readText())
        assertEquals(listOf(old.name), temporary.root.list()!!.toList())
    }

    @Test fun rejectedConfigKeepsTheOldPathAndDeletesTheNewFile() {
        val old = oldIcon()
        val configured = old.path
        val swap =
            swap {
                assertEquals("old", File(configured).readText())
                false
            }
        assertFalse(swap.execute(old.name, configured))
        assertEquals("old", old.readText())
        assertEquals(listOf(old.name), temporary.root.list()!!.toList())
    }

    @Test fun throwingConfigAlsoPreservesTheOldFile() {
        val old = oldIcon()
        assertFalse(swap { throw IOException("config") }.execute(old.name, old.path))
        assertEquals("old", old.readText())
        assertEquals(listOf(old.name), temporary.root.list()!!.toList())
    }

    @Test fun successPublishesValidatedUniqueFileBeforeDeletingThePreviousPath() {
        val old = oldIcon()
        var configured = old.path
        val swap =
            swap { path ->
                assertEquals("old", old.readText())
                assertEquals("new", File(path).readText())
                assertNotEquals(old.path, path)
                configured = path
                true
            }
        assertTrue(swap.execute(old.name, old.path))
        assertFalse(old.exists())
        assertEquals("new", File(configured).readText())
        assertEquals(1, temporary.root.list()!!.size)
    }

    @Test fun anotherTargetIsUntouchedByAReplacement() {
        val old = oldIcon()
        val other = temporary.newFile(IconFileNames.fileName("special:files")).apply { writeText("other") }
        assertTrue(swap { true }.execute(old.name, old.path))
        assertEquals("other", other.readText())
    }

    private fun oldIcon(): File =
        temporary.newFile(IconFileNames.fileName("special:settings")).apply { writeText("old") }

    private fun swap(
        valid: Boolean = true,
        publish: (String) -> Boolean,
    ): IconOverrideSwap =
        IconOverrideSwap(
            temporary.root,
            { ByteArrayInputStream("new".toByteArray()) },
            { valid },
            publish,
        )
}
