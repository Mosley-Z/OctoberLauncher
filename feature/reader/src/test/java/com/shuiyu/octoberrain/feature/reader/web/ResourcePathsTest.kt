package com.shuiyu.octoberrain.feature.reader.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourcePathsTest {
    @Test fun acceptsLocalNamesAndNormalizesWithinDocumentRoot() {
        assertEquals("图片/a b+.png", ResourcePaths.relative("图片/a%20b+.png"))
        assertEquals("ok.png", ResourcePaths.relative("images/../ok.png"))
    }

    @Test fun rejectsEncodedTraversalAbsolutePathsAndRemoteResources() {
        for (path in listOf(
            "../private.png",
            "%2e%2e/private.png",
            "a/../../private.png",
            "%5c..%5cprivate.png",
            "file:///data/private.png",
            "https://evil.invalid/a.png",
            "//evil.invalid/a.png",
            "C:/private.png",
            "a%00.png",
        )) {
            assertNull(path, ResourcePaths.relative(path))
        }
    }
}
