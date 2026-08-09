package io.github.bbs1.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.net.Bbs1Api
import io.github.bbs1.net.Bbs1ApiClient
import io.github.plaza.core.AppDispatchers
import okhttp3.OkHttpClient

/** The one place the dependency graph is built. Everything else receives it. */
interface AppContainer {
    val instanceRepository: InstanceRepository
    val api: Bbs1Api
}

// A property delegate on Context so exactly one DataStore exists per file, per process — the
// factory throws on the second instance over the same file.
private val Context.instancesDataStore by preferencesDataStore(name = "instances")

class DefaultAppContainer(context: Context) : AppContainer {
    override val instanceRepository = InstanceRepository(context.instancesDataStore)

    // One client for every site: OkHttp pools connections per host on its own, and the sites share
    // nothing else (no cookies, no auth yet). The defaults — 10s connect/read — fit self-hosted
    // forums; tune here, once, if they ever don't.
    override val api: Bbs1Api = Bbs1ApiClient(OkHttpClient(), AppDispatchers())
}
