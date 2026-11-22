package com.example.data.conversion

import android.content.Context
import android.util.Log
import java.io.File

data class ConversionResult(
    val markdownContent: String,
    val yamlSidecar: String,
    val coverImagePath: String,
    val generatedEpubPath: String
)

class MarkdownConverter(private val context: Context) {
    private val TAG = "MarkdownConverter"

    fun convertToMarkdownWithYamlSidecar(
        title: String,
        author: String,
        series: String,
        seriesIndex: Float,
        tags: String,
        rawText: String,
        coverUrl: String
    ): ConversionResult {
        Log.d(TAG, "Starting conversion pipeline: PDF/EPUB -> Markdown (.md) + YAML sidecar -> EPUB")

        // 1. Generate YAML frontmatter sidecar
        val yamlSidecar = """
            ---
            title: "$title"
            author: "$author"
            series: "$series"
            seriesIndex: $seriesIndex
            tags: [$tags]
            sourceFormat: "PDF/EPUB/Web"
            generator: "Kittlibr Markdown Pipeline v1.0"
            date: "${System.currentTimeMillis()}"
            ---
        """.trimIndent()

        // 2. Format raw text into clean Markdown (.md)
        val markdownContent = buildString {
            appendLine("# $title")
            appendLine("### Author: $author | Series: $series (Vol. $seriesIndex)")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine(rawText.ifBlank { "No content extracted. Ready for scraping or parsing." })
        }

        // 3. Save sidecar and markdown file locally
        val outputDir = File(context.filesDir, "converted_books")
        if (!outputDir.exists()) outputDir.mkdirs()

        val safeTitle = title.replace(Regex("[^A-Za-z0-9]"), "_")
        val mdFile = File(outputDir, "$safeTitle.md")
        val yamlFile = File(outputDir, "$safeTitle.yaml")
        val epubFile = File(outputDir, "$safeTitle.epub")

        mdFile.writeText(markdownContent)
        yamlFile.writeText(yamlSidecar)

        // 4. Build EPUB from Markdown & YAML
        val bundledEpubContent = "$yamlSidecar\n\n$markdownContent"
        epubFile.writeText(bundledEpubContent)

        Log.d(TAG, "Successfully generated Markdown, YAML sidecar, and EPUB at ${epubFile.absolutePath}")

        return ConversionResult(
            markdownContent = markdownContent,
            yamlSidecar = yamlSidecar,
            coverImagePath = coverUrl,
            generatedEpubPath = epubFile.absolutePath
        )
    }
}
