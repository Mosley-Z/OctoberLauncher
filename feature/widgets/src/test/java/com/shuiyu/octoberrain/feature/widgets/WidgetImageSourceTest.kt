package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WidgetImageSourceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun deletionRequiresReadableParent() {
        val original = folder.newFile("image.jpg")
        assertFalse(WidgetImageSource.missingFile(original))
        assertTrue(original.delete())
        assertTrue(WidgetImageSource.missingFile(original))
        assertFalse(WidgetImageSource.missingFile(File(folder.root, "absent/image.jpg")))
        assertFalse(WidgetImageSource.missingFile(folder.newFolder("directory")))
    }
}
