package io.github.nsreader.di

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekClient
import io.github.nsreader.core.net.NodeSeekJsonClient
import io.github.nsreader.core.net.WebViewCookieJar
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.NetworkPostRepository
import io.github.nsreader.data.PostRepository
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * A hand-rolled container. The graph is small enough that a DI framework would cost more than it
 * saves; if it grows past a screen, swap this for Hilt rather than letting it sprawl.
 */
object ServiceLocator {

    val cookieJar: WebViewCookieJar by lazy { WebViewCookieJar() }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Applied to page *and* image requests, which both have to look like the mobile site.
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", NodeSeekSite.USER_AGENT)
                }
                if (request.header("Referer") == null) {
                    builder.header("Referer", "${NodeSeekSite.BASE_URL}/")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    val nodeSeekClient: NodeSeekClient by lazy { NodeSeekClient(okHttpClient) }

    val jsonClient: NodeSeekJsonClient by lazy { NodeSeekJsonClient(okHttpClient) }

    val postRepository: PostRepository by lazy { NetworkPostRepository(nodeSeekClient) }

    val categoryRepository: CategoryRepository by lazy { CategoryRepository(jsonClient) }
}
