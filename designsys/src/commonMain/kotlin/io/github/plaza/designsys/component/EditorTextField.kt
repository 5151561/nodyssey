package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.plaza.designsys.editor.InlinePictureLayer
import io.github.plaza.designsys.editor.InlinePictureOutput
import io.github.plaza.designsys.editor.InlinePictures

/**
 * The text field every editor in the app is built on.
 *
 * A `BasicTextField` rather than Material's filled one, for the reason the message bar found first: a
 * filled field reserves the room a floating label would need and stands 56dp tall before it holds
 * anything. What the four editors actually shared was smaller and more error-prone than that — a
 * placeholder drawn behind the field whenever the text is empty, which has to sit inside the decorator
 * and therefore got rewritten at every call site.
 *
 * [container] is what the four still differ in, and the only thing they should: the post body wants a
 * box that fills the screen, the reply sheet one that fills the width, the message bar a rounded pill,
 * the title a row with a counter and a rule under it. It receives the placeholder and the field
 * together and is responsible for stacking them — a `Box`, or something that ends in one.
 *
 * [inlinePictures] shows the runs it finds — stickers, in practice — as pictures in the text rather
 * than as the Markdown that stands for them; the text itself is untouched.
 */
@Composable
fun EditorTextField(
    state: TextFieldState,
    hint: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    hintStyle: TextStyle = textStyle,
    hintMaxLines: Int = Int.MAX_VALUE,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.MultiLine(),
    inputTransformation: InputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    inlinePictures: InlinePictures? = null,
    container: @Composable (content: @Composable () -> Unit) -> Unit = { content ->
        Box(Modifier.fillMaxWidth()) { content() }
    },
) {
    val output = remember(inlinePictures) { inlinePictures?.let { InlinePictureOutput(it.find) } }
    // Hoisted only so the pictures can follow the text when the field scrolls under them.
    val scrollState = rememberScrollState()
    var layout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    BasicTextField(
        state = state,
        lineLimits = lineLimits,
        inputTransformation = inputTransformation,
        outputTransformation = output,
        scrollState = scrollState,
        onTextLayout = if (inlinePictures == null) null else { result -> layout = result },
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorator = { inner ->
            container {
                if (state.text.isEmpty()) {
                    Text(
                        text = hint,
                        style = hintStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = hintMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (inlinePictures == null) {
                    inner()
                } else {
                    Box {
                        inner()
                        layout?.let { InlinePictureLayer(state, inlinePictures, scrollState, it) }
                    }
                }
            }
        },
    )
}
