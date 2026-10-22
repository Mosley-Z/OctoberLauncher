package com.shuiyu.octoberrain.feature.reader.office.slides

import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import com.shuiyu.octoberrain.feature.reader.web.ImageResourceBudget
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Produces a single-slide package: unrelated slides/media never reach JSZip or the DOM. */
internal class SlidePackage(
    private val zip: ZipArchive,
    private val index: SlideIndex,
) {
    fun build(slide: Int): ByteArray {
        val selected = index.paths[slide]
        val included = dependencies(selected)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { sink ->
            included.forEach { path -> writeEntry(sink, path, included, selected) }
        }
        return output.toByteArray()
    }

    private fun dependencies(selected: String): Set<String> {
        val included = linkedSetOf("[Content_Types].xml", "ppt/presentation.xml")
        val queue = ArrayDeque<String>()
        listOf(selected, "docProps/app.xml", "ppt/tableStyles.xml").filter { exists(it) }.forEach { queue.add(it) }
        index
            .relationships("ppt/presentation.xml")
            .values
            .filter {
                it !in index.paths && permitted(it)
            }.forEach { queue.add(it) }
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (!included.add(path)) continue
            require(exists(path))
            enqueueRelations(path, selected, queue)
            val rel = SlideIndex.relPath(path)
            if (exists(rel)) included.add(rel)
        }
        included.add(SlideIndex.relPath("ppt/presentation.xml"))
        return included
    }

    private fun enqueueRelations(
        path: String,
        selected: String,
        queue: ArrayDeque<String>,
    ) {
        index
            .relationships(path)
            .values
            .filter {
                (it !in index.paths || it == selected) && permitted(it)
            }.forEach { queue.add(it) }
    }

    private fun writeEntry(
        sink: ZipOutputStream,
        path: String,
        included: Set<String>,
        selected: String,
    ) {
        sink.putNextEntry(ZipEntry(path))
        if (path.endsWith(".xml") || path.endsWith(".rels")) {
            val doc = index.xml(path)
            val links = index.relationships(path).filterValues { it in included }
            val content = SlideSanitizer.clean(doc, path, included, selected, links)
            sink.write(content.toByteArray(Charsets.UTF_8))
        } else {
            zip.open(path).use { it.copyTo(sink) }
        }
        sink.closeEntry()
    }

    private fun exists(path: String): Boolean = zip.items.any { it.path == path }

    companion object {
        private fun permitted(path: String): Boolean =
            (path.endsWith(".xml") || ImageResourceBudget.mime(path) != null) &&
                !path.startsWith("ppt/notes") &&
                !path.startsWith("ppt/embeddings/")
    }
}
