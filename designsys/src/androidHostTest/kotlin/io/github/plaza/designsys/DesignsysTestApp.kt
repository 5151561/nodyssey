package io.github.plaza.designsys

import android.app.Application
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * An [Application] that exists to set one thing, installed for every test by `robolectric.properties`.
 *
 * The thing is the locale. This module's 44 strings ship in three languages, and the tests in here
 * assert the Simplified Chinese ones — `加粗`, `工具栏上的按键`, the image-failure sentences. Compose
 * Resources picks a bundle by `androidx.compose.ui.text.intl.Locale.current`, which on Android is the
 * *process* default locale rather than anything in the test's `Configuration`; Robolectric sets the
 * latter and leaves the former to the JVM. So on a machine defaulting to `en-US` — every CI runner —
 * these tests would read the English bundle and fail without a line of this module having changed.
 *
 * `attachBaseContext` rather than `onCreate`: it is the earliest hook Robolectric calls on the
 * application, and nothing between the two reads a string.
 */
class DesignsysTestApp : Application() {
    override fun attachBaseContext(base: Context?) {
        LocaleList.setDefault(LocaleList(Locale.SIMPLIFIED_CHINESE))
        super.attachBaseContext(base)
    }
}
