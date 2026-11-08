package com.macarmusic.app.radio

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.macarmusic.app.R

enum class RadioStationLogo(@StringRes val title: Int, @DrawableRes val image: Int) {
    NEWS(R.string.radio_mark_news, R.drawable.radio_mark_news),
    TALK(R.string.radio_mark_talk, R.drawable.radio_mark_talk),
    POP(R.string.radio_mark_pop, R.drawable.radio_mark_pop),
    CLASSIC(R.string.radio_mark_classic, R.drawable.radio_mark_classic),
    PIANO(R.string.radio_mark_piano, R.drawable.radio_mark_piano),
    ACOUSTIC(R.string.radio_mark_acoustic, R.drawable.radio_mark_acoustic),
    JAZZ(R.string.radio_mark_jazz, R.drawable.radio_mark_jazz),
    WORLD(R.string.radio_mark_world, R.drawable.radio_mark_world),
    AMBIENT(R.string.radio_mark_ambient, R.drawable.radio_mark_ambient),
    FOLK(R.string.radio_mark_folk, R.drawable.radio_mark_folk),
    DANCE(R.string.radio_mark_dance, R.drawable.radio_mark_dance),
    CCTV(R.string.radio_mark_cctv, R.drawable.radio_mark_cctv),
}
