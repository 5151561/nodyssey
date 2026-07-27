package io.github.nsreader.model

data class TermsDocument(
    val title: String,
    val effectiveDate: String?,
    val blocks: List<TermsBlock>,
)

sealed interface TermsBlock {
    data class Heading(val level: Int, val text: String) : TermsBlock

    data class Paragraph(val text: String) : TermsBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<String>,
    ) : TermsBlock
}
