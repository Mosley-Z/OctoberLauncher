package com.shuiyu.octoberrain.feature.home

internal object PickerCandidateLabels {
    fun detail(
        candidate: FolderCandidate,
        existing: Boolean,
        folderNames: Map<String, String>,
        compact: Boolean,
    ): String {
        val status =
            when {
                existing -> "已加入"
                !candidate.installed -> "已卸载"
                candidate.parentFolderId != null ->
                    "位于：${folderNames[candidate.parentFolderId] ?: candidate.parentFolderName.orEmpty()}"
                else -> null
            }
        return if (compact && status != null) "$status · ${candidate.packageName}" else candidate.packageName
    }
}
