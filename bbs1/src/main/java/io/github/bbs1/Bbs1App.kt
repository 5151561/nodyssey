package io.github.bbs1

import android.app.Application
import io.github.bbs1.di.AppContainer
import io.github.bbs1.di.DefaultAppContainer

class Bbs1App : Application() {
    /** The one place the dependency graph is built. Everything else receives it. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
