package com.macarmusic.app.ui

import android.content.Context
import androidx.annotation.DrawableRes
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.model.AppLogo
import com.macarmusic.app.radio.RadioButtonLogo

object Branding {
    @DrawableRes fun icon(logo: AppLogo) =
        if (logo == AppLogo.RADIO) R.drawable.ic_launcher_radio else R.drawable.ic_launcher

    @DrawableRes fun artwork(logo: AppLogo) =
        if (logo == AppLogo.RADIO) R.drawable.player_radio_logo else R.drawable.player_logo

    @DrawableRes fun icon(context: Context) =
        icon((context.applicationContext as MaCarMusicApp).displaySettings.options.value.appLogo)

    @DrawableRes fun radio(logo: RadioButtonLogo) =
        if (logo == RadioButtonLogo.BRANDED_RADIO) R.drawable.ic_launcher_radio else R.drawable.ic_radio

    @DrawableRes fun radio(context: Context) =
        radio((context.applicationContext as MaCarMusicApp).radio.options.value.buttonLogo)
}
