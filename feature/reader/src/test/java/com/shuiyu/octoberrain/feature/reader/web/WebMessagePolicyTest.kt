package com.shuiyu.octoberrain.feature.reader.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebMessagePolicyTest {
    @Test fun scrollLocationRequiresBoundedFraction() {
        val location = """{"session":"live","kind":"locate","anchor":32,"fraction":0.75}"""
        assertEquals(
            0.75,
            WebMessagePolicy.parse(location, "live", WebMessagePolicy.ORIGIN, true, false)?.position?.fraction,
        )
        assertNull(
            WebMessagePolicy.parse(location.replace("0.75", "1.1"), "live", WebMessagePolicy.ORIGIN, true, false),
        )
    }

    private val message =
        """
        {"session":"live","kind":"ready","request":2,"anchor":1024,"fraction":0.5,"page":1,"pages":3}
        """.trimIndent()

    @Test fun acceptsOnlyCurrentMainFrameAtExactOrigin() {
        assertEquals(
            1024,
            WebMessagePolicy.parse(message, "live", WebMessagePolicy.ORIGIN, true, false)?.position?.anchor,
        )
        assertNull(WebMessagePolicy.parse(message, "old", WebMessagePolicy.ORIGIN, true, false))
        assertNull(WebMessagePolicy.parse(message, "live", "https://evil.invalid", true, false))
        assertNull(WebMessagePolicy.parse(message, "live", WebMessagePolicy.ORIGIN, false, false))
        assertNull(WebMessagePolicy.parse(message, "live", WebMessagePolicy.ORIGIN, true, true))
    }

    @Test fun malformedOversizedAndOutOfRangeMessagesCannotControlHost() {
        for (value in listOf(
            "{",
            "x".repeat(8193),
            message.replace("0.5", "2.0"),
            message.replace("\"page\":1", "\"page\":99"),
            message.replace("\"ready\"", "\"readFile\""),
        )) {
            assertNull(WebMessagePolicy.parse(value, "live", WebMessagePolicy.ORIGIN, true, false))
        }
        assertNotNull(
            WebMessagePolicy.parse(
                """{"session":"live","kind":"turn","delta":-1}""",
                "live",
                WebMessagePolicy.ORIGIN,
                true,
                false,
            ),
        )
        assertNull(
            WebMessagePolicy.parse(
                """{"session":"live","kind":"turn","delta":900}""",
                "live",
                WebMessagePolicy.ORIGIN,
                true,
                false,
            ),
        )
    }
}
