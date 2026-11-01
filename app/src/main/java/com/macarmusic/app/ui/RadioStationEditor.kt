package com.macarmusic.app.ui

import android.content.Context
import android.content.DialogInterface
import android.text.InputType
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.macarmusic.app.R
import com.macarmusic.app.radio.RadioAddress
import com.macarmusic.app.radio.RadioList
import com.macarmusic.app.radio.RadioProgram

object RadioStationEditor {
    fun show(context: Context, station: RadioProgram, save: (RadioProgram) -> Unit) {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (8 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        fun field(id: Int, hint: Int, text: String, multiline: Boolean = false): Pair<TextInputLayout, TextInputEditText> {
            val layout = TextInputLayout(context).apply {
                this.hint = context.getString(hint)
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            }
            val input = TextInputEditText(layout.context).apply {
                this.id = id
                inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS else InputType.TYPE_CLASS_TEXT
                setSingleLine(!multiline)
                if (multiline) { minLines = 4; maxLines = 8; gravity = android.view.Gravity.TOP }
                setText(text)
            }
            layout.addView(input)
            body.addView(layout)
            return layout to input
        }
        val (nameLayout, name) = field(R.id.radioEditName, R.string.radio_edit_name, station.title(context))
        val (groupLayout, group) = field(R.id.radioEditGroup, R.string.radio_edit_group, station.group)
        val (urlLayout, urls) = field(R.id.radioEditSources, R.string.radio_edit_sources, station.sources.joinToString("\n"), true)
        val (logoLayout, logo) = field(R.id.radioEditLogo, R.string.radio_edit_logo, station.logoUri)
        val dialog = AccentTheme.dialog(context).setTitle(R.string.radio_edit)
            .setView(ScrollView(context).apply { addView(body) })
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val title = name.text.toString().trim()
            val category = group.text.toString().trim()
            val image = logo.text.toString().trim()
            val lines = urls.text.toString().lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            val addresses = lines.map(RadioAddress::canonical)
            nameLayout.error = if (title.isBlank() || title.length > 256 || title.any(Char::isISOControl))
                context.getString(R.string.radio_list_bad_name) else null
            groupLayout.error = if (category.length > 256 || category.any(Char::isISOControl))
                context.getString(R.string.radio_list_bad_name) else null
            urlLayout.error = when {
                addresses.isEmpty() || addresses.any { it == null } -> context.getString(R.string.radio_list_bad_url)
                addresses.distinct().size > RadioList.MAX_SOURCES -> context.getString(R.string.radio_sources_too_many)
                else -> null
            }
            logoLayout.error = if (image.isNotEmpty() && RadioAddress.parse(image) == null)
                context.getString(R.string.radio_list_bad_url) else null
            if (listOf(nameLayout, groupLayout, urlLayout, logoLayout).any { it.error != null }) return@setOnClickListener
            val sources = addresses.filterNotNull().distinct()
            save(station.copy(name = if (station.builtin?.let { context.getString(it.title) } == title) "" else title,
                group = category, uri = sources.first(), alternateUris = sources.drop(1),
                sourceIndex = sources.indexOf(station.streamUri).coerceAtLeast(0), logoUri = image))
            dialog.dismiss()
        }
    }
}
