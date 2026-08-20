package io.github.nodyssey

import android.app.Application
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import org.robolectric.TestLifecycleApplication
import java.lang.reflect.Method

/**
 * A bare [Application] plus one teardown chore, installed for every Robolectric test by
 * `robolectric.properties`.
 *
 * Nothing else: a screen in this module takes its dependencies as parameters, so there is no graph
 * to build here. `:app` has its own copy of this class over `NodysseyApp`, which does have one.
 *
 * The chore is draining the main looper, and it exists because two Compose singletons outlive a
 * Robolectric test while the looper under them does not. `AndroidUiDispatcher.Main` is built once
 * per JVM, and `GlobalSnapshotManager` runs its snapshot-draining coroutine on it, also once per
 * JVM. A test that ends with that coroutine resumed but not yet run leaves the dispatcher holding
 * `scheduledTrampolineDispatch = true` and a callback posted to the main looper; Robolectric then
 * discards that queue, so the callback never runs and the flag never clears. From then on every
 * `dispatch` on it only appends to a queue nobody drains, for the rest of the suite. Idling here
 * runs the stranded callback while its looper is still alive, which is the whole fix.
 *
 * Nothing notices a dead dispatcher, because the Compose test rule drives its own clock and never
 * needs that one — except Paging. `LazyPagingItems` hands `AndroidUiDispatcher.Main` to its
 * `PagingDataPresenter` as the context it presents on, so once it is wedged a list receives neither
 * its items nor its load states, and every assertion about either fails. That is `PostListScreenTest`
 * and `LedgerScreensTest` failing wholesale because of a class that ran before them — the class that
 * wedges it is never the one that fails, which is why this is fixed here and not in a test.
 *
 * It also has to be this late. An `@After` runs before the Compose rule disposes the composition,
 * and disposal is when the last dispatch is posted.
 */
class RobolectricApp :
    Application(),
    TestLifecycleApplication {
    override fun beforeTest(method: Method?) = Unit

    override fun prepareTest(test: Any?) = Unit

    override fun afterTest(method: Method?) {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
