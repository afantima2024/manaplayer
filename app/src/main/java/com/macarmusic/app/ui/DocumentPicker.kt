package com.macarmusic.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log

internal object DocumentPicker {
    fun launch(mimeType: String, multiple: Boolean = false, launch: (Intent) -> Unit): Boolean {
        val requests = listOf(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            },
            Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE),
            Intent(Intent.ACTION_GET_CONTENT),
        )
        for (request in requests) {
            request.type = mimeType
            request.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            if (multiple) request.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            request.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                launch(request)
                return true
            } catch (error: ActivityNotFoundException) {
                Log.i("DocumentPicker", "Picker unavailable for ${request.action}; trying the next compatible request", error)
            }
        }
        return false
    }
}
