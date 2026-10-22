package com.shuiyu.octoberrain.feature.reader.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OwnedDocumentPathTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun onlyTheOwnedProviderAndRootMapToLocalIdentity() {
        val file = temporary.newFile("sample.docx")
        val root = temporary.root
        assertEquals(
            file.canonicalFile,
            OwnedDocumentPath.resolve("app.files", listOf("files_share", file.name), "app.files", root),
        )
        assertNull(OwnedDocumentPath.resolve("other.files", listOf("files_share", file.name), "app.files", root))
        assertNull(OwnedDocumentPath.resolve("app.files", listOf("themes", file.name), "app.files", root))
        assertNull(OwnedDocumentPath.resolve("app.files", listOf("files_share", "..", file.name), "app.files", root))
        assertNull(OwnedDocumentPath.resolve("app.files", listOf("files_share", "../" + file.name), "app.files", root))
    }
}
