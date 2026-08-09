package io.github.bbs1.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * A repository over two real preference stores under [dir].
 *
 * Real stores rather than fakes because the preferences core runs on the plain JVM and the thing
 * worth testing is the join between the two files — the site list and the credentials that are kept
 * out of backups. [scope] is the store's own: cancel it to release the files.
 */
fun newTestInstanceRepository(
    scope: CoroutineScope,
    dir: File,
    prefix: String = "",
    newId: () -> String = { "id" },
): InstanceRepository =
    InstanceRepository(
        dataStore =
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "${prefix}instances.preferences_pb") },
        sessionStore =
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "${prefix}sessions.preferences_pb") },
        newId = newId,
    )
