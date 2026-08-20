package io.github.plaza.core.net

/**
 * A [HttpTransport] that answers from a script and keeps what it was asked.
 *
 * The common-code replacement for the terminal OkHttp `Interceptor` these tests used until step D3c:
 * it stands where the network would, so the request under assertion is the one the code above
 * actually produced. What it can say about that request is *more* than the interceptor could, not
 * less — a body arrives as [HttpBody.Multipart] with its parts still apart, rather than as the
 * encoded string an assertion had to go looking for `name="image"` inside.
 *
 * One class for every caller of [HttpTransport] rather than one per test family: the vote signature
 * and the six image hosts both need it, and a second copy would be a second set of answers to
 * "what did the transport see".
 *
 * @param answers consumed in order; the last repeats once the list runs out, so a test that sends
 *   one request writes one answer and a test whose subject retries does not have to say so twice.
 */
class RecordingTransport(
    private vararg val answers: HttpResponse,
) : HttpTransport {
    init {
        require(answers.isNotEmpty()) { "a transport with no answers can only hang" }
    }

    val requests: MutableList<HttpRequest> = mutableListOf()

    /** The only request, for the tests that send one. Fails rather than lying when there were more. */
    val request: HttpRequest?
        get() = when (requests.size) {
            0 -> null
            1 -> requests.single()
            else -> throw AssertionError("expected one request, got ${requests.size}")
        }

    val last: HttpRequest get() = requests.last()

    /** Whether an upload listener was handed down, which is the progress ring's half of the contract. */
    var listened: Boolean = false
        private set

    /** Set to make the next call report a failure the way a real transport reports one. */
    var failWith: SiteError? = null

    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
        requests += request
        listened = onUploadProgress != null
        // Two reports, which is the least a transport may make and still keep the promise
        // [UploadProgress] makes about finishing at 1f. How many steps a real one takes is that
        // platform's business — see `OkHttpTransportProgressTest`.
        onUploadProgress?.invoke(0f)
        failWith?.let { throw SiteException(it) }
        onUploadProgress?.invoke(1f)
        return answers.getOrElse(requests.size - 1) { answers.last() }
    }
}

/** The answer most tests want: 200, a body, and a URL nothing reads. */
fun httpResponse(
    body: String,
    code: Int = 200,
    url: String = "https://example.invalid/",
    headers: Map<String, String> = emptyMap(),
): HttpResponse = HttpResponse(code = code, url = url, headers = headers, body = body)

/** The multipart body the request carried, or a failure saying it did not carry one. */
val HttpRequest.multipart: HttpBody.Multipart
    get() = body as? HttpBody.Multipart ?: throw AssertionError("expected a multipart body, got $body")

val HttpRequest.path: String? get() = WebUrl.parse(url)?.path

val HttpRequest.host: String? get() = WebUrl.parse(url)?.host

/** One query field of the request's own URL, percent-decoded — see [WebUrl.queryParameter]. */
fun HttpRequest.queryParameter(name: String): String? = WebUrl.parse(url)?.queryParameter(name)
