package io.github.plaza.designsys.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import io.github.plaza.designsys.theme.ControlShape
import io.github.plaza.designsys.theme.LocalPlazaLayers

/**
 * The one styling every form field takes: Material's `OutlinedTextField` / `OutlinedSecureTextField`
 * with this [shape] and these [colors]. Filled rather than hollow, because an unfilled outlined field
 * reads as a hole in whatever it sits on rather than as a place to type.
 */
object PlazaFieldDefaults {
    val shape: Shape = ControlShape

    /**
     * On the page ([inCard] false) a field wears the card's white; inside a card it is a well in the
     * inset tone with a lighter outline, so it does not read as a second card.
     */
    @Composable
    fun colors(inCard: Boolean = false): TextFieldColors {
        val fill = if (inCard) LocalPlazaLayers.current.inset else LocalPlazaLayers.current.card
        return OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill,
            errorContainerColor = fill,
            unfocusedBorderColor =
            if (inCard) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline,
        )
    }
}
