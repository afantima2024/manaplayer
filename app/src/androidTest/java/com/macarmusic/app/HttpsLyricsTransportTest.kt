package com.macarmusic.app

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.lyrics.HttpsLyricsTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

@RunWith(AndroidJUnit4::class)
class HttpsLyricsTransportTest {
    private val address = URL("https://lyrics.kugou.com/test")

    @Test fun cancellingFromTheMainThreadClosesTheBlockedRequestOnlyOnIoThreads() = runBlocking {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + Dispatchers.IO)
        val connection = FixtureConnection(blockRead = true)
        try {
            val transport = HttpsLyricsTransport(scope) { connection }
            val request = scope.async { transport.request(address.toString(), null, null) }
            assertTrue("Request did not reach its read", connection.readStarted.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { request.cancel() }
            assertTrue("Cancelled request was not disconnected", connection.closed.await(5, TimeUnit.SECONDS))
            withTimeout(5_000) { owner.children.toList().joinAll() }
            assertTrue(request.isCancelled)
            assertTrue(connection.closeThreads.isNotEmpty())
            assertTrue("TLS teardown must not run on the Android main thread",
                connection.closeThreads.all { it !== Looper.getMainLooper().thread })
        } finally {
            connection.closed.countDown()
            scope.cancel()
        }
    }

    @Test fun successfulRequestKeepsLimitsHeadersAndIoCleanup() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val connection = FixtureConnection()
        try {
            val transport = HttpsLyricsTransport(scope) { connection }
            assertEquals("synthetic response",
                withTimeout(5_000) { transport.request(address.toString(), "s=fixture", "https://lyrics.kugou.com/") })
            assertTrue(connection.closed.await(5, TimeUnit.SECONDS))
            assertEquals(8_000, connection.connectTimeout)
            assertEquals(8_000, connection.readTimeout)
            assertFalse(connection.instanceFollowRedirects)
            assertFalse(connection.useCaches)
            assertEquals("POST", connection.requestMethod)
            assertEquals("s=fixture", connection.body.toString("UTF-8"))
            assertEquals("https://lyrics.kugou.com/", connection.getRequestProperty("Referer"))
            assertTrue(connection.closeThreads.all { it !== Looper.getMainLooper().thread })
        } finally { scope.cancel() }
    }

    @Test fun unapprovedAddressesNeverOpenAConnection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val transport = HttpsLyricsTransport(scope) { throw AssertionError("Unexpected connection") }
            for (url in listOf("http://lyrics.kugou.com/test", "https://invalid.example/test")) {
                try {
                    transport.request(url, null, null)
                    fail("Unapproved address was accepted")
                } catch (_: IllegalArgumentException) { }
            }
        } finally { scope.cancel() }
    }

    private inner class FixtureConnection(private val blockRead: Boolean = false) : HttpsURLConnection(address) {
        val readStarted = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val closeThreads = CopyOnWriteArrayList<Thread>()
        val body = ByteArrayOutputStream()
        private val headers = mutableMapOf<String, String>()

        override fun setRequestProperty(key: String, value: String) { headers[key] = value }
        override fun getRequestProperty(key: String): String? = headers[key]
        override fun getResponseCode(): Int = 200
        override fun getContentLength(): Int = -1
        override fun getInputStream(): InputStream {
            readStarted.countDown()
            if (!blockRead) return "synthetic response".byteInputStream()
            if (!closed.await(5, TimeUnit.SECONDS)) throw IOException("Fixture read was not cancelled")
            throw IOException("Fixture connection closed")
        }
        override fun getOutputStream() = body
        override fun disconnect() {
            closeThreads.add(Thread.currentThread())
            closed.countDown()
        }
        override fun connect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getCipherSuite(): String = "fixture"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
