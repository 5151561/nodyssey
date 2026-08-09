package io.github.bbs1.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.bbs1.data.InstanceRepository

/** The one place the dependency graph is built. Everything else receives it. */
interface AppContainer {
    val instanceRepository: InstanceRepository
}

// A property delegate on Context so exactly one DataStore exists per file, per process — the
// factory throws on the second instance over the same file.
private val Context.instancesDataStore by preferencesDataStore(name = "instances")

class DefaultAppContainer(context: Context) : AppContainer {
    override val instanceRepository = InstanceRepository(context.instancesDataStore)
}
