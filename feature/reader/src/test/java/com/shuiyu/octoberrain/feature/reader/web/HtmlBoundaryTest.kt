package com.shuiyu.octoberrain.feature.reader.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlBoundaryTest {
    @Test fun untrustedHtmlCannotIntroduceScriptEventsFramesOrRemoteCss() {
        val html =
            HtmlBoundary.clean(
                """<script>alert(1)</script><style>@import 'https://evil.invalid';</style>
            <iframe src="https://evil.invalid"></iframe><img src="a.png" onerror="alert(1)">
            <a href="javascript:alert(1)">正文</a><h1 id="reader-content">标题</h1>""",
            ) {
                WebMessagePolicy.ORIGIN + "/session/test/res/a.png"
            }
        for (unsafe in listOf("<script", "<style", "<iframe", "onerror", "javascript:", "evil.invalid")) {
            assertFalse(unsafe, html.contains(unsafe))
        }
        assertTrue(html.contains("doc-reader-content"))
        assertTrue(html.contains("标题"))
    }
}
