package com.macarmusic.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.macarmusic.app.model.AppLogo

object LauncherIcons {
    fun select(context: Context, logo: AppLogo) {
        val selected = if (logo == AppLogo.RADIO) "RadioLauncher" else "MusicLauncher"
        val other = if (logo == AppLogo.RADIO) "MusicLauncher" else "RadioLauncher"
        val manager = context.packageManager
        // Enable the replacement first; never leave the app without a launcher entry.
        manager.setComponentEnabledSetting(ComponentName(context.packageName, "${context.packageName}.$selected"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        manager.setComponentEnabledSetting(ComponentName(context.packageName, "${context.packageName}.$other"),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
