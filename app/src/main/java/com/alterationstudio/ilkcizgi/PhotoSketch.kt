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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * FOTOĞRAFTAN ÇİZİM
 * Seçilen fotoğraf telefonda (internete gönderilmeden) çizgi çizimine çevrilir:
 * resim iki farklı miktarda bulanıklaştırılır, aradaki fark kenarları verir (DoG yöntemi).
 * Çevresinden koyu olan ince yerler çizgi olur; tek tük noktalar temizlenir.
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

/** Kutu bulanıklığı (yatay + dikey), 3 kez uygulanınca Gauss bulanıklığına çok yakındır */
private fun boxBlur3(src: IntArray, w: Int, h: Int, r: Int): IntArray {
    val a = src.copyOf()
    val b = IntArray(w * h)
    val d = 2 * r + 1
    repeat(3) {
        // yatay
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -r..r) sum += a[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                b[row + x] = sum / d
                sum += a[row + (x + r + 1).coerceAtMost(w - 1)] - a[row + (x - r).coerceAtLeast(0)]
            }
        }
        // dikey
        for (x in 0 until w) {
            var sum = 0
            for (k in -r..r) sum += b[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                a[y * w + x] = sum / d
                sum += b[(y + r + 1).coerceAtMost(h - 1) * w + x] - b[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }
    return a
}

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
    val n = w * h
    val px = IntArray(n)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    if (bmp !== src) bmp.recycle() // küçültülmüş kopya artık gerekmiyor (asıl fotoğrafa dokunulmaz)
    val gray = IntArray(n) {
        val c = px[it]
        (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    }
    // Ölçek: her çözünürlükte aynı görünsün diye 1000 piksele göre
    val k = max(w, h) / 1000f
    val sigma = (0.8f + 1.8f * thickness) * k
    val r1 = max(1, sigma.roundToInt())
    val r2 = max(r1 + 1, (sigma * 2.2f).roundToInt())
    check()
    val fine = boxBlur3(gray, w, h, r1)
    check()
    val coarse = boxBlur3(gray, w, h, r2)
    check()
    val thr = 14f - 11f * detail

    // Çizgi adayı: çevresinden belirgin koyu yer
    val diff = FloatArray(n) { (coarse[it] - fine[it]).toFloat() }
    val line = BooleanArray(n) { diff[it] > thr }

    // Küçük lekeleri temizle (8 komşuluk)
    val minSize = ((10f + 40f * (1f - detail)) * k * k).roundToInt().coerceAtLeast(3)
    val seen = BooleanArray(n)
    val queue = IntArray(n)
    val keep = BooleanArray(n)
    for (start in 0 until n) {
        if (!line[start] || seen[start]) continue
        var head = 0
        var tail = 0
        queue[tail++] = start
        seen[start] = true
        while (head < tail) {
            val p = queue[head++]
            val x = p % w
            val y = p / w
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val q = ny * w + nx
                if (line[q] && !seen[q]) { seen[q] = true; queue[tail++] = q }
            }
        }
        if (tail >= minSize) for (i in 0 until tail) keep[queue[i]] = true
    }

    check()
    // Yumuşak kenarlı koyu çizgi
    val out = IntArray(n)
    for (i in 0 until n) {
        val ink = if (keep[i]) ((diff[i] - thr) * 255f / (thr * 1.2f + 6f) + 90f).coerceIn(0f, 255f).toInt() else 0
        val v = 255 - ink
        out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

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
        delay(120)
        preview = withContext(Dispatchers.Default) {
            val job = coroutineContext[Job]
            sketchify(s, PREVIEW_SIDE, detail, thickness) { job?.isActive == false }
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
                    } else {
                        CircularProgressIndicator(color = deep)
                    }
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
                        .clickable(enabled = !saving) { source = null; preview = null },
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
                            scope.launch {
                                val original = keepOriginal
                                val file = withContext(Dispatchers.Default) {
                                    if (original) {
                                        // Olduğu gibi: sadece en uzun kenar FINAL_SIDE olacak şekilde küçültülür
                                        val k = FINAL_SIDE.toFloat() / maxOf(src.width, src.height)
                                        val out = if (k < 1f) Bitmap.createScaledBitmap(
                                            src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true
                                        ) else src
                                        UserSketches.saveOriginal(context, out)
                                    } else {
                                        val full = sketchify(src, FINAL_SIDE, detail, thickness)
                                        UserSketches.saveFile(context, full)
                                    }
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
