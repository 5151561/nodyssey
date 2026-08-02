package io.github.nodyssey.ui.composer

/**
 * The keys on the strip, and the keys the wrench panel offers to add.
 *
 * Two lists rather than one list plus a predicate, because that is what the panel draws: the enabled
 * half in the order it will appear, the rest as a pool underneath.
 */
data class ToolbarLayout(
    val enabled: List<EditorAction>,
    val available: List<EditorAction>,
)

/**
 * Reads a stored arrangement back into keys.
 *
 * Deliberately *not* the home strip's ranking model, despite the family resemblance. That one
 * reconciles a saved order against a board list the server can change under it, so it has to carry
 * ranks for boards that no longer exist and adopt boards it has never seen. Nothing here comes from a
 * server: [EditorAction] is a closed set this app ships, so the stored value is simply the enabled
 * keys in order and everything else is available.
 *
 * The two rules that remain are the ones any stored enum name needs:
 *
 * - A name that no longer parses is dropped. An action removed in a later version leaves behind
 *   arrangements that mention it, and one of those must not empty somebody's toolbar.
 * - An action the arrangement has never heard of lands in [ToolbarLayout.available], not on the
 *   strip. A key added in a later version is an offer, not a change to a toolbar someone laid out.
 *
 * An empty [stored] means "never customised", so it yields [defaults] — which is safe only because
 * the panel refuses to remove the last key. If emptying the strip were ever allowed, this would read
 * it back as untouched and hand the defaults straight back.
 */
fun toolbarLayout(
    stored: List<String>,
    defaults: List<EditorAction>,
): ToolbarLayout {
    val byName = EditorAction.entries.associateBy(EditorAction::name)
    val enabled = stored.mapNotNull(byName::get).distinct().ifEmpty { defaults }
    return ToolbarLayout(
        enabled = enabled,
        // Enum order, not the order they were removed in: the pool is a catalogue, and a catalogue
        // that reshuffles itself as you take things out of it is one you have to re-read every time.
        available = EditorAction.entries.filterNot { it in enabled },
    )
}

/** The strip always keeps one key. See [toolbarLayout] for what an empty arrangement would mean. */
const val MIN_TOOLBAR_KEYS = 1
