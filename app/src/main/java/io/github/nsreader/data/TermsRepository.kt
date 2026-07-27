package io.github.nsreader.data

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.html.TermsParser
import io.github.nsreader.core.net.HtmlSource
import io.github.nsreader.model.TermsDocument

fun interface TermsRepository {
    suspend fun terms(): TermsDocument
}

class NetworkTermsRepository(
    private val htmlSource: HtmlSource,
) : TermsRepository {
    override suspend fun terms(): TermsDocument =
        TermsParser.parse(htmlSource.getHtml(NodeSeekSite.TERMS_OF_SERVICE_PATH))
}
