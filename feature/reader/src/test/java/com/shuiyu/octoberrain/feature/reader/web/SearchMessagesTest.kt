package com.shuiyu.octoberrain.feature.reader.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchMessagesTest {
    private val message =
        """
        {"kind":"searchResults","session":"live","request":1,"offset":0,"total":1,
        "hits":[{"anchor":20,"snippet":"目标"}]}
        """.trimIndent()

    @Test fun acceptsBoundedCurrentSessionSearchCoordinates() {
        assertEquals(
            20,
            parse(message)
                ?.search
                ?.hits
                ?.single()
                ?.position
                ?.anchor,
        )
        assertNull(WebMessagePolicy.parse(message, "old", WebMessagePolicy.ORIGIN, true, false))
        assertNull(parse(message.replace("\"total\":1", "\"total\":2")))
        assertNull(parse(message.replace("\"anchor\":20", "\"anchor\":-1")))
        assertNull(parse(message.replace("\"anchor\":20", "\"anchor\":1.5")))
        assertNull(parse(message.replace("目标", "x".repeat(161))))
    }

    private fun parse(value: String) = WebMessagePolicy.parse(value, "live", WebMessagePolicy.ORIGIN, true, false)
}
