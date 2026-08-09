/*
 * Pins transitive dependency versions in each module's `gradle.lockfile`.
 *
 * The version catalog already pins what this project asks for directly; locking pins what those
 * dependencies in turn drag in. Without it, this commit built today and the same commit built in six
 * months can resolve different transitive versions, and a CI failure nobody can reproduce locally is
 * the usual way that gets discovered.
 *
 * After changing any dependency, regenerate with:
 *
 *     ./gradlew resolveAndLockAll --write-locks
 *
 * and commit every lockfile it touches. Running it unqualified from the root is what makes it cover
 * all modules: Gradle matches the task name in every project that registers it. Until the lockfiles
 * are regenerated the build fails rather than resolving something else.
 */
val lockedConfigurations =
    setOf(
        // What ships.
        "debugCompileClasspath",
        "debugRuntimeClasspath",
        "releaseCompileClasspath",
        "releaseRuntimeClasspath",
        // What the tests run against, since a test-only dependency drifting is just as confusing.
        "debugUnitTestCompileClasspath",
        "debugUnitTestRuntimeClasspath",
    )

dependencyLocking {
    // STRICT so that a dependency with *no* lock state fails too, not just one whose version moved.
    // The default mode happily resolves an unlocked module — checked by adding one and watching the
    // build pass — which would have made this gate far weaker than it looks.
    lockMode.set(LockMode.STRICT)
}

// Named configurations rather than `lockAllConfigurations()`: AGP registers internal configurations
// such as `androidApis` that are lockable but never produce lock state, and under STRICT every one of
// those fails the build. Library modules add more of them — `debugApiElements` and friends — so the
// named set matters more here than it did when only `:app` existed. These six are the ones whose
// contents actually determine what ships and what the tests execute.
configurations.configureEach {
    if (name in lockedConfigurations) {
        resolutionStrategy.activateDependencyLocking()
    }
}

/*
 * Resolves the locked configurations in one pass so `--write-locks` can write a complete lockfile.
 *
 * `--write-locks` only records configurations that actually got resolved during the build, so without
 * a task that touches all of them the lockfile would silently cover only part of the graph.
 * Deliberately guarded: it must never run except to write locks.
 */
tasks.register("resolveAndLockAll") {
    // Resolving configurations by hand needs the project at execution time, which the configuration
    // cache forbids. Opting out is safe precisely because this task is not part of any normal build —
    // it runs only when a human regenerates the lockfile.
    notCompatibleWithConfigurationCache("resolves configurations to regenerate gradle.lockfile")

    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "resolveAndLockAll exists only to regenerate locks: run it with --write-locks"
        }
    }
    doLast {
        configurations
            .filter { it.name in lockedConfigurations && it.isCanBeResolved }
            .forEach { configuration ->
                // The *graph* is what gets locked, so resolve only that. Asking for artifacts as well
                // (`resolve()`, or a file collection) makes AGP's own `debugApiElements` variants
                // ambiguous and fails before any lock state is written.
                configuration.incoming.resolutionResult.root
            }
    }
}
