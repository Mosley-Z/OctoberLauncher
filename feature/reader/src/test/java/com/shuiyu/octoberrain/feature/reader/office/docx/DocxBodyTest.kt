package com.shuiyu.octoberrain.feature.reader.office.docx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxBodyTest {
    @Test
    fun bodyKeepsTablesAndTextButIgnoresFieldsAndExternalImages() {
        val xml =
            "<w:document><w:body><w:p><w:r><w:t>中文&amp;内容</w:t>" +
                "<w:instrText>INCLUDETEXT remote</w:instrText></w:r></w:p>" +
                "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>单元格</w:t></w:r></w:p></w:tc></w:tr></w:tbl>" +
                "<w:p><a:blip r:embed=\"r1\"/></w:p></w:body></w:document>"
        val rels =
            "<Relationships><Relationship Id=\"r1\" Target=\"https://example.invalid/a.png\" " +
                "TargetMode=\"External\"/></Relationships>"
        val html = DocxBody.render(xml, rels) { error("External image must not resolve") }
        assertTrue(html.contains("中文&amp;内容"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("单元格"))
        assertTrue(html.contains("图片未显示"))
        assertFalse(html.contains("INCLUDETEXT"))
        assertFalse(html.contains("https://"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun entityDefinitionsAreRejectedBeforeParsing() {
        DocxBody.render("<!DOCTYPE document [<!ENTITY x SYSTEM 'file:///a'>]><document/>", "<Relationships/>") { null }
    }

    @Test
    fun internalImagesUseOnlyMappedResourcesAndPreserveTextOrder() {
        val xml =
            "<w:document><w:body><w:p><w:r><w:t>前</w:t></w:r><a:blip r:embed=\"img\"/>" +
                "<w:r><w:t>后</w:t></w:r></w:p></w:body></w:document>"
        val rels = "<Relationships><Relationship Id=\"img\" Target=\"media/image.png\"/></Relationships>"
        val html = DocxBody.render(xml, rels) { if (it == "media/image.png") "/session/res/image.png" else null }
        assertTrue(html.indexOf("前") < html.indexOf("<img"))
        assertTrue(html.indexOf("<img") < html.indexOf("后"))
        assertTrue(html.contains("/session/res/image.png"))
        assertFalse(html.contains("\n"))
    }
}
