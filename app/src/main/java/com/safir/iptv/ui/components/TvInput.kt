package com.safir.iptv.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.ui.theme.Brand

enum class TvFieldKind { TEXT, URL, PASSWORD }

/**
 * Text input for TV.
 *
 * Deliberately built on a plain [EditText] instead of Compose's TextField: the
 * Leanback IME edits through `ExtractedText` (the keyboard owns the whole screen
 * and mirrors the text back), which Compose text fields do not implement. On a TV
 * box that looks like the keyboard works while every keystroke is discarded — the
 * field stays empty and nothing downstream ever sees a value.
 *
 * Interaction follows the set-top-box convention: move onto the field with the
 * D-pad, press OK to open the keyboard, type, press Back to close it.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    kind: TvFieldKind = TvFieldKind.TEXT,
    /** Takes the initial focus. Done in View-world; Compose cannot focus into an interop view. */
    autoFocus: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brand.Surface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Brand.Accent else Brand.Border,
                shape = shape
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) Brand.Accent else Brand.TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                buildEditText(context, kind, placeholder) { text ->
                    currentOnValueChange(text)
                }.apply {
                    setOnFocusChangeListener { _, hasFocus -> focused = hasFocus }
                    // OK on the remote is a click on a focused view — that is where
                    // the keyboard belongs, not on focus, or it pops up while the
                    // user is only passing through on the way down the form.
                    setOnClickListener { view -> showKeyboard(view) }
                    setOnEditorActionListener { view, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_NEXT ||
                            actionId == EditorInfo.IME_ACTION_DONE
                        ) {
                            hideKeyboard(view)
                            view.focusSearch(View.FOCUS_DOWN)?.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    if (autoFocus) post { requestFocus() }
                }
            },
            update = { editText ->
                // Guarded so the TextWatcher's own emission cannot loop back in.
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    editText.setSelection(value.length)
                }
            }
        )
    }
}

private fun buildEditText(
    context: Context,
    kind: TvFieldKind,
    placeholder: String,
    onChange: (String) -> Unit
): EditText = EditText(context).apply {
    background = null
    setPadding(0, 0, 0, 0)
    setTextColor(AndroidColor.parseColor("#F2F4F8"))
    setHintTextColor(AndroidColor.parseColor("#5C6478"))
    textSize = 16f
    setSingleLine(true)
    gravity = Gravity.CENTER_VERTICAL
    hint = placeholder
    isFocusable = true
    isFocusableInTouchMode = true

    inputType = when (kind) {
        TvFieldKind.URL ->
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        TvFieldKind.PASSWORD ->
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        TvFieldKind.TEXT ->
            InputType.TYPE_CLASS_TEXT
    }
    imeOptions = EditorInfo.IME_ACTION_NEXT

    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            onChange(s?.toString().orEmpty())
        }
    })
}

private fun showKeyboard(view: View) {
    view.requestFocus()
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

private fun hideKeyboard(view: View) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(view.windowToken, 0)
}
