package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.FileItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemApkTest {
    @Test fun apkChoiceDoesNotDependOnDownloadDirectoryOrMimeGuess() {
        assertTrue(FileItem.file("APP.APK", "/Download/APP.APK", 1, 1, "application/zip").isApk)
        assertTrue(FileItem.file("app.apk", "/elsewhere/app.apk", 1, 1, "application/octet-stream").isApk)
        assertTrue(
            FileItem.file("download", "/Download/download", 1, 1, "Application/Vnd.Android.Package-Archive; x=y").isApk,
        )
        assertFalse(FileItem.dir("app.apk", "/Download/app.apk", 1).isApk)
    }

    @Test fun normalArchivesAndDocumentsDoNotOfferInstallation() {
        listOf("zip", "rar", "7z", "epub", "docx", "png").forEach { extension ->
            assertFalse(
                FileItem.file("sample.$extension", "/Download/sample.$extension", 1, 1, "application/zip").isApk,
            )
        }
    }
}
