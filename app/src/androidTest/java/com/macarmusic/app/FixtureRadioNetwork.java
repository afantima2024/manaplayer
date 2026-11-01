package com.macarmusic.app;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.SystemClock;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;

// Test-APK only: exercises the real catalogue, HTTP, HLS and AAC paths without Internet or broadcast recordings.
public final class FixtureRadioNetwork extends URLStreamHandler {
    private static FixtureRadioNetwork installed;
    public final AtomicInteger active = new AtomicInteger();
    public final AtomicInteger catalogues = new AtomicInteger();
    public final AtomicInteger totalCatalogues = new AtomicInteger();
    public final AtomicInteger manifests = new AtomicInteger();
    public final AtomicInteger segments = new AtomicInteger();
    public final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    public volatile int failStreams;
    public volatile String redirect;
    public volatile String customPlaylist;
    public volatile String failPathPrefix;
    public volatile java.util.concurrent.CountDownLatch manifestStarted;
    public volatile java.util.concurrent.CountDownLatch manifestRelease;
    public final AtomicInteger logos = new AtomicInteger();
    public volatile java.util.concurrent.CountDownLatch logoStarted;
    public volatile java.util.concurrent.CountDownLatch logoRelease;
    private final byte[] audio;
    private final byte[] catalogue;
    private long liveStarted;
    private final byte[] logo;

    public static synchronized FixtureRadioNetwork install() throws IOException {
        if (installed == null) {
            FixtureRadioNetwork fixture = new FixtureRadioNetwork();
            URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) || "http".equals(protocol) ? fixture : null);
            installed = fixture;
        }
        return installed;
    }

    private FixtureRadioNetwork() throws IOException {
        audio = encodeTone();
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888);
        image.eraseColor(android.graphics.Color.CYAN);
        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        if (!image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, imageBytes))
            throw new IOException("Cannot encode fixture logo");
        image.recycle();
        logo = imageBytes.toByteArray();
        int[] numbers = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 25};
        String[] codes = {"zgzs", "jjzs", "yyzs", "dszs", "zhzs", "szzs", "hxzs", "xgzs",
            "mzzs", "wyzs", "lnzs", "zygb", "wygb", "gsgljtgb", "xczs", "hygb"};
        StringBuilder json = new StringBuilder("{\"code\":\"200\",\"data\":{\"categories\":[{\"detail\":[");
        for (int i = 0; i < numbers.length; i++) {
            if (i > 0) json.append(',');
            json.append("{\"other_info11\":[{\"url\":\"https://ngcdn")
                .append(String.format(java.util.Locale.ROOT, "%03d", numbers[i]))
                .append(".cnr.cn/live/").append(codes[i]).append("/index.m3u8?key=synthetic-fixture\"}]}");
        }
        catalogue = json.append("]}]}}").toString().getBytes(StandardCharsets.UTF_8);
        liveStarted = SystemClock.elapsedRealtime();
    }

    public void reset() {
        if (active.get() != 0) throw new IllegalStateException("A previous fixture connection is still open");
        failStreams = 0;
        redirect = null;
        customPlaylist = null;
        failPathPrefix = null;
        manifestStarted = null;
        manifestRelease = null;
        logos.set(0);
        logoStarted = null;
        logoRelease = null;
        requests.clear();
        catalogues.set(0);
        manifests.set(0);
        segments.set(0);
        liveStarted = SystemClock.elapsedRealtime();
    }

    private byte[] livePlaylist() {
        long sequence = (SystemClock.elapsedRealtime() - liveStarted) / 4000;
        StringBuilder m3u = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:5\n#EXT-X-MEDIA-SEQUENCE:")
            .append(sequence).append('\n');
        for (long index = sequence; index < sequence + 10; index++)
            m3u.append("#EXTINF:4.0,\ntone").append(index).append(".aac\n");
        return m3u.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override protected URLConnection openConnection(URL address) throws IOException {
        if (!"apppc.cnr.cn".equals(address.getHost()) &&
            !address.getHost().matches("ngcdn\\d{3}\\.cnr\\.cn") &&
            !"radio.example".equals(address.getHost()) && !"cdn.radio.example".equals(address.getHost()) &&
            !"cnvod.cnr.cn".equals(address.getHost()) && !"p1crires.cri.cn".equals(address.getHost()) &&
            !"sk.cri.cn".equals(address.getHost()))
            throw new IOException("Unexpected fixture host");
        return new HttpsURLConnection(address) {
            private byte[] body;
            private int status = 200;
            private String type;
            private String location;
            private boolean counted;
            @Override public void connect() throws IOException {
                if (body != null) return;
                requests.add(getURL().getHost() + getURL().getPath());
                if ("apppc.cnr.cn".equals(getURL().getHost())) {
                    if (!"/national".equals(getURL().getPath()) || !"POST".equals(getRequestMethod()))
                        throw new IOException("Unexpected fixture catalogue request");
                    catalogues.incrementAndGet();
                    totalCatalogues.incrementAndGet();
                    body = catalogue;
                    type = "application/json";
                } else if (getURL().getPath().endsWith(".m3u8")) {
                    manifests.incrementAndGet();
                    if (manifestStarted != null) manifestStarted.countDown();
                    if (manifestRelease != null) {
                        try {
                            if (!manifestRelease.await(15, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IOException("Fixture manifest wait timed out");
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Fixture manifest interrupted", error);
                        }
                    }
                    body = customPlaylist == null ? livePlaylist() : customPlaylist.getBytes(StandardCharsets.UTF_8);
                    type = "application/vnd.apple.mpegurl";
                    if (failPathPrefix != null && getURL().getPath().startsWith(failPathPrefix)) status = 404;
                    else if (failStreams != 0) status = failStreams;
                    else if (redirect != null && getURL().getPath().endsWith("/index.m3u8")) {
                        location = redirect;
                        status = 302;
                    }
                } else if (getURL().getPath().endsWith(".png") || getURL().getPath().endsWith(".jpg")) {
                    logos.incrementAndGet();
                    if (logoStarted != null) logoStarted.countDown();
                    if (logoRelease != null) {
                        try {
                            if (!logoRelease.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IOException("Fixture logo wait timed out");
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Fixture logo interrupted", error);
                        }
                    }
                    body = getURL().getPath().contains("oversize") ? new byte[512 * 1024 + 1] :
                        getURL().getPath().contains("invalid") ? new byte[]{1, 2, 3} : logo;
                    type = "image/png";
                } else if (getURL().getPath().endsWith(".aac")) {
                    segments.incrementAndGet();
                    body = audio;
                    type = "audio/aac";
                } else throw new IOException("Unexpected fixture stream path");
                counted = true;
                active.incrementAndGet();
            }
            @Override public int getResponseCode() throws IOException { connect(); return status; }
            @Override public String getResponseMessage() { return "Synthetic response"; }
            @Override public InputStream getInputStream() throws IOException { connect(); return new ByteArrayInputStream(body); }
            @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
            @Override public int getContentLength() { return body == null ? -1 : body.length; }
            @Override public String getHeaderField(String name) {
                if ("Location".equalsIgnoreCase(name)) return location;
                if ("Content-Length".equalsIgnoreCase(name)) return body == null ? null : Integer.toString(body.length);
                if ("Content-Type".equalsIgnoreCase(name)) return type;
                return null;
            }
            @Override public Map<String, List<String>> getHeaderFields() {
                Map<String, List<String>> headers = new HashMap<>();
                if (body != null) headers.put("Content-Length", Collections.singletonList(Integer.toString(body.length)));
                if (location != null) headers.put("Location", Collections.singletonList(location));
                return headers;
            }
            @Override public void disconnect() {
                if (counted) { counted = false; active.decrementAndGet(); }
            }
            @Override public boolean usingProxy() { return false; }
            @Override public String getCipherSuite() { return "Test fixture"; }
            @Override public Certificate[] getLocalCertificates() { return null; }
            @Override public Certificate[] getServerCertificates() { return new Certificate[0]; }
        };
    }

    private static byte[] encodeTone() throws IOException {
        MediaCodec encoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
        boolean started = false;
        try {
            int rate = 44100;
            MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", rate, 1);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            started = true;
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int samples = 0;
            boolean inputEnded = false;
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (SystemClock.uptimeMillis() < deadline) {
                if (!inputEnded) {
                    int index = encoder.dequeueInputBuffer(1000);
                    if (index >= 0) {
                        ByteBuffer input = encoder.getInputBuffer(index);
                        if (input == null) throw new IOException("No AAC encoder input buffer");
                        input.clear();
                        input.order(ByteOrder.LITTLE_ENDIAN);
                        int count = Math.min(input.remaining() / 2, rate * 4 - samples);
                        for (int i = 0; i < count; i++) {
                            input.putShort((short) (Math.sin((samples + i) * 2 * Math.PI * 440 / rate) * 500));
                        }
                        inputEnded = count == 0;
                        encoder.queueInputBuffer(index, 0, count * 2, samples * 1000000L / rate,
                            inputEnded ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        samples += count;
                    }
                }
                int index = encoder.dequeueOutputBuffer(info, 1000);
                if (index >= 0) {
                    ByteBuffer output = encoder.getOutputBuffer(index);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                        if (output == null) throw new IOException("No AAC encoder output buffer");
                        int length = info.size + 7;
                        byte[] header = {(byte) 0xff, (byte) 0xf1, (byte) ((1 << 6) | (4 << 2)),
                            (byte) ((1 << 6) | (length >> 11)), (byte) (length >> 3),
                            (byte) (((length & 7) << 5) | 0x1f), (byte) 0xfc};
                        result.write(header);
                        byte[] frame = new byte[info.size];
                        output.position(info.offset);
                        output.get(frame);
                        result.write(frame);
                    }
                    encoder.releaseOutputBuffer(index, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return result.toByteArray();
                }
            }
            throw new IOException("Synthetic AAC encoding timed out");
        } finally {
            if (started) encoder.stop();
            encoder.release();
        }
    }
}
