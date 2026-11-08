package com.macarmusic.app.ui

import android.content.Context
import android.content.DialogInterface
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.macarmusic.app.R
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.radio.RadioCatalogState
import com.macarmusic.app.radio.RadioSubscription
import com.macarmusic.app.radio.RadioSubscriptionProgress
import com.macarmusic.app.radio.RadioSubscriptions
import java.text.DateFormat
import java.util.Date

internal class RadioSubscriptionsDialog(
    private val context: Context,
    private val model: MusicViewModel,
    dismissed: () -> Unit,
) {
    private var state: RadioCatalogState? = null
    private var progress: RadioSubscriptionProgress? = null
    private var editable = false
    private var child: PlayerDialog? = null
    private var buttons: BlockButtonStyler? = null
    private class Row(val button: MaterialButton) {
        val styler = BlockButtonStyler(button)
    }
    private val rowViews = linkedMapOf<String, Row>()
    private val rows = LinearLayout(context).apply { id = R.id.radioSubscriptionRows; orientation = LinearLayout.VERTICAL }
    private val status = text().apply { id = R.id.radioSubscriptionProgress; isVisible = false }
    private val empty = text().apply { setText(R.string.radio_subscription_empty) }
    private val body: ScrollView = ScrollView(context).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(8))
            addView(text().apply { setText(R.string.radio_subscription_intro) })
            addView(status)
            addView(empty)
            addView(rows)
        })
    }
    private val dialog: PlayerDialog = AccentTheme.dialog(context).setTitle(R.string.radio_subscriptions)
        .setView(lyricDialogContent(body, (context.resources.configuration.screenHeightDp - 200).coerceIn(160, 400)))
        .setPositiveButton(R.string.radio_subscription_add, null)
        .setNeutralButton(R.string.radio_subscription_update_all, null)
        .setNegativeButton(R.string.close, null).create().apply {
            setOnDismissListener { child?.dismiss(); child = null; dismissed() }
        }

    fun show() {
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { presets() }
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            if (progress != null) model.cancelRadioSubscriptions() else model.refreshRadioSubscriptions()
        }
        buttons = BlockButtonStyler.dialogButtons(dialog)
    }

    fun dismiss() { dialog.dismiss() }

    fun update(catalog: RadioCatalogState, pending: RadioSubscriptionProgress?, busy: Boolean) {
        state = catalog
        progress = pending
        editable = catalog.ready && catalog.error == null && !busy && pending == null
        status.isVisible = pending != null || catalog.error != null || !catalog.ready
        status.text = when {
            catalog.error != null -> context.getString(R.string.radio_list_load_failed)
            pending?.cancelling == true -> context.getString(R.string.radio_subscription_cancelling)
            pending != null && pending.total > 0 -> context.getString(R.string.radio_subscription_progress,
                pending.name, pending.completed + 1, pending.total)
            else -> context.getString(R.string.loading)
        }
        empty.isVisible = catalog.ready && catalog.subscriptions.isEmpty()
        val ids = catalog.subscriptions.mapTo(hashSetOf()) { it.id }
        rowViews.keys.filter { it !in ids }.forEach { id -> rows.removeView(rowViews.remove(id)?.button) }
        for (subscription in catalog.subscriptions) {
            val row = rowViews.getOrPut(subscription.id) {
                MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    tag = subscription.id
                    setTag(R.id.playerFocusKey, "subscription:${subscription.id}")
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    textSize = 16f
                    minHeight = dp(64)
                    setOnClickListener { state?.subscriptions?.find { it.id == subscription.id }?.let(::options) }
                    AccentTheme.tintControls(this, readableOnSurface = true)
                    installPlayerInputFocus()
                    rows.addView(this, LinearLayout.LayoutParams(-1, -2))
                }.let(::Row)
            }.button
            val summary = summary(subscription)
            if (row.text.toString() != summary) row.text = summary
            row.isEnabled = editable
        }
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = editable &&
            catalog.subscriptions.size < RadioSubscriptions.MAX_SUBSCRIPTIONS
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).apply {
            setText(if (pending == null) R.string.radio_subscription_update_all else R.string.radio_subscription_cancel)
            isEnabled = pending != null && !pending.cancelling ||
                editable && catalog.subscriptions.any { it.enabled }
        }
        val display = model.app.displaySettings.options.value
        configureBlocks(display.controlBlocks, display.blockOutline)
    }

    fun configureBlocks(enabled: Boolean, outline: BlockOutline) {
        buttons?.apply(enabled, outline)
        rowViews.values.forEach { it.styler.apply(enabled, outline) }
    }

    private fun summary(subscription: RadioSubscription): String {
        fun time(value: Long) = if (value == 0L) context.getString(R.string.radio_subscription_never)
            else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
        val detail = subscription.failure?.let {
            context.getString(R.string.radio_subscription_failed,
                context.getString(RadioSubscriptions.failureMessage(it), it.httpStatus))
        } ?: context.getString(R.string.radio_subscription_updated, time(subscription.updatedAtMs))
        val summary = context.getString(R.string.radio_subscription_summary, subscription.name,
            context.getString(if (subscription.enabled) R.string.radio_subscription_enabled else R.string.radio_subscription_disabled),
            subscription.programs.size, subscription.programs.sumOf { it.sources.size },
            time(subscription.checkedAtMs), detail)
        return summary + (if (subscription.unavailableStations == 0) "" else "\n" +
            context.getString(R.string.radio_subscription_unavailable, subscription.unavailableStations)) +
            (if (subscription.ignoredLogos == 0) "" else "\n" +
                context.getString(R.string.radio_subscription_ignored_logos, subscription.ignoredLogos))
    }

    private fun presets() {
        if (!editable) return
        val presets = RadioSubscriptions.presets
        val existing = state?.subscriptions.orEmpty().associateBy { RadioSubscriptions.address(it.url) }
        val labels = presets.map { preset ->
            val title = context.getString(preset.title)
            existing[RadioSubscriptions.address(preset.url)]?.let {
                context.getString(if (it.enabled) R.string.radio_subscription_preset_added
                    else R.string.radio_subscription_preset_disabled, title)
            } ?: title
        }
        child = AccentTheme.dialog(context).setTitle(R.string.radio_subscription_presets)
            .setItems((labels + context.getString(R.string.radio_subscription_custom))
                .toTypedArray()) { _, index ->
                if (index == presets.size) editor()
                else {
                    val preset = presets[index]
                    val added = state?.subscriptions?.find {
                        RadioSubscriptions.address(it.url) == RadioSubscriptions.address(preset.url)
                    }
                    if (added != null) options(added)
                    else editor(name = context.getString(preset.title), url = preset.url, description = preset.description)
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun options(subscription: RadioSubscription) {
        if (!editable) return
        val actions = buildList<Pair<Int, () -> Unit>> {
            if (subscription.enabled) add(R.string.radio_subscription_update to { model.refreshRadioSubscriptions(subscription.id) })
            add((if (subscription.enabled) R.string.radio_subscription_disable else R.string.radio_subscription_enable) to {
                model.changeRadioSubscription(subscription, enabled = !subscription.enabled)
            })
            add(R.string.radio_subscription_edit to { editor(subscription, subscription.name, subscription.url) })
            add(R.string.details to {
                child = AccentTheme.dialog(context).setTitle(subscription.name)
                    .setMessage("${summary(subscription)}\n\n${subscription.url}")
                    .setPositiveButton(R.string.close, null).show()
            })
            add(R.string.radio_subscription_remove to {
                child = AccentTheme.dialog(context).setTitle(R.string.radio_subscription_remove)
                    .setMessage(context.getString(R.string.radio_subscription_remove_confirm, subscription.name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.radio_subscription_remove) { _, _ ->
                        model.changeRadioSubscription(subscription, remove = true)
                    }.show()
            })
        }
        child = AccentTheme.dialog(context).setTitle(subscription.name)
            .setItems(actions.map { context.getString(it.first) }.toTypedArray()) { _, index -> actions[index].second() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun editor(original: RadioSubscription? = null, name: String = "", url: String = "",
        description: Int = R.string.radio_subscription_edit_help) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(16))
        }
        content.addView(text().apply { setText(description) })
        fun field(id: Int, label: Int, value: String, length: Int, uri: Boolean = false): Pair<TextInputLayout, TextInputEditText> {
            val layout = TextInputLayout(context).apply {
                hint = context.getString(label)
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            }
            val input = TextInputEditText(layout.context).apply {
                this.id = id
                inputType = InputType.TYPE_CLASS_TEXT or if (uri) InputType.TYPE_TEXT_VARIATION_URI else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine(true)
                minHeight = dp(56)
                filters = arrayOf(InputFilter.LengthFilter(length))
                setText(value)
            }
            layout.addView(input)
            content.addView(layout, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            return layout to input
        }
        val (nameLayout, title) = field(R.id.radioSubscriptionName, R.string.radio_subscription_name, name, 80)
        val (urlLayout, address) = field(R.id.radioSubscriptionUrl, R.string.radio_subscription_url, url, 8192, true)
        val editor = AccentTheme.dialog(context)
            .setTitle(if (original == null) R.string.radio_subscription_add else R.string.radio_subscription_edit)
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (original == null) R.string.radio_subscription_add_update else R.string.save, null).show()
        child = editor
        editor.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val enteredName = title.text.toString().trim()
            val enteredUrl = RadioSubscriptions.address(address.text.toString())
            nameLayout.error = if (RadioSubscriptions.validName(enteredName)) null else context.getString(R.string.radio_subscription_bad_name)
            urlLayout.error = if (enteredUrl != null) null else context.getString(R.string.radio_list_bad_url)
            if (nameLayout.error == null && enteredUrl != null) {
                model.saveRadioSubscription(enteredName, enteredUrl, original)
                editor.dismiss()
            }
        }
    }

    private fun text() = TextView(context).apply { textSize = 14f; setPadding(0, dp(6), 0, dp(6)) }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
}
