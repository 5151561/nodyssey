package io.github.nodyssey.data.local

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Runs [block] inside one write transaction.
 *
 * This is `androidx.room.withTransaction` written out. That function ships in `room-ktx`, which is
 * the Android-only half of Room and did not come along in step A6; the multiplatform runtime offers
 * the two calls below instead, and one place that composes them is better than the same two lines at
 * every call site.
 *
 * `immediateTransaction` — `BEGIN IMMEDIATE` — rather than the deferred one, because every caller
 * here writes: taking the write lock at the start is what keeps two feed refreshes from discovering
 * each other halfway through and one of them being rolled back.
 *
 * DAO calls inside [block] join this transaction rather than opening one of their own: Room carries
 * the connection in the coroutine context, which is also why [block] must not move itself to another
 * dispatcher.
 */
suspend fun <R> RoomDatabase.writeTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor ->
        transactor.immediateTransaction { block() }
    }
