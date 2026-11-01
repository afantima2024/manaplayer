package com.macarmusic.app.radio

import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

@androidx.annotation.OptIn(UnstableApi::class)
class RadioTraffic(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val seconds = LongArray(4) { -1 }
    private val bytes = LongArray(4)
    private var selected: String? = null
    private var selectedSource: String? = null
    private var started = 0L

    @Synchronized fun select(id: String?, uri: String? = null) {
        selected = id
        selectedSource = uri
        started = clock()
        seconds.fill(-1)
        bytes.fill(0)
    }

    @Synchronized fun bytesPerSecond(id: String): Long {
        if (selected != id) return 0
        val now = clock()
        val second = now / 1000
        val count = seconds.indices.sumOf { if (seconds[it] in (second - 2)..second) bytes[it] else 0 }
        return count * 1000 / (now - started).coerceIn(1000, 3000)
    }

    fun listener(station: RadioProgram): TransferListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork) receive(station.id, station.streamUri, bytesTransferred)
        }
    }

    @Synchronized private fun receive(id: String, uri: String, bytesTransferred: Int) {
        if (selected != id || selectedSource != uri) return
        val second = clock() / 1000
        val index = (second % seconds.size).toInt()
        if (seconds[index] != second) { seconds[index] = second; bytes[index] = 0 }
        bytes[index] += bytesTransferred
    }
}
