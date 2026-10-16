package com.alterationstudio.ilkcizgi

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * GÖRÜNTÜ İŞLEME
 *
 * Kamera karesi arka planda işlenir (ana ekranı hiç yormaz). Ekran küçük hücrelere bölünür ve
 * her hücre için çizimin orada görünüp görünmeyeceği hesaplanır:
 *  - EL ÖNDE: Kâğıt beyazı olmayan BÜYÜK lekeler (el, kalem tutan parmaklar) bulunur, çizim oralarda gizlenir.
 *  - ÇİZDİKLERİMİ GİZLE: Kâğıttaki ince koyu çizgiler (kalemle çizdiğin yerler) bulunur,
 *    kılavuz oralarda kaybolur. Böylece nereyi çizdiğini, nerenin kaldığını görürsün.
 */

const val MASK_W = 128          // maskenin yatay hücre sayısı
private const val SUB = 3        // her hücrede SUB x SUB nokta okunur (ince kalem çizgisi kaçmasın)
private const val LOCAL_R = 3    // yerel kâğıt parlaklığı için komşuluk yarıçapı (hücre)

/**
 * Kamera görüntüsü (tampon) koordinatları ile ekran koordinatları arasında çeviri.
 * Görüntünün görünen kısmı (cropRect) dik çevrilip ekrana ortalanarak sığdırılır (önizlemeyle aynı).
 */
class ViewMapper(crop: Rect, private val rot: Int, private val viewW: Int, private val viewH: Int) {
    private val cl = crop.left.toFloat()
    private val ct = crop.top.toFloat()
    private val cw = crop.width().toFloat()
    private val ch = crop.height().toFloat()
    /** Dik çevrilmiş görüntünün boyutları */
    val uprightW = if (rot == 90 || rot == 270) ch else cw
    val uprightH = if (rot == 90 || rot == 270) cw else ch
    private val s = max(viewW / uprightW, viewH / uprightH)
    private val ox = (viewW - uprightW * s) / 2f
    private val oy = (viewH - uprightH * s) / 2f

    fun matches(crop: Rect, rot: Int, vw: Int, vh: Int) =
        crop.left.toFloat() == cl && crop.top.toFloat() == ct && crop.width().toFloat() == cw &&
                crop.height().toFloat() == ch && rot == this.rot && vw == viewW && vh == viewH

    /** Dik görüntü noktası -> tampon pikseli (x, y) */
    fun uprightToBufferX(ux: Float, uy: Float): Float = cl + when (rot) {
        90 -> uy
        180 -> cw - ux
        270 -> cw - uy
        else -> ux
    }

    fun uprightToBufferY(ux: Float, uy: Float): Float = ct + when (rot) {
        90 -> ch - ux
        180 -> ch - uy
        270 -> ux
        else -> uy
    }

    fun viewToUprightX(vx: Float) = (vx - ox) / s
    fun viewToUprightY(vy: Float) = (vy - oy) / s
}

/** Maskenin dikey hücre sayısı (ekran oranına göre) */
fun maskRows(viewW: Int, viewH: Int) = max(1, (MASK_W.toLong() * viewH / viewW).toInt())

/**
 * Bir karenin sonucu.
 * mask: çizimin görüneceği yerler (alfa), null ise maske yok.
 * ink: kâğıtta kalemle çizilmiş hücreler (ilerleme yüzdesi için), null ise bakılmadı.
 * hand: elin kapattığı hücreler (ilerleme yüzdesi için: elin altındaki çizgiler o an görünmez, silinmiş sayılmaz).
 */
class MaskResult(val mask: Bitmap?, val ink: BooleanArray?, val hand: BooleanArray?, val cols: Int, val rows: Int)

/**
 * Çizimin nerede görüneceğini belirleyen maske (alfa = görünürlük).
 */
class TraceMasker {
    private var mapper: ViewMapper? = null
    private var yRowStride = -1
    private var yPixStride = -1
    private var mw = 0
    private var mh = 0
    private var yIdx = IntArray(0)   // her hücrenin SUB*SUB noktasının Y düzlemindeki yeri
    private var cIdxU = IntArray(0)  // hücre ortasının U düzlemindeki yeri
    private var cIdxV = IntArray(0)  // hücre ortasının V düzlemindeki yeri
    private var yAvg = IntArray(0)
    private var yMin = IntArray(0)
    private var us = IntArray(0)
    private var vs = IntArray(0)
    private var tmp = IntArray(0)
    private var local = IntArray(0)
    private var handRaw = BooleanArray(0)
    private var handCore = BooleanArray(0)
    private var hand = BooleanArray(0)
    private var ink = BooleanArray(0)
    private var prev = FloatArray(0)
    private var alpha = IntArray(0)
    private var alphas = ByteArray(0)   // maskenin görünürlük değerleri (satır satır)
    private val hist = IntArray(256)

    fun build(
        image: ImageProxy, m: ViewMapper, viewW: Int, viewH: Int,
        handFront: Boolean, hideDrawn: Boolean, detectInk: Boolean, sensitivity: Float
    ): MaskResult {
        val yP = image.planes[0]
        val uP = image.planes[1]
        val vP = image.planes[2]
        if (mapper !== m || yP.rowStride != yRowStride || yP.pixelStride != yPixStride) {
            prepare(m, image, viewW, viewH)
        }
        val w = mw
        val h = mh
        val n = w * h
        val yb = yP.buffer
        val ub = uP.buffer
        val vb = vP.buffer
        val ss = SUB * SUB

        // --- 1) Her hücrenin ortalama ve en koyu parlaklığı, rengi ---
        hist.fill(0)
        for (k in 0 until n) {
            var sum = 0
            var mn = 255
            val base = k * ss
            for (t in 0 until ss) {
                val y = yb.get(yIdx[base + t]).toInt() and 0xFF
                sum += y
                if (y < mn) mn = y
            }
            val a = sum / ss
            yAvg[k] = a
            yMin[k] = mn
            hist[a]++
            us[k] = ub.get(cIdxU[k]).toInt() and 0xFF
            vs[k] = vb.get(cIdxV[k]).toInt() and 0xFF
        }
        // Kâğıdın parlaklığı: görüntünün en parlak %15'inin sınırı (ışığa göre kendini ayarlar)
        val target = (n * 0.85f).toInt()
        var acc = 0
        var ref = 255
        for (v in 0 until 256) {
            acc += hist[v]
            if (acc > target) { ref = v; break }
        }

        // --- 2) El: kâğıt gibi görünmeyen BÜYÜK lekeler ---
        val minY = ref * (0.55f + 0.30f * sensitivity) // hassasiyet arttıkça daha çok yer (gölge dahil) "el" sayılır
        val maxRed = (22 - 12 * sensitivity).toInt()     // ten rengi kırmızımsıdır (Cr yüksek)
        // Kilitliyken (ilerleme sayılırken) el her zaman bulunur: elin altındaki çizgiler silinmiş sanılmasın.
        // Maskede ise el sadece "El önde" açıkken kullanılır.
        if (handFront || detectInk) {
            for (k in 0 until n) {
                val paper = yAvg[k] >= minY && (vs[k] - 128) < maxRed && abs(us[k] - 128) < 30
                handRaw[k] = !paper
            }
            // İnce şeyleri (kalem çizgisi) at: hücrenin ve komşularının çoğu "el" olmalı
            for (j in 0 until h) for (i in 0 until w) {
                val k = j * w + i
                if (!handRaw[k]) { handCore[k] = false; continue }
                var cnt = 0
                for (dj in -1..1) for (di in -1..1) {
                    val x = i + di
                    val y = j + dj
                    if (x < 0 || y < 0 || x >= w || y >= h) { cnt++; continue } // kenar dışı el sayılır
                    if (handRaw[y * w + x]) cnt++
                }
                handCore[k] = cnt >= 7
            }
            // Kalan eli 2 hücre genişlet (parmak kenarları, kalem ucu)
            dilate(handCore, hand, w, h, 2)
        } else {
            hand.fill(false)
        }

        // --- 3) Çizdiğin çizgiler: yerel kâğıttan belirgin koyu, renksiz ince izler ---
        if (hideDrawn || detectInk) {
            // Yerel kâğıt parlaklığı = çevredeki en parlak hücre (köşelerdeki kararmayı telafi eder)
            boxMax(yAvg, local, w, h, LOCAL_R)
            // Hassasiyet arttıkça eşik düşer: sadece koyu çizgiler sayılır. 0'da açık kalem izleri de yakalanır (önerilen)
            val k1 = 0.84f - 0.14f * sensitivity
            for (k in 0 until n) {
                val neutral = abs(vs[k] - 128) < 14 && abs(us[k] - 128) < 18
                ink[k] = !hand[k] && neutral && local[k] > 40 && yMin[k] < local[k] * k1
            }
            // Gürültü tek hücre: en az bir komşusu da çizgi olsun (çizgiler süreklidir)
            for (j in 0 until h) for (i in 0 until w) {
                val k = j * w + i
                if (!ink[k]) { handRaw[k] = false; continue }
                var nb = false
                loop@ for (dj in -1..1) for (di in -1..1) {
                    if (di == 0 && dj == 0) continue
                    val x = i + di
                    val y = j + dj
                    if (x < 0 || y < 0 || x >= w || y >= h) continue
                    if (ink[y * w + x]) { nb = true; break@loop }
                }
                handRaw[k] = nb // handRaw'ı geçici dizi olarak kullan
            }
            // Kılavuz çizgisi tam üstüne denk gelmese de gizlensin diye 1 hücre genişlet
            dilate(handRaw, ink, w, h, 1)
        } else {
            ink.fill(false)
        }

        // --- 4) Maske + titremesin diye yumuşatma ---
        val mask = if (handFront || hideDrawn) {
            for (k in 0 until n) {
                val visible = !((handFront && hand[k]) || (hideDrawn && ink[k]))
                val v = prev[k] * 0.35f + (if (visible) 0.65f else 0f)
                prev[k] = v
                alpha[k] = (v * 255).toInt()
            }
            // Sadece görünürlük tutan küçük bitmap (ALPHA_8): renkliye göre 4 kat küçük, çöp azalır.
            // Her seferinde yenisi: eskisi o an ekranda çiziliyor olabilir.
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
            val row = bmp.rowBytes
            if (alphas.size != row * h) alphas = ByteArray(row * h)
            for (j in 0 until h) {
                val o = j * row
                val i0 = j * w
                for (i in 0 until w) alphas[o + i] = alpha[i0 + i].toByte()
            }
            bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(alphas))
            bmp
        } else {
            prev.fill(1f)
            null
        }
        return MaskResult(
            mask,
            if (detectInk) ink.copyOf() else null,
            if (detectInk) hand.copyOf() else null,
            w, h
        )
    }

    /** src'deki işaretli hücreleri r hücre büyütüp dst'ye yazar (önce yatay, sonra dikey) */
    private fun dilate(src: BooleanArray, dst: BooleanArray, w: Int, h: Int, r: Int) {
        val row = BooleanArray(w * h)
        for (j in 0 until h) for (i in 0 until w) {
            var on = false
            for (d in -r..r) {
                val x = i + d
                if (x in 0 until w && src[j * w + x]) { on = true; break }
            }
            row[j * w + i] = on
        }
        for (j in 0 until h) for (i in 0 until w) {
            var on = false
            for (d in -r..r) {
                val y = j + d
                if (y in 0 until h && row[y * w + i]) { on = true; break }
            }
            dst[j * w + i] = on
        }
    }

    /** Her hücre için (2r+1)x(2r+1) komşulukta en büyük değer */
    private fun boxMax(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        for (j in 0 until h) for (i in 0 until w) {
            var mx = 0
            for (d in -r..r) {
                val x = (i + d).coerceIn(0, w - 1)
                val v = src[j * w + x]
                if (v > mx) mx = v
            }
            tmp[j * w + i] = mx
        }
        for (j in 0 until h) for (i in 0 until w) {
            var mx = 0
            for (d in -r..r) {
                val y = (j + d).coerceIn(0, h - 1)
                val v = tmp[y * w + i]
                if (v > mx) mx = v
            }
            dst[j * w + i] = mx
        }
    }

    private fun prepare(m: ViewMapper, image: ImageProxy, viewW: Int, viewH: Int) {
        mapper = m
        val yP = image.planes[0]
        val uP = image.planes[1]
        val vP = image.planes[2]
        yRowStride = yP.rowStride
        yPixStride = yP.pixelStride
        mw = MASK_W
        mh = maskRows(viewW, viewH)
        val n = mw * mh
        yIdx = IntArray(n * SUB * SUB)
        cIdxU = IntArray(n); cIdxV = IntArray(n)
        yAvg = IntArray(n); yMin = IntArray(n); us = IntArray(n); vs = IntArray(n)
        tmp = IntArray(n); local = IntArray(n)
        handRaw = BooleanArray(n); handCore = BooleanArray(n); hand = BooleanArray(n); ink = BooleanArray(n)
        prev = FloatArray(n) { 1f }
        alpha = IntArray(n)
        alphas = ByteArray(0) // ilk maskede bitmap'in satır uzunluğuna göre ayrılır
        val maxX = image.width - 1
        val maxY = image.height - 1
        val cellW = viewW.toFloat() / mw
        val cellH = viewH.toFloat() / mh
        for (j in 0 until mh) for (i in 0 until mw) {
            val k = j * mw + i
            // Hücre içindeki SUB x SUB nokta
            for (b in 0 until SUB) for (a in 0 until SUB) {
                val vx = (i + (a + 0.5f) / SUB) * cellW
                val vy = (j + (b + 0.5f) / SUB) * cellH
                val ux = m.viewToUprightX(vx)
                val uy = m.viewToUprightY(vy)
                val px = m.uprightToBufferX(ux, uy).toInt().coerceIn(0, maxX)
                val py = m.uprightToBufferY(ux, uy).toInt().coerceIn(0, maxY)
                yIdx[k * SUB * SUB + b * SUB + a] = py * yP.rowStride + px * yP.pixelStride
            }
            // Renk: hücre ortası
            val ux = m.viewToUprightX((i + 0.5f) * cellW)
            val uy = m.viewToUprightY((j + 0.5f) * cellH)
            val cx = (m.uprightToBufferX(ux, uy).toInt().coerceIn(0, maxX)) shr 1
            val cy = (m.uprightToBufferY(ux, uy).toInt().coerceIn(0, maxY)) shr 1
            cIdxU[k] = cy * uP.rowStride + cx * uP.pixelStride
            cIdxV[k] = cy * vP.rowStride + cx * vP.pixelStride
        }
    }
}

/**
 * Kamera karelerini arka planda işleyen sınıf. Ayarları ekran tarafı yazar (@Volatile),
 * sonuçları ana iş parçacığına geri yollar.
 */
class FrameAnalyzer(
    private val onResult: (MaskResult?) -> Unit
) : ImageAnalysis.Analyzer {
    @Volatile var viewW = 0
    @Volatile var viewH = 0
    @Volatile var handFront = true
    @Volatile var hideDrawn = false
    @Volatile var detectInk = false   // ilerleme yüzdesi için çizilen yerleri bul
    @Volatile var sensitivity = SENSITIVITY_RECOMMENDED

    /**
     * Hızlandırılmış video için kare alıcı. null değilse her frameIntervalMs'de bir,
     * kameranın gördüğü kâğıt (kılavuz olmadan) küçük bir resim olarak ana iş parçacığına verilir.
     * Kare burada, arka planda üretilir: ekranı hiç bekletmez.
     */
    @Volatile var frameSink: ((Bitmap) -> Unit)? = null
    @Volatile var frameIntervalMs = 1200L

    private val main = Handler(Looper.getMainLooper())
    private val masker = TraceMasker()
    private var mapper: ViewMapper? = null
    private var frame = 0L
    private var wasMasking = false
    private var lastFrameAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val vw = viewW
            val vh = viewH
            if (vw <= 0 || vh <= 0) return
            val crop = image.cropRect
            val rot = image.imageInfo.rotationDegrees
            val m = mapper?.takeIf { it.matches(crop, rot, vw, vh) }
                ?: ViewMapper(crop, rot, vw, vh).also { mapper = it }

            // Video karesi (kayıt sürerken)
            val sink = frameSink
            if (sink == null) {
                lastFrameAt = 0L
            } else {
                val now = SystemClock.elapsedRealtime()
                if (lastFrameAt == 0L || now - lastFrameAt >= frameIntervalMs) {
                    lastFrameAt = now
                    val bmp = uprightFrame(image, m, VIDEO_FRAME_W)
                    main.post { sink(bmp) }
                    return // bu karede maskeyi atla, işlemci yorulmasın
                }
            }

            val hf = handFront
            val hd = hideDrawn
            val di = detectInk
            if (!hf && !hd && !di) {
                if (wasMasking) {
                    wasMasking = false
                    main.post { onResult(null) }
                }
                return
            }
            frame++
            // Maske her 2 karede bir (yaklaşık saniyede 15 kez)
            if (frame % 2 != 0L) return
            val result = masker.build(image, m, vw, vh, hf, hd, di, sensitivity)
            wasMasking = true
            main.post { onResult(result) }
        } catch (_: Throwable) {
            // Bir karede hata olursa sadece o kare atlanır
        } finally {
            image.close()
        }
    }
}

private const val VIDEO_FRAME_W = 720

/**
 * Kamera karesini (YUV) ekranda görünen kısmıyla, dik ve renkli küçük bir resme çevirir.
 * Genişlik en fazla maxW; oran ekrandakiyle aynı.
 */
private fun uprightFrame(image: ImageProxy, m: ViewMapper, maxW: Int): Bitmap {
    val w = min(maxW, m.uprightW.toInt()).coerceAtLeast(2)
    val h = (w * m.uprightH / m.uprightW).toInt().coerceAtLeast(2)
    val yP = image.planes[0]
    val uP = image.planes[1]
    val vP = image.planes[2]
    val yb = yP.buffer
    val ub = uP.buffer
    val vb = vP.buffer
    val yRow = yP.rowStride; val yPix = yP.pixelStride
    val uRow = uP.rowStride; val uPix = uP.pixelStride
    val vRow = vP.rowStride; val vPix = vP.pixelStride
    val maxX = image.width - 1
    val maxY = image.height - 1
    val sx = m.uprightW / w
    val sy = m.uprightH / h
    val out = IntArray(w * h)
    for (j in 0 until h) {
        val uy = (j + 0.5f) * sy
        for (i in 0 until w) {
            val ux = (i + 0.5f) * sx
            val bx = m.uprightToBufferX(ux, uy).toInt().coerceIn(0, maxX)
            val by = m.uprightToBufferY(ux, uy).toInt().coerceIn(0, maxY)
            val y = yb.get(by * yRow + bx * yPix).toInt() and 0xFF
            val cx = bx shr 1
            val cy = by shr 1
            val u = (ub.get(cy * uRow + cx * uPix).toInt() and 0xFF) - 128
            val v = (vb.get(cy * vRow + cx * vPix).toInt() and 0xFF) - 128
            // YUV -> RGB (kamera görüntüsü tam aralık, BT.601)
            val r = (y + ((1436 * v) shr 10)).coerceIn(0, 255)
            val g = (y - ((352 * u + 731 * v) shr 10)).coerceIn(0, 255)
            val b = (y + ((1815 * u) shr 10)).coerceIn(0, 255)
            out[j * w + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

// ---------------------------------------------------------------------------------------------
// Geometri
// ---------------------------------------------------------------------------------------------

/**
 * Çizim resmini ekrandaki 4 köşeye götüren dönüşüm. mirror: yatay aynalama.
 * Çizim katmanı ve ilerleme hesabı aynı dönüşümü kullanır.
 */
fun drawingMatrix(bmpW: Int, bmpH: Int, dst: FloatArray, mirror: Boolean, out: android.graphics.Matrix): Boolean {
    val w = bmpW.toFloat()
    val h = bmpH.toFloat()
    val src = if (mirror) floatArrayOf(w, 0f, 0f, 0f, 0f, h, w, h)
    else floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
    return out.setPolyToPoly(src, 0, dst, 0, 4)
}

/**
 * Kılavuz çizgilerinin maske ızgarasında kapladığı hücreler (ilerleme yüzdesi için).
 * Çizimdeki mürekkep noktaları ekrana taşınır ve düştükleri hücre işaretlenir.
 */
fun guideCells(art: Bitmap, corners: FloatArray, mirror: Boolean, viewW: Int, viewH: Int): BooleanArray? {
    if (viewW <= 0 || viewH <= 0) return null
    val cols = MASK_W
    val rows = maskRows(viewW, viewH)
    val m = android.graphics.Matrix()
    if (!drawingMatrix(art.width, art.height, corners, mirror, m)) return null
    val w = art.width
    val h = art.height
    val px = IntArray(w * h)
    art.getPixels(px, 0, w, 0, 0, w, h)
    val out = BooleanArray(cols * rows)
    val pt = FloatArray(2)
    val sx = cols.toFloat() / viewW
    val sy = rows.toFloat() / viewH
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            if ((px[y * w + x] ushr 24) > 110) {
                pt[0] = x + 0.5f; pt[1] = y + 0.5f
                m.mapPoints(pt)
                val cx = (pt[0] * sx).toInt()
                val cy = (pt[1] * sy).toInt()
                if (cx in 0 until cols && cy in 0 until rows) out[cy * cols + cx] = true
            }
            x += 2
        }
        y += 2
    }
    return out
}

/** Ortası (cx, cy), genişliği w, yüksekliği h olan ve deg derece döndürülmüş dikdörtgenin köşeleri */
fun rotatedRect(cx: Float, cy: Float, w: Float, h: Float, deg: Float): FloatArray {
    val r = Math.toRadians(deg.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    val hw = w / 2f
    val hh = h / 2f
    val local = floatArrayOf(-hw, -hh, hw, -hh, hw, hh, -hw, hh)
    val out = FloatArray(8)
    for (i in 0 until 4) {
        val x = local[i * 2]
        val y = local[i * 2 + 1]
        out[i * 2] = cx + x * c - y * s
        out[i * 2 + 1] = cy + x * s + y * c
    }
    return out
}
