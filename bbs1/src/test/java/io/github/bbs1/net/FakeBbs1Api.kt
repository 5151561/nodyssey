package io.github.bbs1.net

/**
 * The API as a set of swappable lambdas: a test states each call's outcome, including throwing the
 * [Bbs1ApiException] flavour under test. Defaults answer empty success so a test only names what it
 * is about.
 */
class FakeBbs1Api : Bbs1Api {
    var metaResult: (String) -> ApiMeta = { ApiMeta(ApiSite(name = "FORUM")) }
    var forumsResult: (String) -> List<ApiForum> = { emptyList() }
    var topicsResult: (String, Long?, Int) -> ApiTopicsPage = { _, _, _ -> ApiTopicsPage() }
    var topicResult: (String, Long, Int) -> ApiTopicPage = { _, id, _ ->
        ApiTopicPage(topic = ApiTopicDetail(id = id))
    }

    /** One entry per call, e.g. `"topics(null, p=2)"` — order assertions read it directly. */
    val calls = mutableListOf<String>()

    override suspend fun meta(baseUrl: String): ApiMeta {
        calls += "meta"
        return metaResult(baseUrl)
    }

    override suspend fun forums(baseUrl: String): List<ApiForum> {
        calls += "forums"
        return forumsResult(baseUrl)
    }

    override suspend fun topics(baseUrl: String, forumId: Long?, page: Int): ApiTopicsPage {
        calls += "topics($forumId, p=$page)"
        return topicsResult(baseUrl, forumId, page)
    }

    override suspend fun topic(baseUrl: String, id: Long, page: Int): ApiTopicPage {
        calls += "topic($id, p=$page)"
        return topicResult(baseUrl, id, page)
    }
}
