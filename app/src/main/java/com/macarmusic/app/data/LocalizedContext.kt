package com.macarmusic.app.data

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.core.content.ContextCompat

// AppCompat localizes activities on older Android versions, but repositories and services need it too.
class LocalizedContext(base: Context) : ContextWrapper(base) {
    override fun getResources(): Resources {
        return ContextCompat.getContextForLanguage(baseContext).resources
    }
}
