package com.alterationstudio.ilkcizgi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

/*
 * FOTOĞRAFTAN ÇİZİM
 * Seçilen fotoğraf telefonda (internete gönderilmeden) çizgi çizimine çevrilir.
 * Yöntem akış tabanlı DoG'dur (FDoG/XDoG ailesi): fotoğraf kenarları koruyarak düzlenir,
 * iki Gauss farkı koyu ince yerleri bulur, çizgiler kenar yönü boyunca birleştirilir,
 * güçlü çizgiye bağlı parçalar tutulur. Ayrıntılar lineArtPixels() açıklamasında.
 * Hiçbir ek kütüphane kullanmaz; önizleme 640, kayıt 1200 piksel üstünde yapılır.
 * Hız: ağır adımlar telefonun çekirdeklerine bölünür, çalışma dizileri yeniden kullanılır, önizlemeler
 * birer birer hesaplanır (ayar kaydırılırken eski hesap hemen bırakılır).
 */

private const val PREVIEW_SIDE = 640
private const val FINAL_SIDE = 1200

/** Fotoğrafı açar, telefonun çektiği yöne göre çevirir */
private fun decodeUri(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    val cr = context.contentResolver
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
    var s = 1
    while (max(b.outWidth, b.outHeight) / (s * 2) >= maxSide) s *= 2
    val bmp = cr.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
    } ?: return null
    val deg = runCatching {
        cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)
    if (deg == 0) bmp
    else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
        .also { if (it !== bmp) bmp.recycle() }
}.getOrNull()

// ---------------- ÇİZGİ ÇEKİRDEĞİ (yalnız sayı dizileriyle çalışır) ----------------

/**
 * Bir çizim hesabının bütün çalışma dizileri. Her önizlemede yeniden ayrılmasın diye boyuta göre saklanır
 * (LineArtBufferCache): kaydırıcı oynarken onlarca MB çöp birikip çöp toplayıcı ekranı takılmasın.
 */
internal class LineArtBuffers(val w: Int, val h: Int) {
    val n = w * h
    val px = IntArray(n)        // giriş pikselleri
    val out = IntArray(n)       // çıkış pikselleri
    val g = FloatArray(n)       // gri → düzlenmiş
    val a = FloatArray(n)       // DoG yanıtı (e)
    val b = FloatArray(n)       // ara dizi / akış sonucu
    val t1 = FloatArray(n)      // ayrılabilir bulanıklık ara dizileri
    val t2 = FloatArray(n)
    val jxx = FloatArray(n)     // yapı tensörü; sonra kenar yönü (tx = jxx, ty = jyy)
    val jxy = FloatArray(n)
    val jyy = FloatArray(n)
    val sumsA = DoubleArray(w)  // dikey kutu bulanıklığı sütun toplamları
    val sumsB = DoubleArray(w)
    val seen = BooleanArray(n)
    val keep = BooleanArray(n)
    val queue = IntArray(n)
}

/** Son kullanılan birkaç boyutun dizileri (önizleme ve kayıt boyutu); bellek darlığında Android geri alabilir */
internal object LineArtBufferCache {
    private val slots = ArrayList<java.lang.ref.SoftReference<LineArtBuffers>>()

    @Synchronized
    fun take(w: Int, h: Int): LineArtBuffers {
        val it = slots.iterator()
        while (it.hasNext()) {
            val b = it.next().get()
            if (b == null) { it.remove(); continue }
            if (b.w == w && b.h == h) { it.remove(); return b }
        }
        return LineArtBuffers(w, h)
    }

    @Synchronized
    fun give(b: LineArtBuffers) {
        slots.add(0, java.lang.ref.SoftReference(b))
        while (slots.size > 2) slots.removeAt(slots.lastIndex)
    }
}

/**
 * Ağır döngüleri telefonun çekirdeklerine böler (en fazla 4 parça). Her parça kendi satır/sütun aralığına
 * yazar, okuduğu dizilere o adımda kimse yazmaz: sonuç tek çekirdekteki ile birebir aynıdır.
 */
private object LineArtThreads {
    private val count = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    private val pool: java.util.concurrent.ExecutorService? by lazy {
        if (count < 2) null
        else java.util.concurrent.Executors.newFixedThreadPool(count - 1) { r ->
            Thread(r, "cizgi-hesabi").apply { isDaemon = true }
        }
    }

    /** 0 until total aralığını parçalara böler; ilk parçayı çağıran iş parçacığı yapar */
    fun split(total: Int, body: (from: Int, to: Int) -> Unit) {
        val p = if (total >= 64) pool else null
        if (p == null) { body(0, total); return }
        val step = (total + count - 1) / count
        val futures = ArrayList<java.util.concurrent.Future<*>>(count)
        var s = step
        while (s < total) {
            val from = s
            val to = minOf(total, s + step)
            futures.add(p.submit { body(from, to) })
            s += step
        }
        body(0, minOf(step, total))
        for (f in futures) {
            try { f.get() } catch (e: java.util.concurrent.ExecutionException) { throw e.cause ?: e }
        }
    }
}

/** Gauss çekirdeğinin yarısı: k[0] merkez, k[t] hem -t hem +t için (toplamı 1) */
private fun gaussHalfKernel(sigma: Float): FloatArray {
    val r = max(1, (3f * sigma + 0.5f).toInt())
    val k = FloatArray(r + 1) { exp(-(it * it) / (2f * sigma * sigma)) }
    var s = k[0]
    for (t in 1..r) s += 2f * k[t]
    for (t in 0..r) k[t] /= s
    return k
}

/** Yatay kutu bulanıklığı (kenarda en yakın piksel), satır aralığı [y0, y1). Toplamlar Double: kayma olmasın */
private fun boxH(src: FloatArray, dst: FloatArray, w: Int, r: Int, y0: Int, y1: Int) {
    val inv = 1.0 / (2 * r + 1)
    for (y in y0 until y1) {
        val row = y * w
        var s = 0.0
        for (t in -r..r) s += src[row + (if (t < 0) 0 else if (t >= w) w - 1 else t)]
        for (x in 0 until w) {
            dst[row + x] = (s * inv).toFloat()
            val ad = x + r + 1
            val su = x - r
            s += src[row + (if (ad >= w) w - 1 else ad)] - src[row + (if (su < 0) 0 else su)]
        }
    }
}

/** Dikey kutu bulanıklığı, sütun aralığı [x0, x1). Satır satır ilerler (önbelleğe uygun) */
private fun boxV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int, x0: Int, x1: Int, sums: DoubleArray) {
    val inv = 1.0 / (2 * r + 1)
    for (x in x0 until x1) sums[x] = 0.0
    for (t in -r..r) {
        val row = (if (t < 0) 0 else if (t >= h) h - 1 else t) * w
        for (x in x0 until x1) sums[x] += src[row + x]
    }
    for (y in 0 until h) {
        val row = y * w
        val add = min(y + r + 1, h - 1) * w
        val sub = max(y - r, 0) * w
        for (x in x0 until x1) {
            dst[row + x] = (sums[x] * inv).toFloat()
            sums[x] += src[add + x] - src[sub + x]
        }
    }
}

/** Kutu bulanıklığı (yatay + dikey); tmp ara dizi. src ile dst aynı olabilir */
private fun box(src: FloatArray, dst: FloatArray, tmp: FloatArray, w: Int, h: Int, r: Int, sums: DoubleArray) {
    LineArtThreads.split(h) { y0, y1 -> boxH(src, tmp, w, r, y0, y1) }
    LineArtThreads.split(w) { x0, x1 -> boxV(tmp, dst, w, h, r, x0, x1, sums) }
}

/** İki diziye aynı anda yatay kutu bulanıklığı (tek döngü: döngü yükü yarıya iner) */
private fun boxH2(srcA: FloatArray, srcB: FloatArray, dstA: FloatArray, dstB: FloatArray, w: Int, r: Int, y0: Int, y1: Int) {
    val inv = 1.0 / (2 * r + 1)
    for (y in y0 until y1) {
        val row = y * w
        var sa = 0.0
        var sb = 0.0
        for (t in -r..r) {
            val i = row + (if (t < 0) 0 else if (t >= w) w - 1 else t)
            sa += srcA[i]
            sb += srcB[i]
        }
        for (x in 0 until w) {
            dstA[row + x] = (sa * inv).toFloat()
            dstB[row + x] = (sb * inv).toFloat()
            val ad = x + r + 1
            val su = x - r
            val ia = row + (if (ad >= w) w - 1 else ad)
            val iu = row + (if (su < 0) 0 else su)
            sa += srcA[ia] - srcA[iu]
            sb += srcB[ia] - srcB[iu]
        }
    }
}

/** İki diziye aynı anda dikey kutu bulanıklığı, sütun aralığı [x0, x1) */
private fun boxV2(
    srcA: FloatArray, srcB: FloatArray, dstA: FloatArray, dstB: FloatArray,
    w: Int, h: Int, r: Int, x0: Int, x1: Int, sumsA: DoubleArray, sumsB: DoubleArray
) {
    val inv = 1.0 / (2 * r + 1)
    for (x in x0 until x1) { sumsA[x] = 0.0; sumsB[x] = 0.0 }
    for (t in -r..r) {
        val row = (if (t < 0) 0 else if (t >= h) h - 1 else t) * w
        for (x in x0 until x1) { sumsA[x] += srcA[row + x]; sumsB[x] += srcB[row + x] }
    }
    for (y in 0 until h) {
        val row = y * w
        val add = min(y + r + 1, h - 1) * w
        val sub = max(y - r, 0) * w
        for (x in x0 until x1) {
            dstA[row + x] = (sumsA[x] * inv).toFloat()
            dstB[row + x] = (sumsB[x] * inv).toFloat()
            sumsA[x] += srcA[add + x] - srcA[sub + x]
            sumsB[x] += srcB[add + x] - srcB[sub + x]
        }
    }
}

/** İki diziye kutu bulanıklığı (srcA → dstA, srcB → dstB; kaynak ve hedef aynı olabilir); ta, tb ara diziler */
private fun box2(
    srcA: FloatArray, srcB: FloatArray, dstA: FloatArray, dstB: FloatArray,
    ta: FloatArray, tb: FloatArray, buf: LineArtBuffers, r: Int
) {
    val w = buf.w
    val h = buf.h
    LineArtThreads.split(h) { y0, y1 -> boxH2(srcA, srcB, ta, tb, w, r, y0, y1) }
    LineArtThreads.split(w) { x0, x1 -> boxV2(ta, tb, dstA, dstB, w, h, r, x0, x1, buf.sumsA, buf.sumsB) }
}

/**
 * Üç diziye aynı anda Gauss bulanıklığı, yerinde (yapı tensörü için: üç dizi tek döngüde).
 * ta, tb, tc ara diziler.
 */
private fun gauss3InPlace(
    a: FloatArray, b: FloatArray, c: FloatArray, ta: FloatArray, tb: FloatArray, tc: FloatArray,
    w: Int, h: Int, sigma: Float
) {
    val k = gaussHalfKernel(sigma)
    val r = k.size - 1
    val k0 = k[0]
    LineArtThreads.split(h) { y0, y1 ->
        for (y in y0 until y1) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                var sa = k0 * a[i]
                var sb = k0 * b[i]
                var sc = k0 * c[i]
                if (x >= r && x < w - r) {
                    for (t in 1..r) {
                        val kt = k[t]
                        sa += kt * (a[i - t] + a[i + t])
                        sb += kt * (b[i - t] + b[i + t])
                        sc += kt * (c[i - t] + c[i + t])
                    }
                } else {
                    for (t in 1..r) {
                        val kt = k[t]
                        val l = row + (if (x - t < 0) 0 else x - t)
                        val rr = row + (if (x + t >= w) w - 1 else x + t)
                        sa += kt * (a[l] + a[rr])
                        sb += kt * (b[l] + b[rr])
                        sc += kt * (c[l] + c[rr])
                    }
                }
                ta[i] = sa
                tb[i] = sb
                tc[i] = sc
            }
        }
    }
    LineArtThreads.split(h) { y0, y1 ->
        for (y in y0 until y1) {
            val row = y * w
            val inside = y >= r && y < h - r
            for (x in 0 until w) {
                val i = row + x
                var sa = k0 * ta[i]
                var sb = k0 * tb[i]
                var sc = k0 * tc[i]
                if (inside) {
                    var off = w
                    for (t in 1..r) {
                        val kt = k[t]
                        sa += kt * (ta[i - off] + ta[i + off])
                        sb += kt * (tb[i - off] + tb[i + off])
                        sc += kt * (tc[i - off] + tc[i + off])
                        off += w
                    }
                } else {
                    for (t in 1..r) {
                        val kt = k[t]
                        val u = (if (y - t < 0) 0 else y - t) * w + x
                        val d = (if (y + t >= h) h - 1 else y + t) * w + x
                        sa += kt * (ta[u] + ta[d])
                        sb += kt * (tb[u] + tb[d])
                        sc += kt * (tc[u] + tc[d])
                    }
                }
                a[i] = sa
                b[i] = sb
                c[i] = sc
            }
        }
    }
}

/**
 * DoG: e = Gauss(geniş) − Gauss(dar); iki bulanıklık aynı döngülerde (yarı yarıya az geçiş).
 * kf dar, kc geniş yarım çekirdek (kc en az kf kadar uzun).
 */
private fun dog(src: FloatArray, e: FloatArray, tf: FloatArray, tc: FloatArray, w: Int, h: Int, kf: FloatArray, kc: FloatArray) {
    val rf = kf.size - 1
    val rc = kc.size - 1
    LineArtThreads.split(h) { y0, y1 ->
        for (y in y0 until y1) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                val v = src[i]
                var f = kf[0] * v
                var c = kc[0] * v
                if (x >= rc && x < w - rc) {
                    for (t in 1..rf) { val s = src[i - t] + src[i + t]; f += kf[t] * s; c += kc[t] * s }
                    for (t in rf + 1..rc) c += kc[t] * (src[i - t] + src[i + t])
                } else {
                    for (t in 1..rc) {
                        val l = x - t
                        val rr = x + t
                        val s = src[row + (if (l < 0) 0 else l)] + src[row + (if (rr >= w) w - 1 else rr)]
                        if (t <= rf) f += kf[t] * s
                        c += kc[t] * s
                    }
                }
                tf[i] = f
                tc[i] = c
            }
        }
    }
    LineArtThreads.split(h) { y0, y1 ->
        for (y in y0 until y1) {
            val row = y * w
            if (y >= rc && y < h - rc) {
                for (x in 0 until w) {
                    val i = row + x
                    var f = kf[0] * tf[i]
                    var c = kc[0] * tc[i]
                    var off = w
                    for (t in 1..rf) { f += kf[t] * (tf[i - off] + tf[i + off]); c += kc[t] * (tc[i - off] + tc[i + off]); off += w }
                    for (t in rf + 1..rc) { c += kc[t] * (tc[i - off] + tc[i + off]); off += w }
                    e[i] = c - f
                }
            } else {
                for (x in 0 until w) {
                    val i = row + x
                    var f = kf[0] * tf[i]
                    var c = kc[0] * tc[i]
                    for (t in 1..rc) {
                        val u = (if (y - t < 0) 0 else y - t) * w + x
                        val d = (if (y + t >= h) h - 1 else y + t) * w + x
                        if (t <= rf) f += kf[t] * (tf[u] + tf[d])
                        c += kc[t] * (tc[u] + tc[d])
                    }
                    e[i] = c - f
                }
            }
        }
    }
}

/**
 * Yönlendirmeli süzgeç (He) yerinde: img düzlenmiş hâliyle değişir.
 * m, m2: ortalama ve kare ortalaması (sonra a, b); ta, tb ara diziler.
 */
private fun guidedInPlace(img: FloatArray, m: FloatArray, m2: FloatArray, ta: FloatArray, tb: FloatArray, buf: LineArtBuffers, r: Int, eps: Float) {
    val n = buf.n
    LineArtThreads.split(n) { i0, i1 -> for (i in i0 until i1) { val v = img[i]; m2[i] = v * v } }
    box2(img, m2, m, m2, ta, tb, buf, r)
    LineArtThreads.split(n) { i0, i1 ->
        for (i in i0 until i1) {
            val mm = m[i]
            val v = (m2[i] - mm * mm).coerceAtLeast(0f)
            val a = v / (v + eps)
            m[i] = a
            m2[i] = mm - a * mm
        }
    }
    box2(m, m2, m, m2, ta, tb, buf, r)
    LineArtThreads.split(n) { i0, i1 -> for (i in i0 until i1) img[i] = m[i] * img[i] + m2[i] }
}

/** Histogramdan yüzdelik değer (0..255) */
private fun percentile(hist: IntArray, n: Int, p: Float): Float {
    val target = p * (n - 1)
    var acc = 0
    for (v in 0..255) {
        acc += hist[v]
        if (acc > target) return v.toFloat()
    }
    return 255f
}

/**
 * Fotoğraf pikselleri (buf.px) → çizgi çizimi pikselleri (buf.out döner; beyaz zemin, koyu çizgi).
 * Yöntem (akış tabanlı DoG, Kang 2007 / Winnemöller 2012 çizgisinde):
 *  1. Gri ton + karşıtlık germe (en koyu %1 → siyah, en açık %1 → beyaz)
 *  2. İki kez yönlendirmeli süzgeç: kenarlar keskin kalır, doku silinir
 *  3. DoG: çevresinden koyu ince yerler (iki Gauss farkı, oran 1.6)
 *  4. Yerel karşıtlığa bölme: gölgedeki kenar da parlak yerdeki kadar güçlü çıkar
 *  5. Kenar akışı boyunca ortalama: kesik kesik çizgiler birleşir, tek tük noktalar söner
 *  6. Histerezis: güçlü bir çizgiye bağlı zayıf parçalar alınır, kopuk zayıflar atılır; küçük lekeler temizlenir
 *  7. Koyu, kenarı yumuşak mürekkep
 * Hız: ağır adımlar çekirdeklere bölünür, aynı bulanıklığı isteyen diziler tek döngüde işlenir, düz alanlar
 * atlanır, diziler yeniden kullanılır. Sonuç tek çekirdekli, her şeyi hesaplayan hâliyle birebir aynıdır.
 * detail: 0 sade … 1 çok ayrıntılı, thickness: 0 ince … 1 kalın.
 * check(): aşamalar arasında çağrılır; iptal için istisna atabilir (çağıran iş parçacığında).
 */
internal fun lineArtPixels(buf: LineArtBuffers, detail: Float, thickness: Float, check: () -> Unit): IntArray {
    val w = buf.w
    val h = buf.h
    val n = buf.n
    val px = buf.px
    val g = buf.g
    val e = buf.a
    val k = max(w, h) / 1000f // ölçek: her çözünürlükte aynı görünsün diye 1000 piksele göre

    // 1) Gri + karşıtlık germe
    val hist = IntArray(256)
    for (i in 0 until n) {
        val c = px[i]
        val v = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        hist[v]++
        g[i] = v.toFloat()
    }
    val lo0 = percentile(hist, n, 0.01f)
    val hi0 = percentile(hist, n, 0.99f)
    val stretch = 255f / max(hi0 - lo0, 1f)
    LineArtThreads.split(n) { i0, i1 -> for (i in i0 until i1) g[i] = ((g[i] - lo0) * stretch).coerceIn(0f, 255f) }
    check()

    // 2) Kenarı koruyan düzleme (sade ayarda daha güçlü)
    val epsRoot = 14f + 34f * (1f - detail)
    val gr = max(1, (3f * k).roundToInt())
    guidedInPlace(g, e, buf.b, buf.t1, buf.t2, buf, gr, epsRoot * epsRoot)
    check()
    guidedInPlace(g, e, buf.b, buf.t1, buf.t2, buf, gr, epsRoot * epsRoot)
    check()

    // 3) DoG: e = geniş bulanık − dar bulanık (koyu çizgide artı)
    val sigma = (0.9f + 1.6f * thickness) * k
    dog(g, e, buf.t1, buf.t2, w, h, gaussHalfKernel(sigma), gaussHalfKernel(sigma * 1.6f))
    check()

    // 4) Yerel karşıtlığa göre normalleştir
    val local = buf.b
    LineArtThreads.split(n) { i0, i1 -> for (i in i0 until i1) local[i] = abs(e[i]) }
    val lr = max(1, (12f * k - 0.5f).roundToInt())
    repeat(3) { box(local, local, buf.t1, w, h, lr, buf.sumsA) }
    LineArtThreads.split(n) { i0, i1 -> for (i in i0 until i1) e[i] /= local[i] + 1.5f }
    check()

    // Akış ve eşik ayarları (6. adımın alt eşiği 5. adımda da gerekiyor)
    val steps = max(2, (10f * k).roundToInt())
    val hiT = 1.3f - 0.7f * detail
    val loT = hiT * 0.45f
    // Hız: resim 8×8 karelere bölünür. Akış yolu en fazla `steps` piksel gider: çevresinde e > loT olan
    // hiç değer yoksa ortalama da loT'u geçemez, o pikseller hesaplanmaz (sonuç aynı, iş çok azalır).
    val tile = 8
    val tw = (w + tile - 1) / tile
    val tht = (h + tile - 1) / tile
    val tileMax = FloatArray(tw * tht) { -Float.MAX_VALUE }
    for (y in 0 until h) {
        val trow = (y / tile) * tw
        val row = y * w
        for (x in 0 until w) {
            val t = trow + x / tile
            val v = e[row + x]
            if (v > tileMax[t]) tileMax[t] = v
        }
    }
    val reach = (steps + tile - 1) / tile
    // active: bu karede akış hesaplanacak; needDir: kenar yönü gereken kareler (aktif karelerden yolun gidebildiği yer)
    val active = BooleanArray(tw * tht)
    val needDir = BooleanArray(tw * tht)
    for (ty0 in 0 until tht) for (tx0 in 0 until tw) {
        var m = -Float.MAX_VALUE
        for (ty1 in max(0, ty0 - reach)..min(tht - 1, ty0 + reach))
            for (tx1 in max(0, tx0 - reach)..min(tw - 1, tx0 + reach))
                m = max(m, tileMax[ty1 * tw + tx1])
        if (m > loT) {
            active[ty0 * tw + tx0] = true
            for (ty1 in max(0, ty0 - reach)..min(tht - 1, ty0 + reach))
                for (tx1 in max(0, tx0 - reach)..min(tw - 1, tx0 + reach))
                    needDir[ty1 * tw + tx1] = true
        }
    }

    // 5a) Kenar yönü: yapı tensörü (Sobel türevlerinden); baskın yönün dikine kenar boyunca yöndür
    val jxx = buf.jxx
    val jxy = buf.jxy
    val jyy = buf.jyy
    LineArtThreads.split(h) { yFrom, yTo ->
        for (y in yFrom until yTo) {
            val ym = max(y - 1, 0) * w
            val y0 = y * w
            val yp = min(y + 1, h - 1) * w
            for (x in 0 until w) {
                // içeride sınır denetimi gerekmez; kenarda en yakın piksel
                val xm = if (x > 0) x - 1 else 0
                val xp = if (x < w - 1) x + 1 else w - 1
                val gx = (g[ym + xp] + 2f * g[y0 + xp] + g[yp + xp]) - (g[ym + xm] + 2f * g[y0 + xm] + g[yp + xm])
                val gy = (g[yp + xm] + 2f * g[yp + x] + g[yp + xp]) - (g[ym + xm] + 2f * g[ym + x] + g[ym + xp])
                jxx[y0 + x] = gx * gx
                jxy[y0 + x] = gx * gy
                jyy[y0 + x] = gy * gy
            }
        }
    }
    check()
    gauss3InPlace(jxx, jxy, jyy, buf.t1, buf.t2, buf.b, w, h, 2.5f * k)
    check()
    // jxx ve jyy dizileri kenar yönü (tx, ty) için yeniden kullanılır; sadece gereken karelerde hesaplanır
    val tx = jxx
    val ty = jyy
    LineArtThreads.split(h) { yFrom, yTo ->
        for (y in yFrom until yTo) {
            val trow = (y / tile) * tw
            val row = y * w
            for (x in 0 until w) {
                if (!needDir[trow + x / tile]) continue
                val i = row + x
                // açı yerine yarım açı formülü: atan2/sin/cos'tan çok daha hızlı
                val a = jxx[i] - jyy[i]
                val b = 2f * jxy[i]
                val r = sqrt(a * a + b * b)
                if (r < 1e-6f) { tx[i] = 0f; ty[i] = 1f; continue }
                val c2 = a / r
                val s = sqrt(((1f - c2) * 0.5f).coerceAtLeast(0f))
                tx[i] = if (b < 0f) s else -s
                ty[i] = sqrt(((1f + c2) * 0.5f).coerceAtLeast(0f))
            }
        }
    }
    check()

    // 5b) Kenar akışı boyunca Gauss ağırlıklı ortalama
    val sm = max(1f, 5f * k)
    val wt = FloatArray(steps + 1) { exp(-(it * it) / (2f * sm * sm)) }
    // ağırlık toplamı her piksel için aynı (iki yön × steps adım); toplama sırası öncekiyle aynı
    var ws = 1f
    for (dir in 0..1) for (i in 1..steps) ws += wt[i]
    val en = buf.b
    // Satır bantları hâlinde: aralarda iptal denetlenir (ayar değişince eski hesap çabuk bırakılır)
    val band = 64
    var bandStart = 0
    while (bandStart < h) {
        val y0 = bandStart
        val bandEnd = min(h, y0 + band)
        LineArtThreads.split(bandEnd - y0) { from, to ->
            for (y in y0 + from until y0 + to) {
                val trow = (y / tile) * tw
                val row = y * w
                for (x in 0 until w) {
                    val p = row + x
                    if (!active[trow + x / tile]) { en[p] = e[p]; continue }
                    var acc = e[p]
                    for (dir in 0..1) {
                        var cx = x.toFloat()
                        var cy = y.toFloat()
                        var xi = x
                        var yi = y
                        var dx = if (dir == 0) tx[p] else -tx[p]
                        var dy = if (dir == 0) ty[p] else -ty[p]
                        for (i in 1..steps) {
                            // yön: bulunulan pikselin kenar yönü, ters dönmesin
                            val ti = yi * w + xi
                            val nx = tx[ti]
                            val ny = ty[ti]
                            if (nx * dx + ny * dy < 0f) { dx = -nx; dy = -ny } else { dx = nx; dy = ny }
                            cx += dx
                            cy += dy
                            // (v + 0.5).toInt(): eksi değerler zaten 0'a kırpılır
                            xi = (cx + 0.5f).toInt()
                            if (xi < 0) xi = 0 else if (xi >= w) xi = w - 1
                            yi = (cy + 0.5f).toInt()
                            if (yi < 0) yi = 0 else if (yi >= h) yi = h - 1
                            acc += wt[i] * e[yi * w + xi]
                        }
                    }
                    en[p] = acc / ws
                }
            }
        }
        check()
        bandStart = bandEnd
    }

    // 6) Histerezis + küçük leke temizliği (8 komşuluk)
    val minSize = ((12f + 50f * (1f - detail)) * k * k).roundToInt().coerceAtLeast(4)
    val seen = buf.seen
    val keep = buf.keep
    val queue = buf.queue
    seen.fill(false)
    keep.fill(false)
    for (start in 0 until n) {
        if (seen[start] || en[start] <= loT) continue
        var head = 0
        var tail = 0
        var strong = false
        queue[tail++] = start
        seen[start] = true
        while (head < tail) {
            val p = queue[head++]
            if (en[p] > hiT) strong = true
            val x = p % w
            val y = p / w
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val q = ny * w + nx
                if (!seen[q] && en[q] > loT) { seen[q] = true; queue[tail++] = q }
            }
        }
        if (strong && tail >= minSize) for (i in 0 until tail) keep[queue[i]] = true
    }
    check()

    // 7) Koyu, kenarı yumuşak mürekkep
    val out = buf.out
    LineArtThreads.split(n) { i0, i1 ->
        for (i in i0 until i1) {
            val ink = if (keep[i]) 0.5f + 0.5f * tanh((en[i] - loT).coerceAtLeast(0f) * 2.5f) else 0f
            val v = (255f - ink * 255f).toInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }
    return out
}

// ---------------- ÇİZGİ ÇEKİRDEĞİ SONU ----------------

/**
 * Fotoğrafı çizgi çizimine çevirir. Sonuç beyaz zemin üstünde koyu çizgilerdir.
 * detail: 0 sade … 1 çok ayrıntılı, thickness: 0 ince … 1 kalın
 */
fun sketchify(
    src: Bitmap, maxSide: Int, detail: Float, thickness: Float,
    cancelled: () -> Boolean = { false }
): Bitmap {
    // Ayar kaydırılırken eski hesap boşuna sürmesin: aşamalar arasında iptal kontrol edilir
    fun check() { if (cancelled()) throw kotlinx.coroutines.CancellationException("ayar değişti") }
    val scale = maxSide.toFloat() / max(src.width, src.height)
    val bmp = if (scale < 1f) Bitmap.createScaledBitmap(
        src, (src.width * scale).roundToInt().coerceAtLeast(1), (src.height * scale).roundToInt().coerceAtLeast(1), true
    ) else src
    val w = bmp.width
    val h = bmp.height
    // Çalışma dizileri bir önceki hesaptan (aynı boyuttaysa) yeniden kullanılır
    val buf = LineArtBufferCache.take(w, h)
    try {
        bmp.getPixels(buf.px, 0, w, 0, 0, w, h)
        if (bmp !== src) bmp.recycle() // küçültülmüş kopya artık gerekmiyor (asıl fotoğrafa dokunulmaz)
        check()
        val out = lineArtPixels(buf, detail, thickness) { check() }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    } finally {
        LineArtBufferCache.give(buf)
    }
}

/**
 * Önizleme hesapları sırayla, birer birer yapılır: ayar hızlı değişince eski hesap bitmeden (iptali fark
 * etmeden) yenisi başlamaz; işlemci ve bellek ikiye katlanmaz, ekran takılmaz.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val PreviewDispatcher = Dispatchers.Default.limitedParallelism(1)

@Composable
fun PhotoSketchScreen(onBack: () -> Unit, onCreated: (CatalogItem) -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val color = Ui.Rose
    val deep = Ui.deep(color, 0.42f)

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var detail by remember { mutableFloatStateOf(0.6f) }
    var thickness by remember { mutableFloatStateOf(0.35f) }
    // true: fotoğraf olduğu gibi kaydedilir; false: ana hatlarıyla çizgiye dönüştürülür
    var keepOriginal by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Süren önizleme hesabı sayısı (ana iş parçacığında artıp azalır): köşedeki küçük "hazırlanıyor" göstergesi
    var computing by remember { mutableIntStateOf(0) }

    fun loadFrom(uri: Uri?) {
        if (uri == null) return
        loading = true
        error = null
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeUri(context, uri, FINAL_SIDE + 200) }
            loading = false
            if (bmp == null) error = lang.pick("Fotoğraf açılamadı, başka birini dene.", "Couldn't open the photo, try another one.")
            source = bmp
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { loadFrom(it) }
    val captureFile = remember { File(context.cacheDir, "cekim.jpg") }
    val captureUri = remember { fileUri(context, captureFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) loadFrom(captureUri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(captureUri)
    }
    fun pickGallery() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun takePhoto() {
        // Uygulamada kamera izni tanımlı olduğu için, telefonun kamera uygulamasını açmadan önce de izin gerekir
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera.launch(captureUri)
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    // Ayar değişince önizlemeyi yeniden üret (kaydırırken kısa bekleyerek)
    LaunchedEffect(source, detail, thickness, keepOriginal) {
        val s = source ?: return@LaunchedEffect
        if (keepOriginal) return@LaunchedEffect // orijinal seçiliyken çizgi önizlemesine gerek yok
        computing++
        try {
            delay(120)
            preview = withContext(PreviewDispatcher) {
                val job = coroutineContext[Job]
                sketchify(s, PREVIEW_SIDE, detail, thickness) { job?.isActive == false }
            }
            error = null
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // Bellek yetmezse ya da fotoğraf bozuksa uygulama kapanmasın
            error = lang.pick("Bu fotoğraf çizgiye dönüştürülemedi, başka birini dene.", "Couldn't turn this photo into lines, try another one.")
        } finally {
            computing--
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Paper)
    ) {
        // Başlık
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ui.tint(color, 0.32f))
                .statusBarsPadding()
                .padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Ui.PaperCard)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { ToolIcon(Glyph.Back, deep, size = 22.dp) }
            Spacer(Modifier.width(14.dp))
            Text(L("Fotoğraftan çiz", "Draw from a photo"), color = deep, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        val src = source
        if (src == null) {
            // ---------- Fotoğraf seçme ----------
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    L("Bir fotoğraf seç; istersen telefon onu ana hatlarıyla çizgilere çevirsin, istersen olduğu gibi kullan. Fotoğraf hiçbir yere gönderilmez.",
                        "Pick a photo; let the phone turn it into clean outlines, or use it as it is. The photo never leaves your phone."),
                    color = Ui.Ink, fontSize = 15.sp, lineHeight = 21.sp
                )
                SourceCard(Glyph.Picture, L("Galeriden seç", "Choose from gallery"),
                    L("Telefonundaki bir fotoğraf", "A photo on your phone"), Ui.Accent) { pickGallery() }
                SourceCard(Glyph.Camera, L("Fotoğraf çek", "Take a photo"),
                    L("Kamerayla hemen çek", "Snap one with the camera"), Ui.Mint) { takePhoto() }
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = deep, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(L("Fotoğraf açılıyor…", "Opening the photo…"), color = Ui.InkMuted, fontSize = 14.sp)
                    }
                }
                error?.let { Text(it, color = Ui.Danger, fontSize = 14.sp) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Ui.PaperCard)
                        .padding(16.dp)
                ) {
                    Text(L("İyi sonuç için", "For best results"), color = Ui.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L("• Sade arka planlı fotoğraflar seç.\n• Yüz, hayvan ve nesne fotoğrafları en güzel sonucu verir.\n• Işığı iyi, net fotoğraflar daha temiz çizgi çıkarır.",
                            "• Choose photos with a plain background.\n• Faces, animals and objects work best.\n• Sharp, well-lit photos give cleaner lines."),
                        color = Ui.InkMuted, fontSize = 14.sp, lineHeight = 21.sp
                    )
                }
            }
        } else {
            // ---------- Önizleme ve ayarlar ----------
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Ui.DrawPaper)
                        .border(1.5.dp, Ui.tint(color, 0.45f), RoundedCornerShape(24.dp))
                        .graphPaper(line = Ui.paperTint(color, 0.18f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val shown = if (keepOriginal) src else preview
                    if (shown != null) {
                        Image(shown.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        // Ayar değişti, yeni çizgiler hazırlanıyor: eski önizleme yerinde kalır, köşede küçük gösterge
                        if (!keepOriginal && computing > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Ui.PaperCard),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(color = deep, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp)) }
                        }
                    } else {
                        // İlk çizgiler hazırlanırken fotoğraf soluk görünür (boş ekran yerine)
                        Image(src.asImageBitmap(), null, contentScale = ContentScale.Fit, alpha = 0.25f, modifier = Modifier.fillMaxSize())
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = deep)
                            Spacer(Modifier.height(10.dp))
                            Text(L("Çizgiler hazırlanıyor…", "Preparing the lines…"), color = deep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Ui.Danger, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(12.dp))
                // Nasıl kaydedilsin: çizgiye dönüştür ya da olduğu gibi
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ui.tint(color, 0.25f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(false to L("Çizgiye dönüştür", "Turn into lines"), true to L("Orijinal fotoğraf", "Original photo")).forEach { (orig, label) ->
                        val sel = keepOriginal == orig
                        Box(
                            Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) Ui.PaperCard else Color.Transparent)
                                .border(if (sel) 1.5.dp else 0.dp, if (sel) deep else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable(enabled = !saving) { keepOriginal = orig },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (sel) deep else Ui.InkMuted, fontSize = 14.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (keepOriginal) {
                    Text(
                        L("Fotoğraf olduğu gibi kaydedilir. Çizerken saydamlığını ayarlayıp üstünden geçebilirsin.",
                            "The photo is saved as it is. While drawing, adjust its opacity and trace over it."),
                        color = Ui.InkMuted, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                } else {
                    SketchSlider(L("Ayrıntı", "Detail"), L("Sade", "Simple"), L("Çok", "Lots"), detail) { detail = it }
                    SketchSlider(L("Çizgi kalınlığı", "Line weight"), L("İnce", "Thin"), L("Kalın", "Bold"), thickness) { thickness = it }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, Ui.tint(color, 0.7f), RoundedCornerShape(16.dp))
                        .clickable(enabled = !saving) { source = null; preview = null; error = null },
                    contentAlignment = Alignment.Center
                ) { Text(L("Başka fotoğraf", "Another photo"), color = deep, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .weight(1.3f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(Ui.Rose, Ui.Sun)))
                        .clickable(enabled = !saving) {
                            saving = true
                            error = null
                            scope.launch {
                                val original = keepOriginal
                                val file = withContext(Dispatchers.Default) {
                                    // Hata (ör. bellek) olursa uygulama kapanmaz, aşağıdaki uyarı çıkar
                                    runCatching {
                                        if (original) {
                                            // Olduğu gibi: sadece en uzun kenar FINAL_SIDE olacak şekilde küçültülür
                                            val k = FINAL_SIDE.toFloat() / maxOf(src.width, src.height)
                                            val out = if (k < 1f) Bitmap.createScaledBitmap(
                                                src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true
                                            ) else src
                                            UserSketches.saveOriginal(context, out).also { if (out !== src) out.recycle() }
                                        } else {
                                            val full = sketchify(src, FINAL_SIDE, detail, thickness)
                                            UserSketches.saveFile(context, full).also { full.recycle() }
                                        }
                                    }.getOrNull()
                                }
                                val item = file?.let { UserSketches.register(context, it) }
                                saving = false
                                if (item != null) onCreated(item)
                                else error = lang.pick("Kaydedilemedi, telefonda yer olmayabilir.", "Couldn't save, the phone may be out of space.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (saving) CircularProgressIndicator(color = Ui.OnAccent, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                    else Text(L("Kaydet ve çiz →", "Save and draw →"), color = Ui.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SourceCard(icon: Glyph, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Ui.tint(color, 0.32f))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Ui.PaperCard),
            contentAlignment = Alignment.Center
        ) { ToolIcon(icon, Ui.deep(color, 0.42f), size = 28.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ui.deep(color, 0.42f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Ui.Ink.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        Text("›", color = Ui.deep(color, 0.42f), fontSize = 28.sp)
    }
}

@Composable
private fun SketchSlider(title: String, low: String, high: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = Ui.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(low, color = Ui.InkMuted, fontSize = 12.sp, modifier = Modifier.width(46.dp))
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Ui.deep(Ui.Rose, 0.7f),
                    activeTrackColor = Ui.Rose,
                    inactiveTrackColor = Ui.tint(Ui.Rose, 0.3f)
                ),
                modifier = Modifier.weight(1f)
            )
            Text(high, color = Ui.InkMuted, fontSize = 12.sp, modifier = Modifier.width(46.dp).padding(start = 8.dp))
        }
    }
}
