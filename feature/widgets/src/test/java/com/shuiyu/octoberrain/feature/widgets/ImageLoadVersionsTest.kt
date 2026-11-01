package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLoadVersionsTest {
    @Test
    fun sourceChangeRejectsPreviousSource() {
        val versions = ImageLoadVersions()
        val sourceA = versions.capture()
        versions.sourceChanged()
        assertFalse(versions.canPublish(sourceA))
        assertTrue(versions.canPublish(versions.capture()))
    }

    @Test
    fun twoSelectionChangesCannotRestoreOldToken() {
        val versions = ImageLoadVersions()
        val token = versions.capture()
        versions.selectionChanged()
        versions.selectionChanged()
        assertFalse(versions.canPublish(token))
    }

    @Test
    fun dimensionsChangeRejectsToken() {
        val versions = ImageLoadVersions()
        val token = versions.capture()
        versions.dimensionsChanged()
        assertFalse(versions.canPublish(token))
    }

    @Test
    fun bothVisibilityTransitionsInvalidate() {
        val versions = ImageLoadVersions()
        val visibleToken = versions.capture()
        versions.setVisible(false)
        assertFalse(versions.canPublish(visibleToken))
        val hiddenToken = versions.capture()
        versions.setVisible(true)
        assertFalse(versions.canPublish(hiddenToken))
        assertFalse(versions.canPublish(visibleToken))
        assertTrue(versions.mayRunResources())
    }

    @Test
    fun releaseIsTerminal() {
        val versions = ImageLoadVersions()
        val token = versions.capture()
        versions.release()
        versions.setVisible(false)
        versions.setVisible(true)
        versions.sourceChanged()
        versions.selectionChanged()
        versions.dimensionsChanged()
        versions.release()
        assertFalse(versions.canPublish(token))
        assertFalse(versions.canPublish(versions.capture()))
        assertFalse(versions.mayRunResources())
    }

    @Test
    fun invisibleStaticPreviewCanPublishWithoutResources() {
        val versions = ImageLoadVersions()
        versions.setVisible(false)
        val token = versions.capture()
        versions.setVisible(false)
        assertTrue(versions.canPublish(token))
        assertFalse(versions.mayRunResources())
    }

    @Test
    fun repeatedTruePreservesTokenAndInitialResources() {
        val versions = ImageLoadVersions()
        val token = versions.capture()
        versions.setVisible(true)
        versions.setVisible(true)
        assertTrue(versions.canPublish(token))
        assertTrue(versions.mayRunResources())
    }

    @Test
    fun tokenBelongsToItsGate() {
        val first = ImageLoadVersions()
        val second = ImageLoadVersions()
        assertFalse(second.canPublish(first.capture()))
    }

    @Test
    fun onlyLatestSelectionPublishes() {
        val versions = ImageLoadVersions()
        val old = versions.capture()
        versions.selectionChanged()
        val latest = versions.capture()
        var publishedIndex = 0
        if (versions.canPublish(latest)) publishedIndex = 1
        if (versions.canPublish(old)) publishedIndex = 0
        org.junit.Assert.assertEquals(1, publishedIndex)
    }
}
