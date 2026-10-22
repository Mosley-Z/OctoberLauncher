package com.shuiyu.octoberrain.feature.reader.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeWebAssetsTest {
    private val resources =
        object : WebResources {
            override fun open(path: String): WebResource? = null

            override fun close() = Unit
        }

    @Test fun scriptsAreSelectedByEngineInsteadOfDocumentPaths() {
        val reflow = WebDocument("live", "", 20, resources)
        val sheets = reflow.copy(office = OfficeWebMode.SHEETS)
        val slides = reflow.copy(office = OfficeWebMode.SLIDES)
        assertTrue(OfficeWebAssets.allows(reflow, "host.js"))
        assertFalse(OfficeWebAssets.allows(reflow, "sheets/xlsx.full.min.js"))
        assertTrue(OfficeWebAssets.allows(sheets, "sheets/xlsx.full.min.js"))
        assertFalse(OfficeWebAssets.allows(sheets, "slides/pptxjs.js"))
        assertTrue(OfficeWebAssets.allows(slides, "slides/pptxjs.css"))
        assertFalse(OfficeWebAssets.allows(slides, "slides/../host.js"))
        assertFalse(OfficeWebAssets.allows(slides, "https://evil.invalid/code.js"))
        assertTrue(
            OfficeWebAssets.markup(slides).indexOf("jszip2.min.js") <
                OfficeWebAssets.markup(slides).indexOf("pptxjs.js"),
        )
    }
}
