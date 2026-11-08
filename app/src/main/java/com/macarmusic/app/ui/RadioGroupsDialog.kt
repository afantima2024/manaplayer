package com.macarmusic.app.ui

import android.content.Context
import android.content.DialogInterface
import android.util.Log
import android.widget.BaseAdapter
import com.macarmusic.app.R
import com.macarmusic.app.radio.RadioCatalogSnapshot
import com.macarmusic.app.radio.RadioGroup
import com.macarmusic.app.radio.RadioGroups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal object RadioGroupsDialog {
    private data class Choice(val group: RadioGroup, val count: Int)

    fun show(context: Context, scope: CoroutineScope, model: MusicViewModel,
        initial: RadioGroup? = null, hiddenOnly: Boolean = false) = scope.launch {
        val catalog = try { model.app.radioCatalog.readyState() }
        catch (error: IOException) {
            Log.w("RadioGroups", "Cannot read countries from the saved catalog", error)
            model.app.report(context.getString(R.string.radio_list_load_failed))
            return@launch
        }
        val hidden = model.app.radio.options.value.hiddenGroups
        val choices = withContext(Dispatchers.Default) {
            val groups = linkedMapOf<String, Choice>()
            for (station in catalog.programs) {
                val group = RadioGroups.of(station)
                val old = groups[group.id]
                groups[group.id] = Choice(old?.group ?: group, (old?.count ?: 0) + 1)
            }
            hidden.forEach { id -> if (id !in groups) groups[id] = Choice(RadioGroups.named(id), 0) }
            groups.values.filter { !hiddenOnly || it.group.id in hidden }
                .sortedWith { a, b -> RadioGroups.order.compare(a.group, b.group) }
        }
        if (choices.isEmpty()) {
            model.app.inform(context.getString(if (hiddenOnly) R.string.radio_groups_none_hidden else R.string.radio_empty))
            return@launch
        }
        val checked = BooleanArray(choices.size) { hiddenOnly || choices[it].group.id == initial?.id }
        fun label(choice: Choice, hiddenGroups: Set<String>): String {
            val count = context.resources.getQuantityString(R.plurals.radio_group_stations, choice.count, choice.count)
            return "${choice.group.title(context)}\n$count" +
                if (choice.group.id in hiddenGroups) " - ${context.getString(R.string.radio_group_hidden)}" else ""
        }
        val labels = choices.map { label(it, hidden) }.toTypedArray()
        var renderedHidden = hidden
        fun selected() = choices.filterIndexed { index, _ -> checked[index] }
        lateinit var dialog: PlayerDialog
        fun update() {
            val selection = selected()
            val currentHidden = model.app.radio.options.value.hiddenGroups
            if (currentHidden != renderedHidden) {
                choices.forEachIndexed { index, choice -> labels[index] = label(choice, currentHidden) }
                (dialog.listView.adapter as BaseAdapter).notifyDataSetChanged()
                renderedHidden = currentHidden
            }
            dialog.setTitle(context.getString(
                if (hiddenOnly) R.string.radio_groups_show_title else R.string.radio_groups_select_title, selection.size))
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).apply {
                isEnabled = selection.isNotEmpty()
                setText(if (selection.isNotEmpty() && selection.all { it.group.id in currentHidden })
                    R.string.radio_groups_show else R.string.radio_groups_hide)
            }
            if (!hiddenOnly) dialog.getButton(DialogInterface.BUTTON_NEUTRAL).isEnabled =
                selection.any { it.count > 0 }
        }
        fun selectAll(value: Boolean) {
            checked.fill(value)
            choices.indices.forEach { dialog.listView.setItemChecked(it, value) }
            update()
        }
        val builder = AccentTheme.dialog(context)
            .setTitle(context.getString(if (hiddenOnly) R.string.radio_groups_show_title else R.string.radio_groups_select_title,
                checked.count { it }))
            .setMultiChoiceItems(labels, checked) { _, _, _ -> update() }
            .setSelectionActions({ selectAll(true) }, { selectAll(false) })
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.radio_groups_hide, null)
        if (!hiddenOnly) builder.setNeutralButton(R.string.radio_groups_delete) { _, _ ->
            confirmDelete(context, model, selected().filter { it.count > 0 }, catalog.snapshot)
        }
        dialog = builder.show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val ids = selected().mapTo(hashSetOf()) { it.group.id }
            if (ids.all { it in model.app.radio.options.value.hiddenGroups }) model.showRadioGroups(ids)
            else model.hideRadioGroups(ids)
            update()
        }
        update()
        choices.indexOfFirst { it.group.id == initial?.id }.takeIf { it >= 0 }?.let { dialog.listView.setSelection(it) }
    }

    private fun confirmDelete(context: Context, model: MusicViewModel, choices: List<Choice>, snapshot: RadioCatalogSnapshot) {
        val names = choices.take(8).joinToString(", ") { it.group.title(context) } +
            if (choices.size > 8) context.getString(R.string.radio_groups_more, choices.size - 8) else ""
        AccentTheme.dialog(context).setTitle(R.string.radio_groups_delete)
            .setMessage(context.getString(R.string.radio_groups_delete_confirm, choices.sumOf { it.count }, names))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.radio_groups_delete) { _, _ ->
                model.deleteRadioGroups(choices.mapTo(hashSetOf()) { it.group.id }, snapshot)
            }.show()
    }
}
