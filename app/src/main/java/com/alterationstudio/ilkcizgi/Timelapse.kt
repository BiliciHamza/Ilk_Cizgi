package com.alterationstudio.ilkcizgi

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/*
 * HIZLANDIRILMIŞ VİDEO (TIME-LAPSE)
 * Çizerken birkaç saniyede bir kameranın gördüğü kâğıt (kılavuz olmadan) kaydedilir.
 * Bitince kareler MP4 videoya çevrilir ve telefonun galerisine (Filmler/IlkCizgi) kaydedilir.
 */

/** Ekran kapansa da işi bitirmesi gereken arka plan işleri için (ör. videoyu kaydetmek) */
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

const val TIMELAPSE_FPS = 12
const val TIMELAPSE_INTERVAL_MS = 1200L
private const val MAX_FRAMES = 600        // bu sayıya gelince kareler seyreltilir
private const val TARGET_W = 720
private const val MAX_H = 1280

class TimelapseRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val id = System.nanoTime()
    private val dir = File(appContext.cacheDir, "timelapse_$id").apply { deleteRecursively(); mkdirs() }
    private val io = Executors.newSingleThreadExecutor()
    private var outW = 0
    private var outH = 0
    @Volatile private var written = 0       // diske yazılan kare sayısı (io iş parçacığı)
    @Volatile private var skip = 1          // seyreltmeden sonra her "skip" karede bir kaydedilir
    private var tick = 0

    /** Ekranda gösterilen kare sayısı */
    var frameCount by mutableIntStateOf(0)
        private set

    /** Ana iş parçacığından çağrılır. Bitmap'i bu sınıf geri dönüştürür. */
    fun addFrame(src: Bitmap) {
        tick++
        if (tick % skip != 0) { src.recycle(); return }
        if (outW == 0) {
            val aspect = src.height.toFloat() / src.width
            var w = TARGET_W
            var h = (w * aspect).toInt() / 16 * 16
            if (h > MAX_H) {
                h = MAX_H
                w = (h / aspect).toInt() / 16 * 16
            }
            outW = max(160, w)
            outH = max(160, h)
        }
        frameCount++
        val w = outW
        val h = outH
        if (io.isShutdown) { src.recycle(); return }
        try {
        io.execute {
            runCatching {
                val frame = cropScale(src, w, h)
                File(dir, "%05d.jpg".format(written)).outputStream().use {
                    frame.compress(Bitmap.CompressFormat.JPEG, 88, it)
                }
                frame.recycle()
                written++
                if (written >= MAX_FRAMES) thin()
            }
            src.recycle()
        }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            src.recycle() // kayıt tam o anda durduruldu
        }
    }

    /** Çok uzun kayıtlarda her ikinci kareyi atıp yer açar (video kısa ve akıcı kalır) */
    private fun thin() {
        val files = dir.listFiles()?.sortedBy { it.name } ?: return
        var n = 0
        files.forEachIndexed { i, f ->
            if (i % 2 == 1) f.delete()
            else f.renameTo(File(dir, "tmp_%05d.jpg".format(n++)))
        }
        dir.listFiles()?.forEach { f -> f.renameTo(File(dir, f.name.removePrefix("tmp_"))) }
        written = n
        skip *= 2
    }

    /**
     * Kaydı bitirir: videoyu üretir, galeriye kaydeder, adresini döndürür.
     * En az 2 kare yoksa null döner.
     */
    suspend fun finish(onProgress: (Float) -> Unit): Uri? = withContext(Dispatchers.Default) {
        io.shutdown()
        io.awaitTermination(15, TimeUnit.SECONDS)
        val frames = dir.listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedBy { it.name } ?: emptyList()
        if (frames.size < 2 || outW == 0) {
            dir.deleteRecursively()
            return@withContext null
        }
        val out = File(appContext.cacheDir, "timelapse_$id.mp4")
        val ok = runCatching { encodeMp4(frames, outW, outH, out, onProgress) }.getOrDefault(false)
        dir.deleteRecursively()
        val uri = if (ok) saveVideoToGallery(appContext, out) else null
        out.delete()
        uri
    }
}

// Temizlik süreç başına sadece bir kere yapılır (ekran yeniden kurulunca süren bir kayda dokunulmasın)
private var staleCleaned = false

/**
 * Uygulama kapanırken yarım kalmış kayıtların geçici kareleri ve videoları silinir.
 * Uygulama açılışında (süreç başına bir kere) ana iş parçacığından çağrılır; silme arka planda yapılır.
 * O anda süren bir kayıt olamaz: kayıtlar uygulama süreciyle birlikte biter.
 */
fun cleanStaleTimelapse(context: Context) {
    if (staleCleaned) return
    staleCleaned = true
    val cache = context.applicationContext.cacheDir
    appScope.launch(Dispatchers.IO) {
        runCatching { cache.listFiles { f -> f.name.startsWith("timelapse_") }?.forEach { it.deleteRecursively() } }
    }
}

/** Kaynağı hedef boyuta ortadan kırparak sığdırır */
private fun cropScale(src: Bitmap, w: Int, h: Int): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val s = max(w.toFloat() / src.width, h.toFloat() / src.height)
    val m = Matrix().apply {
        postScale(s, s)
        postTranslate((w - src.width * s) / 2f, (h - src.height * s) / 2f)
    }
    Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

/** JPEG karelerden H.264 MP4 üretir. Son kare 1 saniye ekranda kalır. */
private fun encodeMp4(frames: List<File>, w: Int, h: Int, out: File, onProgress: (Float) -> Unit): Boolean {
    val fps = TIMELAPSE_FPS
    val hold = fps // son kare 1 sn
    val total = frames.size + hold
    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    var muxer: MediaMuxer? = null
    var muxing = false
    try {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val info = MediaCodec.BufferInfo()
        val argb = IntArray(w * h)
        var loadedIndex = -1
        var input = 0
        var inputDone = false
        var outputDone = false
        var guard = 0

        while (!outputDone) {
            if (!inputDone) {
                val ib = codec.dequeueInputBuffer(10_000)
                if (ib >= 0) {
                    val pts = input * 1_000_000L / fps
                    if (input >= total) {
                        codec.queueInputBuffer(ib, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val fi = min(input, frames.lastIndex)
                        if (fi != loadedIndex) {
                            val bmp = BitmapFactory.decodeFile(frames[fi].path)
                            if (bmp != null) {
                                val fitted = if (bmp.width == w && bmp.height == h) bmp else cropScale(bmp, w, h)
                                fitted.getPixels(argb, 0, w, 0, 0, w, h)
                                if (fitted !== bmp) fitted.recycle()
                                bmp.recycle()
                            }
                            loadedIndex = fi
                        }
                        // Bazı kodlayıcılar satır sonlarına boşluk ekler: tamponun gerçek boyutunu kullan
                        val size = codec.getInputBuffer(ib)?.capacity() ?: (w * h * 3 / 2)
                        val image = codec.getInputImage(ib)
                        if (image != null) fillImage(image, argb, w, h)
                        else codec.getInputBuffer(ib)?.let { fillNv12(it, argb, w, h) }
                        codec.queueInputBuffer(ib, 0, size, pts, 0)
                        input++
                        onProgress(input.toFloat() / total)
                    }
                }
            }
            // Çıkan sıkıştırılmış veriyi dosyaya yaz
            var ob = codec.dequeueOutputBuffer(info, 10_000)
            while (ob != MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                } else if (ob >= 0) {
                    val data = codec.getOutputBuffer(ob)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                    if (data != null && info.size > 0 && muxing) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(track, data, info)
                    }
                    codec.releaseOutputBuffer(ob, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                        break
                    }
                }
                ob = codec.dequeueOutputBuffer(info, 0)
            }
            // Güvenlik: kodlayıcı takılırsa sonsuza kadar bekleme
            if (inputDone && ++guard > 3000) break
        }
        return muxing && outputDone
    } finally {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { if (muxing) muxer?.stop() }
        runCatching { muxer?.release() }
    }
}

// RGB -> YUV (BT.601)
private fun yOf(r: Int, g: Int, b: Int) = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
private fun uOf(r: Int, g: Int, b: Int) = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
private fun vOf(r: Int, g: Int, b: Int) = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

/** Kodlayıcının verdiği YUV görüntüsünü (düzlem aralıklarına uyarak) doldurur */
private fun fillImage(img: Image, argb: IntArray, w: Int, h: Int) {
    val yP = img.planes[0]
    val uP = img.planes[1]
    val vP = img.planes[2]
    val yb = yP.buffer
    val ub = uP.buffer
    val vb = vP.buffer
    val yRow = yP.rowStride; val yPix = yP.pixelStride
    val uRow = uP.rowStride; val uPix = uP.pixelStride
    val vRow = vP.rowStride; val vPix = vP.pixelStride
    for (y in 0 until h) {
        val base = y * yRow
        val off = y * w
        for (x in 0 until w) {
            val c = argb[off + x]
            yb.put(base + x * yPix, yOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF).toByte())
        }
    }
    for (y in 0 until h / 2) {
        for (x in 0 until w / 2) {
            val c = argb[(y * 2) * w + x * 2]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            ub.put(y * uRow + x * uPix, uOf(r, g, b).toByte())
            vb.put(y * vRow + x * vPix, vOf(r, g, b).toByte())
        }
    }
}

/** Yedek yol: çoğu kodlayıcının kullandığı NV12 düzeni */
private fun fillNv12(buf: ByteBuffer, argb: IntArray, w: Int, h: Int) {
    buf.clear()
    for (i in 0 until w * h) {
        val c = argb[i]
        buf.put(yOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF).toByte())
    }
    for (y in 0 until h / 2) {
        for (x in 0 until w / 2) {
            val c = argb[(y * 2) * w + x * 2]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            buf.put(uOf(r, g, b).toByte())
            buf.put(vOf(r, g, b).toByte())
        }
    }
}

/** Videoyu galeriye kaydeder (Android 10+ izin gerekmez). Eski sürümlerde uygulama klasörüne. */
private fun saveVideoToGallery(context: Context, file: File): Uri? = runCatching {
    val name = "IlkCizgi_${System.currentTimeMillis()}.mp4"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val cr = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/IlkCizgi")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return null
        try {
            cr.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Galeriye yazılamadı")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            cr.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            // Yarım kalan (boş) galeri kaydı galeride görünmeyen bir çöp olarak kalmasın
            runCatching { cr.delete(uri, null, null) }
            throw e
        }
    } else {
        val dir = (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "videolar"))
            .apply { mkdirs() }
        val dest = File(dir, name)
        file.copyTo(dest, overwrite = true)
        fileUri(context, dest)
    }
}.getOrNull()
