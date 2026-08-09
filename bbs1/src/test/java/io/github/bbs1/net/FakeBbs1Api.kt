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
    var loginResult: (String, String, String) -> ApiAuth = { _, username, _ ->
        ApiAuth(token = "token-$username", tokenExpiresAt = 0, user = ApiUser(id = 1, username = username))
    }
    var createTopicResult: (Long, String, String) -> ApiTopicCreated = { _, _, _ -> ApiTopicCreated(topicId = 1) }
    var createReplyResult: (Long, String) -> ApiReplyCreated = { topicId, body ->
        ApiReplyCreated(replyId = 1, topicId = topicId, reply = ApiReply(id = 1, body = body))
    }

    /** One entry per call, e.g. `"topics(null, p=2)"` — order assertions read it directly. */
    val calls = mutableListOf<String>()

    /** The token each call arrived with, in call order — how a test proves the credential is sent. */
    val tokens = mutableListOf<String?>()

    override suspend fun meta(baseUrl: String, token: String?): ApiMeta {
        record("meta", token)
        return metaResult(baseUrl)
    }

    override suspend fun forums(baseUrl: String, token: String?): List<ApiForum> {
        record("forums", token)
        return forumsResult(baseUrl)
    }

    override suspend fun topics(baseUrl: String, forumId: Long?, page: Int, token: String?): ApiTopicsPage {
        record("topics($forumId, p=$page)", token)
        return topicsResult(baseUrl, forumId, page)
    }

    override suspend fun topic(baseUrl: String, id: Long, page: Int, token: String?): ApiTopicPage {
        record("topic($id, p=$page)", token)
        return topicResult(baseUrl, id, page)
    }

    override suspend fun login(baseUrl: String, username: String, password: String): ApiAuth {
        record("login($username)", null)
        return loginResult(baseUrl, username, password)
    }

    override suspend fun createTopic(
        baseUrl: String,
        token: String,
        forumId: Long,
        title: String,
        body: String,
    ): ApiTopicCreated {
        record("createTopic($forumId, $title)", token)
        return createTopicResult(forumId, title, body)
    }

    override suspend fun createReply(
        baseUrl: String,
        token: String,
        topicId: Long,
        body: String,
    ): ApiReplyCreated {
        record("createReply($topicId)", token)
        return createReplyResult(topicId, body)
    }

    private fun record(call: String, token: String?) {
        calls += call
        tokens += token
    }
}
