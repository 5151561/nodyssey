package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.app_name
import org.jetbrains.compose.resources.stringResource

/**
 * What this build calls itself.
 *
 * A debug build's launcher icon says "Nodyssey·D", because `app/src/debug/res` overrides `app_name`
 * for that build type — and until step D1 the screens read that same overridden resource and agreed
 * with it. They read a Compose Resource now, and those have no build types, so the name has to
 * arrive from the side that knows which build this is: `NodysseyRoot` provides it from
 * `AppVersion.label`, which Android fills in from `applicationInfo.loadLabel`.
 *
 * Blank means nobody provided one — a `@Preview`, or the gallery — and then [appName] falls back to
 * the resource, which is the released name.
 */
val LocalAppName = staticCompositionLocalOf { "" }

/**
 * The app's own name, for the four places that print it.
 *
 * Reading [LocalAppName] directly would be reading a blank on a preview; this is the accessor that
 * has the fallback in it.
 */
@Composable
fun appName(): String = LocalAppName.current.ifBlank { stringResource(Res.string.app_name) }
