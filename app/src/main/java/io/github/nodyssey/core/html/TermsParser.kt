package io.github.nodyssey.core.html

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.model.TermsBlock
import io.github.nodyssey.model.TermsDocument
import org.jsoup.Jsoup

object TermsParser {
    private val effectiveDatePattern = Regex("(\\d{4}-\\d{2}-\\d{2})")

    fun parse(html: String): TermsDocument {
        val document = Jsoup.parse(html, NodeSeekSite.BASE_URL)
        val article = document.selectFirst("article")
            ?: error("Terms article not found")
        val elements = article.children().toList()
        val titleElement = elements.firstOrNull { it.tagName() in setOf("h1", "h2") }
            ?: error("Terms title not found")
        val title = titleElement.text().trim()
        val dateElement = elements.firstOrNull { element ->
            element.tagName() == "p" && effectiveDatePattern.containsMatchIn(element.text())
        }
        val effectiveDate = dateElement?.text()?.let(effectiveDatePattern::find)?.value
        val blocks = elements
            .dropWhile { it != titleElement }
            .drop(1)
            .filterNot { it == dateElement }
            .mapNotNull { element ->
                val text = element.text().trim()
                when {
                    text.isEmpty() -> null

                    element.tagName() == "h2" -> TermsBlock.Heading(level = 2, text = text)

                    element.tagName() == "h3" -> TermsBlock.Heading(level = 3, text = text)

                    element.tagName() == "p" -> TermsBlock.Paragraph(text)

                    element.tagName() in setOf("ul", "ol") -> TermsBlock.ListBlock(
                        ordered = element.tagName() == "ol",
                        items = element.children().map { it.text().trim() }.filter(String::isNotEmpty),
                    )

                    else -> TermsBlock.Paragraph(text)
                }
            }
        check(blocks.isNotEmpty()) { "Terms article is empty" }
        return TermsDocument(title = title, effectiveDate = effectiveDate, blocks = blocks)
    }
}
