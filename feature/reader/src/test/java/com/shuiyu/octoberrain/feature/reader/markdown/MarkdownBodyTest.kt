package com.shuiyu.octoberrain.feature.reader.markdown

import com.shuiyu.octoberrain.feature.reader.web.ResourcePaths
import com.shuiyu.octoberrain.feature.reader.web.WebMessagePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBodyTest {
    @Test fun chineseTablesCodeAndImagesRemainReadableWithoutDocumentScripts() {
        val markdown = """# 中文标题

> 引用

- 列表

```kotlin
val text = "中文"
```

| 名称 | 数量 |
| --- | --- |
| 中文 | 12 |

![local](images/p.png)
![remote](https://evil.invalid/p.png)

<script>alert(1)</script>
"""
        val html =
            MarkdownBody.render(markdown) { path ->
                ResourcePaths.relative(path)?.let {
                    WebMessagePolicy.ORIGIN + "/session/test/res/" + ResourcePaths.encode(it)
                }
            }
        assertTrue(html.contains("中文标题"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<pre>"))
        assertTrue(html.contains("/res/images/p.png"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("evil.invalid"))
    }

    @Test fun pathologicalNestingStopsBeforeRecursiveHtmlRendering() {
        try {
            MarkdownBody.render(">".repeat(500) + " nested") { null }
            throw AssertionError("Expected depth budget")
        } catch (_: IllegalArgumentException) {
            // 深层输入明确失败，不能压垮宿主栈。
        }
    }
}
