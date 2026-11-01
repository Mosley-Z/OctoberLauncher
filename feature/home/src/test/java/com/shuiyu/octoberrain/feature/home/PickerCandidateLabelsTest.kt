package com.shuiyu.octoberrain.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PickerCandidateLabelsTest {
    @Test
    fun compactDetailKeepsCrossFolderSourceOnSameLine() {
        val candidate = FolderCandidate("app:p/A", "Alias", "p", true, "source", "Old")
        assertEquals("位于：Source · p", PickerCandidateLabels.detail(candidate, false, mapOf("source" to "Source"), true))
        assertEquals("p", PickerCandidateLabels.detail(candidate, false, emptyMap(), false))
        assertEquals("已加入 · p", PickerCandidateLabels.detail(candidate, true, emptyMap(), true))
        assertEquals(
            "已卸载 · p",
            PickerCandidateLabels.detail(candidate.copy(installed = false), false, emptyMap(), true),
        )
    }
}
