package com.shuiyu.octoberrain.feature.reader.epub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubStylesTest {
    @Test fun keepsReadingPropertiesWithoutNetworkPositioningOrUnboundedSelectors() {
        val css =
            EpubStyles.filter(
                """@import 'https://remote.invalid/a.css';
            p.lead {text-align:center;font-weight:bold;color:red;background:url(https://remote.invalid/p.png)}
            #title {font-style:italic;position:fixed;line-height:1.5}
            a[href] {font-weight:bold} p:hover {font-weight:bold}
            .bad {text-align:expression(alert(1));text-indent:9999em}
            """,
            )
        // A rule combined with @import is rejected entirely; subsequent ordinary rules are scoped.
        assertTrue(css.contains("#reader-content #doc-title{font-style:italic;line-height:1.5}"))
        for (unsafe in listOf("url", "remote", "position", "expression", "9999", ":hover", "[href]", "color")) {
            assertFalse(unsafe, css.contains(unsafe))
        }
        assertTrue(EpubStyles.filter("p.lead{text-align:center;font-weight:bold}").contains("text-align:center"))
    }
}
