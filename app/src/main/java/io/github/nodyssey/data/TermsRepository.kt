package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.TermsParser
import io.github.nodyssey.core.net.HtmlSource
import io.github.nodyssey.model.TermsDocument

fun interface TermsRepository {
    suspend fun terms(): TermsDocument
}

class NetworkTermsRepository(
    private val htmlSource: HtmlSource,
) : TermsRepository {
    override suspend fun terms(): TermsDocument =
        TermsParser.parse(htmlSource.getHtml(NodeSeekSite.TERMS_OF_SERVICE_PATH))
}
