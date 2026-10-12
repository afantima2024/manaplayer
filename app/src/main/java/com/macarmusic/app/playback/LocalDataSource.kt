package com.macarmusic.app.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
class LocalDataSource(context: Context) : DataSource {
    private val content = ContentDataSource(context)

    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.uri.scheme != "content") throw IOException("Only content:// audio is allowed")
        return content.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = content.read(buffer, offset, length)

    override fun getUri(): Uri? = content.uri

    override fun close() = content.close()

    override fun addTransferListener(transferListener: TransferListener) = content.addTransferListener(transferListener)
}
