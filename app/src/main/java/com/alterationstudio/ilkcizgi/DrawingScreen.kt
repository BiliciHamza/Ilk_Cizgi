package com.alterationstudio.ilkcizgi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.SystemClock
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * ÇİZİM EKRANI
 *
 * Kamera görüntüsünün üstüne çizim 2D olarak çizilir ve ekranda sabit durur.
 * Telefon bir standa yaslı olduğu için kâğıt da ekranda durur, çizim kâğıttan kaymaz.
 * KİLİTLE: Konum tuşlarını kapatır, kameranın odağını ve ışık ayarını sabitler,
 *          ilerleme yüzdesini saymaya başlar (5 saniyede bir baştan hesaplanır).
 * ARKA PLAN: çizimin arkasına düz zemin koyar; resme bakarak çizmek için.
 * Kilit ekranın üstündedir; yanındaki göz düğmesi ekranı sadeleştirir (sadece kilit ve o kalır).
 * EL ÖNDE / ÇİZDİKLERİMİ GİZLE: Kamera görüntüsüne göre çizim el üstünde ya da
 * çizdiğin yerlerde gizlenir (Vision.kt).
 * ⏺ hızlandırılmış video, 📷 kâğıdın fotoğrafı: ikisi de "Çizimlerim"e kaydedilir.
 */

// ---------- İnce ayar adımları ----------
private const val MOVE_STEP_DP = 1.5f       // ok başına ekranda 1,5 dp
private const val ROTATE_STEP_DEG = 0.5f    // döndürme başına 0,5 derece
private const val SIZE_STEP = 0.01f         // boyutta %1

// Alt araç çubuğu: bir sıranın yüksekliği ve alttaki sıradan görünen kısım
private val TOOL_ROW_H = 84.dp
private val TOOL_PEEK = 30.dp
private const val SHEET_HIDDEN = 0
private const val SHEET_PEEK = 1
private const val SHEET_FULL = 2

// Alt araç çubuğundaki ayar araçları
private const val TOOL_MOVE = "konum"
private const val TOOL_SIZE = "boyut"
private const val TOOL_OPACITY = "saydamlik"
private const val TOOL_COLOR = "renk"
private const val TOOL_SENSE = "hassasiyet"
private const val TOOL_BACKGROUND = "arka_plan"
private const val DONE_AT = 0.85f           // bu orandan sonra "bitti" sayılır
private const val GRID_COLS = 4             // ızgaranın yatay kare sayısı

// İlerleme yüzdesi
private const val PROGRESS_WINDOW_MS = 5000L   // yüzde bu aralıkla baştan hesaplanır
private const val PROGRESS_CONFIRM = 2         // iz art arda bu kadar karede görülünce "çizildi" sayılır
private const val PROGRESS_MIN_SEEN = 8        // bir pencerede bundan az görülebilen (elin altında kalan) yerin eski durumu korunur


/** Çizimi yükler: beyaz arka plan saydam olur, koyu çizgiler kalır. Büyük resimler küçültülür. */
internal fun loadLineArt(context: Context, path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openDrawingStream(context, path).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val src = openDrawingStream(context, path).use { BitmapFactory.decodeStream(it, null, opts) }
        ?: return null
    // Olduğu gibi kaydedilmiş fotoğraf: dönüştürmeden, renkli ve opak gösterilir (saydamlığı kullanıcı ayarlar)
    if (isOriginalPhoto(path)) return src
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    src.recycle() // pikseller alındı; büyük ara resim beklemeden bellekten çıksın
    for (i in px.indices) {
        val c = px[i]
        val a = (c ushr 24) and 0xFF
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        // Koyu piksel = mürekkep (opak), açık piksel = kâğıt (saydam)
        val ink = (((255 - lum) * a) / 255 * 3 / 2).coerceAtMost(255)
        px[i] = (ink shl 24) or (c and 0x00FFFFFF)
    }
    Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}.getOrNull()

/**
 * Çizilen yerleri sayar. Kılavuzun kapladığı hücrelerden kaçında kalem izi görüldüğüne bakar.
 * Bir hücre art arda 2 karede çizili görünürse hemen "çizildi" sayılır (yüzde hızlı artar).
 * 5 saniyede bir yüzde baştan hesaplanır: son 5 saniyede kâğıtta gerçekten görülen izler sayılır.
 * Telefon kayınca ya da gölge düşünce yanlış sayılan yerler böylece kendiliğinden düşer.
 * Yüzde sıfıra inmez: yeni pencere boyunca eski sonuç ekranda kalır, sadece görülmeyen yerler çıkar.
 * Elin altında kalan yerler o an görünmediği için silinmiş sayılmaz (eski durumu korunur).
 */
private class ProgressTracker {
    /** 0..1, -1 = sayılmıyor. Ekran durumu değil: değişince ekran yeniden kurulmaz. */
    var progress = -1f
        private set
    /** Ekranda gösterilen tam sayı yüzde; sadece yüzde değişince yazı yenilenir */
    var percent by mutableIntStateOf(-1)
        private set
    private var guide: BooleanArray? = null
    private var total = 0
    private var drawn = BooleanArray(0)   // ekranda sayılan yerler
    private var seen = BooleanArray(0)    // bu 5 saniyede iz görülen yerler
    private var visible = IntArray(0)     // bu 5 saniyede elin altında kalmadan görülen kare sayısı
    private var streak = IntArray(0)
    private var key = 0
    private var windowStart = 0L

    private fun resetCounts(n: Int) {
        drawn = BooleanArray(n)
        seen = BooleanArray(n)
        visible = IntArray(n)
        streak = IntArray(n)
        windowStart = SystemClock.elapsedRealtime()
    }

    fun setGuide(g: BooleanArray?, newKey: Int) {
        if (g == null) { clear(); return }
        // Çizim yerinden oynamadıysa (aynı anahtar) önceki ilerleme korunur
        if (newKey != key || drawn.size != g.size) {
            resetCounts(g.size)
            key = newKey
        } else if (guide == null) {
            // Kilit yeniden açıldı: yeni 5 saniyelik pencere baştan başlasın
            seen.fill(false)
            visible.fill(0)
            windowStart = SystemClock.elapsedRealtime()
        }
        guide = g
        total = g.count { it }
        recompute()
    }

    fun clear() { guide = null; progress = -1f; percent = -1 }

    /** Yüzdeyi sıfırlar: kılavuz aynı kalır, çizildi sayılan yerler baştan sayılır */
    fun restart() {
        resetCounts(drawn.size)
        recompute()
    }

    fun onInk(ink: BooleanArray, hand: BooleanArray?) {
        val g = guide ?: return
        if (ink.size != g.size) return
        val h = hand?.takeIf { it.size == g.size }
        for (k in ink.indices) {
            if (!g[k]) continue
            if (ink[k]) {
                if (streak[k] < PROGRESS_CONFIRM) streak[k]++
                if (streak[k] >= PROGRESS_CONFIRM) { seen[k] = true; drawn[k] = true }
            } else streak[k] = 0
            if (h == null || !h[k]) visible[k]++
        }
        // 5 saniye doldu: yüzde, bu sürede kâğıtta gerçekten görülen izlere göre yeniden hesaplanır
        val now = SystemClock.elapsedRealtime()
        if (now - windowStart >= PROGRESS_WINDOW_MS) {
            for (k in g.indices) {
                if (!g[k]) continue
                drawn[k] = seen[k] || (drawn[k] && visible[k] < PROGRESS_MIN_SEEN)
                seen[k] = false
                visible[k] = 0
            }
            windowStart = now
        }
        recompute()
    }

    private fun recompute() {
        val g = guide ?: return
        if (total == 0) { progress = -1f; percent = -1; return }
        var c = 0
        for (k in g.indices) if (g[k] && drawn[k]) c++
        progress = c.toFloat() / total
        percent = (progress * 100).roundToInt().coerceIn(0, 100)
    }
}

/** Kilitliyken odak, ışık ve renk dengesi sabitlenir: el kadraja girince görüntü titremez */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun setCameraLock(camera: Camera?, previewView: PreviewView, lock: Boolean) {
    val cam = camera ?: return
    runCatching {
        val c2 = Camera2CameraControl.from(cam.cameraControl)
        if (lock) {
            val point = previewView.meteringPointFactory.createPoint(previewView.width / 2f, previewView.height / 2f)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
            ).disableAutoCancel().build()
            val future = cam.cameraControl.startFocusAndMetering(action)
            future.addListener({
                runCatching {
                    c2.setCaptureRequestOptions(
                        CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                            .build()
                    )
                }
            }, ContextCompat.getMainExecutor(previewView.context))
        } else {
            c2.clearCaptureRequestOptions()
            cam.cameraControl.cancelFocusAndMetering()
        }
    }
}

/**
 * Ders modu: hangi dersin hangi adımındayız, adım değişince ne olacak.
 * Adım değişse de çizimin yeri, boyutu, kilidi ve ilerlemesi korunur.
 */
class LessonInfo(val lesson: Lesson, val step: Int, val onStep: (Int) -> Unit)

/** Kamera işçisine giden ayarlar (biri değişince hepsi birlikte gönderilir) */
private data class AnalyzerSettings(
    val viewW: Int, val viewH: Int, val handFront: Boolean, val hideDrawn: Boolean,
    val detectInk: Boolean, val sensitivity: Float
)

/** Ekranın altında kısa süre görünen bilgi (ör. "Fotoğraf kaydedildi" + Paylaş) */
private class Notice(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

@Composable
fun DrawingScreen(
    drawing: CatalogItem,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    lesson: LessonInfo? = null,
    // Devam Et ya da Son açtıkların ile açıldıysa son kaydedilen yer, boyut ve ayarlar (yoksa Ayarlar'daki başlangıç değerleri)
    resume: LastDrawing? = null
) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Çizim yaparken ekran kararmasın
    val rootView = LocalView.current
    DisposableEffect(Unit) {
        rootView.keepScreenOn = true
        onDispose { rootView.keepScreenOn = false }
    }

    // ---------- Kamera izni ----------
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Çizimi arka planda yükle (bir kere). Saydamlık resmi yeniden üretmez.
    // Ders adımı değişirken eski adım, yenisi yüklenene kadar ekranda kalır (titreme olmasın)
    val loaded by produceState<Pair<String, Bitmap>?>(null, drawing.path) {
        val path = drawing.path
        value = withContext(Dispatchers.Default) { loadLineArt(context, path) }?.let { path to it }
    }
    val lineArt = loaded?.second
    val imageAspect = lineArt?.let { it.height.toFloat() / it.width.toFloat() } ?: 1f

    // ---------- Ayarlar ----------
    // Başlangıç değerleri Ayarlar sekmesinden gelir; Devam Et ya da Son açtıkların ile açıldıysa son kalınan hâlden
    val prefs = remember { Settings.drawPrefs(context) }
    val start = remember { resume }
    var opacity by remember { mutableFloatStateOf(start?.opacity ?: prefs.opacity) }
    var locked by remember { mutableStateOf(false) }
    var handFront by remember { mutableStateOf(start?.handFront ?: prefs.handFront) }
    var hideDrawn by remember { mutableStateOf(start?.hideDrawn ?: prefs.hideDrawn) }
    var handSensitivity by remember { mutableFloatStateOf(start?.sensitivity ?: prefs.sensitivity) }
    var rotation by remember { mutableFloatStateOf(start?.rotation ?: 0f) }
    var mirror by remember { mutableStateOf(start?.mirror ?: false) }
    var gridOn by remember { mutableStateOf(start?.grid ?: prefs.grid) }
    var colorIndex by remember { mutableIntStateOf(start?.colorIndex ?: prefs.colorIndex) }
    var blink by remember { mutableStateOf(start?.blink ?: prefs.blink) }
    // Arka plan (BackgroundColors sırası; 0 = yok)
    var backdropIndex by remember { mutableIntStateOf(start?.background ?: prefs.background) }
    // Sade görünüm: üstte sadece kilit ve göz düğmesi kalır, geri kalan her şey gizlenir (çizim ve maske çalışmaya devam eder)
    var cleanView by remember { mutableStateOf(false) }
    // Yansıtmayı gizle: çizim (kılavuz) tamamen gizlenir, sadece kamera görünür; çizdiğini kontrol etmek için.
    // Kaydedilmez: her çizim yansıtma açık başlar (yüzde sayımı ve diğer ayarlar etkilenmez)
    var guideHidden by remember { mutableStateOf(false) }
    BackHandler(enabled = cleanView) { cleanView = false }
    // Konum ekranın oranı olarak, boyut ekran genişliğinin oranı olarak
    var fixedX by remember { mutableFloatStateOf(start?.x ?: 0.5f) }
    var fixedY by remember { mutableFloatStateOf(start?.y ?: 0.5f) }
    var fixedW by remember { mutableFloatStateOf(start?.w ?: 0.7f) }

    // Olduğu gibi kaydedilmiş fotoğrafta kılavuz rengi ve ilerleme yüzdesi kullanılmaz
    val isPhoto = isOriginalPhoto(drawing.path)

    // ---------- Devam Et için son durumu kaydet ----------
    // Çizim açılınca ve ayar değiştikçe (kısa bir bekleyişle) yazılır, ekrandan çıkarken de son kez yazılır
    val sessionDrawing by rememberUpdatedState(drawing)
    val sessionLesson by rememberUpdatedState(lesson)
    fun sessionNow() = LastDrawing(
        path = sessionDrawing.path,
        lessonId = sessionLesson?.lesson?.id,
        lessonStep = sessionLesson?.step ?: 0,
        x = fixedX, y = fixedY, w = fixedW, rotation = rotation, mirror = mirror,
        opacity = opacity, colorIndex = colorIndex, grid = gridOn, blink = blink,
        handFront = handFront, hideDrawn = hideDrawn, sensitivity = handSensitivity,
        background = backdropIndex
    )
    LaunchedEffect(Unit) {
        snapshotFlow { sessionNow() }.collectLatest { s ->
            delay(500)
            LastSession.save(context, s)
            RecentSettings.save(context, s)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val s = sessionNow()
            LastSession.save(context, s)
            RecentSettings.save(context, s)
        }
    }

    // ---------- Kameradan gelen maske ve ilerleme ----------
    var handMask by remember { mutableStateOf<Bitmap?>(null) }
    val tracker = remember { ProgressTracker() }

    var viewW by remember { mutableIntStateOf(0) }
    var viewH by remember { mutableIntStateOf(0) }

    val analyzer = remember {
        FrameAnalyzer(onResult = { r ->
            handMask = r?.mask
            r?.ink?.let { tracker.onInk(it, r.hand) }
        })
    }
    // Ekran tarafındaki ayarları arka plan işçisine bildir
    // Ayar değiştikçe (ekran yeniden kurulmasa bile) hemen işçiye geçer
    LaunchedEffect(analyzer) {
        snapshotFlow { AnalyzerSettings(viewW, viewH, handFront, hideDrawn, locked, handSensitivity) }
            .collect { a ->
                analyzer.viewW = a.viewW
                analyzer.viewH = a.viewH
                analyzer.handFront = a.handFront
                analyzer.hideDrawn = a.hideDrawn
                analyzer.detectInk = a.detectInk
                analyzer.sensitivity = a.sensitivity
            }
    }

    /** Çizimin ekrandaki 4 köşesi */
    fun currentCorners(): FloatArray? {
        if (lineArt == null || viewW == 0) return null
        val w = fixedW * viewW
        val info = lesson ?: return rotatedRect(fixedX * viewW, fixedY * viewH, w, w * imageAspect, rotation)
        // Ders: önce tüm dersin tuvali yerleşir, adım resmi tuvalin içindeki kendi yerine oturur
        val canvas = rotatedRect(fixedX * viewW, fixedY * viewH, w, w * info.lesson.aspect, rotation)
        // Ekrandaki resim hangi adımınsa onun yeri kullanılır (yeni adım yüklenirken karışmasın)
        val shownPath = loaded?.first
        val st = info.lesson.steps.firstOrNull { it.item.path == shownPath } ?: info.lesson.steps[info.step]
        val asp = info.lesson.aspect
        var u0 = st.x
        var u1 = st.x + st.w
        if (mirror) { val t = u0; u0 = 1f - u1; u1 = 1f - t } // aynalı tuvalde yeri de aynalanır
        val v0 = st.y / asp
        val v1 = (st.y + st.w * imageAspect) / asp
        fun at(u: Float, v: Float, out: FloatArray, i: Int) {
            out[i] = canvas[0] + u * (canvas[2] - canvas[0]) + v * (canvas[6] - canvas[0])
            out[i + 1] = canvas[1] + u * (canvas[3] - canvas[1]) + v * (canvas[7] - canvas[1])
        }
        val q = FloatArray(8)
        at(u0, v0, q, 0); at(u1, v0, q, 2); at(u1, v1, q, 4); at(u0, v1, q, 6)
        return q
    }

    // Kilitlenince kılavuzun kapladığı hücreleri hesapla, ilerleme saymaya başla
    LaunchedEffect(locked, lineArt, mirror, viewW, viewH) {
        val art = lineArt
        val corners = currentCorners()
        if (!locked || art == null || corners == null || isPhoto) {
            tracker.clear()
            return@LaunchedEffect
        }
        // Anahtar çizimin yerine bağlı: ders adımı değişince yer aynı kalır, çizdiklerin sayılmaya devam eder
        val key = listOf(fixedX, fixedY, fixedW, rotation, mirror, viewW, viewH).hashCode()
        val w = viewW
        val h = viewH
        val m = mirror
        val g = withContext(Dispatchers.Default) { guideCells(art, corners, m, w, h) }
        tracker.setGuide(g, key)
    }

    // ---------- Kamera bağlama ----------
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    // Ekran dönünce kamera yeniden bağlanır (görüntü yönü doğru kalsın)
    val orientation = LocalConfiguration.current.orientation
    val lockedNow by rememberUpdatedState(locked)

    DisposableEffect(hasPermission, orientation) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            val p = runCatching { providerFuture.get() }.getOrNull() ?: run {
                cameraError = true; return@addListener
            }
            provider = p
            // Önizleme yerleşince bağla ki görünen alan (viewport) belli olsun
            previewView.post {
                if (disposed) return@post
                val ratio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                val preview = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                // Analiz görüntüsü ≈1280x960: ince kalem çizgileri de seçilebilsin
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(ratio)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 960),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, analyzer)
                val group = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(analysis)
                    .apply { previewView.viewPort?.let { setViewPort(it) } }
                    .build()
                runCatching {
                    p.unbindAll()
                    val cam = p.bindToLifecycle(
                        context as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, group
                    )
                    camera = cam
                    cameraError = false
                    // Ekran dönerken kilitliydiyse kilidi yeniden uygula
                    if (lockedNow) previewView.postDelayed({ setCameraLock(cam, previewView, true) }, 800)
                }.onFailure { cameraError = true }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            runCatching { provider?.unbindAll() }
            camera = null
            torchOn = false
            executor.shutdown()
        }
    }

    // ---------- Bilgi baloncuğu ----------
    var notice by remember { mutableStateOf<Notice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4500)
            notice = null
        }
    }

    // ---------- Yanıp sönen kılavuz ----------
    // Kılavuz birkaç saniyede bir kısa süre kaybolur: kendi çizdiğini temiz görürsün
    // Değer sadece çizim katmanında okunur: yanıp sönerken ekranın geri kalanı yeniden kurulmaz
    val blinkAlpha = remember { Animatable(1f) }
    LaunchedEffect(blink) {
        blinkAlpha.snapTo(1f)
        if (!blink) return@LaunchedEffect
        val (shown, hidden) = blinkTimes(prefs.blinkSeconds)
        while (isActive) {
            delay(shown)
            blinkAlpha.animateTo(0f, tween(220))
            delay(hidden)
            blinkAlpha.animateTo(1f, tween(220))
        }
    }

    // ---------- 📷 Fotoğraf ----------
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash) { delay(140); flash = false }
    }
    fun takePhoto() {
        val bmp = previewView.bitmap
        if (bmp == null) {
            notice = Notice(lang.pick("Kamera hazır değil, birazdan tekrar dene.", "Camera isn't ready, try again in a moment."))
            return
        }
        flash = true
        val pct = tracker.percent
        scope.launch {
            val work = withContext(Dispatchers.IO) { Works.addPhoto(context, bmp, drawing, pct) }
            bmp.recycle()
            notice = if (work != null) Notice(
                lang.pick("Fotoğraf Çizimlerim'e kaydedildi", "Photo saved to My drawings"),
                lang.pick("Paylaş", "Share")
            ) {
                shareUri(context, fileUri(context, java.io.File(work.file)), "image/jpeg",
                    lang.pick("Fotoğrafı paylaş", "Share photo"))
            } else Notice(lang.pick("Fotoğraf kaydedilemedi.", "Couldn't save the photo."))
        }
    }

    // ---------- ⏺ Hızlandırılmış video ----------
    var recorder by remember { mutableStateOf<TimelapseRecorder?>(null) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var encoding by remember { mutableStateOf(false) }
    var encodeProgress by remember { mutableFloatStateOf(0f) }


    fun videoSaved(uri: Uri?) {
        notice = if (uri != null) {
            val pct = tracker.percent
            Works.addVideo(context, uri, drawing, pct)
            Notice(
                lang.pick("Video galeriye ve Çizimlerim'e kaydedildi", "Video saved to gallery and My drawings"),
                lang.pick("Paylaş", "Share")
            ) { shareUri(context, uri, "video/mp4", lang.pick("Videoyu paylaş", "Share video")) }
        } else Notice(lang.pick("Video için çok kısa kayıt. Biraz daha uzun kaydet.", "Recording too short. Record a little longer."))
    }

    fun toggleRecording() {
        val r = recorder
        if (r == null) {
            val nr = TimelapseRecorder(context, prefs.videoFps)
            recorder = nr
            recordStart = System.currentTimeMillis()
            // Kareleri kamera işçisi arka planda üretir (ekran görüntüsü almak ekranı bekletiyordu)
            analyzer.frameIntervalMs = TIMELAPSE_INTERVAL_MS
            analyzer.frameSink = { bmp -> nr.addFrame(bmp) }
            notice = Notice(lang.pick("Kayıt başladı. Durdurmak için üstteki kırmızı düğmeye bas.", "Recording. Tap the red button at the top to stop."))
        } else {
            analyzer.frameSink = null
            recorder = null
            encoding = true
            encodeProgress = 0f
            // Ekrandan çıkılsa bile video bitirilsin diye uygulama kapsamında
            appScope.launch {
                var shown = -1
                val uri = r.finish { p ->
                    // Sadece yüzde değişince ekrana yaz (her karede değil)
                    val q = (p * 100).toInt()
                    if (q != shown) { shown = q; encodeProgress = q / 100f }
                }
                withContext(Dispatchers.Main) {
                    encoding = false
                    videoSaved(uri)
                }
            }
        }
    }

    // Ekrandan çıkarken kayıt sürüyorsa video arka planda bitirilip kaydedilir
    val recorderNow by rememberUpdatedState(recorder)
    val drawingNow by rememberUpdatedState(drawing)
    val langNow by rememberUpdatedState(lang)
    DisposableEffect(Unit) {
        onDispose {
            analyzer.frameSink = null
            val r = recorderNow ?: return@onDispose
            val d = drawingNow
            val lg = langNow
            val appCtx = context.applicationContext
            val pct = tracker.percent
            appScope.launch {
                val uri = r.finish { }
                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        Works.addVideo(appCtx, uri, d, pct)
                        Toast.makeText(appCtx, lg.pick("Video kaydedildi", "Video saved"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ---------- Alt araç çubuğu ----------
    // Üç hâli var: 0 gizli, 1 yarı açık (ilk sıra + alttakinin ucu), 2 tam açık (bütün sıralar).
    // Tutamağa dokununca gizlenir / yarı açılır. Yukarı çekince tam açılır, aşağı çekince bir kademe iner.
    // Bir araca dokununca onun ayarı çubuğun üstünde açılır; tekrar dokununca kapanır.
    // Yatay tutulunca ekran alçak kalır: çubuk gizli başlar, çizim görünsün
    var sheetLevel by remember(orientation) {
        mutableIntStateOf(if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) SHEET_HIDDEN else SHEET_PEEK)
    }
    val dragThreshold = with(density) { 32.dp.toPx() }
    var tool by remember { mutableStateOf<String?>(null) }
    fun setSheet(level: Int) {
        sheetLevel = level.coerceIn(SHEET_HIDDEN, SHEET_FULL)
        if (sheetLevel == SHEET_HIDDEN) tool = null
    }
    // Araçların üstünde parmakla kaydırma: yarı açıkken yukarı itince tam açılır,
    // tam açıkken aşağı çekince yarıya iner
    val sheetScroll = remember(orientation, dragThreshold) {
        object : NestedScrollConnection {
            var pull = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (sheetLevel == SHEET_PEEK && available.y < 0f && source == NestedScrollSource.UserInput) {
                    sheetLevel = SHEET_FULL
                    pull = 0f
                    return available
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (sheetLevel == SHEET_FULL && available.y > 0f && source == NestedScrollSource.UserInput) {
                    pull += available.y
                    if (pull > dragThreshold) { sheetLevel = SHEET_PEEK; pull = 0f }
                    return available
                }
                pull = 0f
                return Offset.Zero
            }
        }
    }

    // ---------- Hareketler ----------
    val stepPx = with(density) { MOVE_STEP_DP.dp.toPx() }
    fun move(dx: Int, dy: Int) {
        if (viewW > 0) fixedX += dx * stepPx / viewW
        if (viewH > 0) fixedY += dy * stepPx / viewH
    }

    fun resetDrawing() {
        rotation = 0f
        fixedX = 0.5f; fixedY = 0.5f; fixedW = 0.7f
    }

    // Sıfırla: kilitli değilken çizimi ortaya alır; kilitliyken yeri bozmadan yüzdeyi sıfırlar
    fun resetPressed() {
        if (locked) {
            tracker.restart()
            notice = Notice(lang.pick(
                "Yüzde sıfırlandı. Kâğıtta görünen çizgiler baştan sayılıyor.",
                "Progress reset. Lines on the paper are being counted again."))
        } else resetDrawing()
    }

    fun setLocked(value: Boolean) {
        locked = value
        setCameraLock(camera, previewView, value)
        // Kilitliyken konum ve boyut değişmez: açık olan ayarı kapat
        if (value && (tool == TOOL_MOVE || tool == TOOL_SIZE)) tool = null
    }

    // ===================================================================================
    // EKRAN
    // ===================================================================================
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewW = it.width; viewH = it.height }
    ) {
        if (!hasPermission) {
            PermissionCard(
                onAsk = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack
            )
            return@Box
        }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // ---------- Çizim katmanı ----------
        TraceOverlay(
            bitmap = { lineArt },
            corners = { currentCorners() },
            opacity = { if (guideHidden) 0f else opacity * blinkAlpha.value },
            mask = { if (handFront || hideDrawn) handMask else null },
            tint = { if (isPhoto) null else guideTintOn(colorIndex, backdropIndex) },
            backdrop = { if (isPhoto) null else BackgroundColors.getOrNull(backdropIndex) },
            mirror = { mirror },
            grid = { gridOn }
        )

        // Fotoğraf çekilince kısa beyaz parlama
        AnimatedVisibility(visible = flash, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.7f)))
        }

        // ---------- Üst: düğmeler ve ipucu ----------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Üst sıra: geri · · · kilit, sade görünüm, kayıt, fener, favori, yardım.
            // Dar ekranda düğmeler biraz küçülür ki 7 düğme (kayıttayken süre hapı dahil) tek sıraya sığsın.
            // Sade görünümde sadece kilit ve göz düğmesi görünür; diğerleri yerinde ama görünmez ve basılmaz
            // (kilit ve göz kaymasın, parmak aynı yere bassın).
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val btn = when {
                    maxWidth >= 392.dp -> 44.dp
                    maxWidth >= 356.dp -> 40.dp
                    else -> 36.dp
                }
                val gap = if (btn == 44.dp) 8.dp else if (btn == 40.dp) 6.dp else 4.dp
                val others = !cleanView
                // maxWidth iç Row'larda okunamaz (Compose kapsam kuralı): burada bir kere hesaplanır
                val showFrames = maxWidth >= 430.dp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Glyph.Back, size = btn, enabled = others, modifier = Modifier.alpha(if (others) 1f else 0f), onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    // Kilit: konumu, odağı ve ışığı sabitler, yüzdeyi saymaya başlar
                    RoundIcon(if (locked) Glyph.Lock else Glyph.Unlock, active = locked, size = btn, onClick = { setLocked(!locked) })
                    Spacer(Modifier.width(gap))
                    // Sade görünüm: ekrandaki her şeyi gizler / geri getirir
                    RoundIcon(if (cleanView) Glyph.Eye else Glyph.EyeOff, active = cleanView, size = btn, onClick = { cleanView = !cleanView })
                    Spacer(Modifier.width(gap))
                    Row(Modifier.alpha(if (others) 1f else 0f), verticalAlignment = Alignment.CenterVertically) {
                        // ⏺ video kaydı
                        val rec = recorder
                        if (rec != null) {
                            RecordChip(rec, recordStart, height = btn, showFrames = showFrames, enabled = others) { toggleRecording() }
                        } else {
                            RoundIcon(Glyph.Record, tint = Ui.Danger, size = btn, enabled = others && !encoding, onClick = { toggleRecording() })
                        }
                        Spacer(Modifier.width(gap))
                        // Fener (telefonda flaş varsa) 
                        if (camera?.cameraInfo?.hasFlashUnit() == true) {
                            RoundIcon(Glyph.Torch, active = torchOn, size = btn, enabled = others, onClick = {
                                torchOn = !torchOn
                                camera?.cameraControl?.enableTorch(torchOn)
                                // Kilitliyken ışık değişti: ışık ayarını yeni duruma göre yeniden sabitle
                                if (locked) scope.launch {
                                    setCameraLock(camera, previewView, false)
                                    delay(700)
                                    if (locked) setCameraLock(camera, previewView, true)
                                }
                            })
                            Spacer(Modifier.width(gap))
                        }
                        val fav = Library.isFavorite(drawing.path)
                        RoundIcon(if (fav) Glyph.HeartFilled else Glyph.Heart, tint = if (fav) Ui.Rose else null, size = btn,
                            enabled = others, onClick = { Library.toggleFavorite(context, drawing.path) })
                        Spacer(Modifier.width(gap))
                        RoundIcon(Glyph.Help, size = btn, enabled = others, onClick = onHelp)
                    }
                }
            }

            val hint = when {
                cameraError -> lang.pick("Kamera açılamadı. Geri çıkıp tekrar dene.", "Couldn't open the camera. Go back and try again.")
                lineArt == null -> lang.pick("Çizim hazırlanıyor…", "Preparing the drawing…")
                locked -> null
                lesson != null && lesson.step > 0 ->
                    lang.pick("Yeni çizgiler önceki adımın tam üstüne gelir. Yerini değiştirme, kilitle ve devam et.",
                        "New lines land right on top of the last step. Don't move it, just lock and carry on.")
                lesson != null ->
                    lang.pick("Telefonu sabitle, alttaki Konum ve Boyut ile hizala, sonra üstteki kilide bas. Her adımda yeni çizgiler eklenecek.",
                        "Prop the phone, line it up with Position and Size below, then tap the lock at the top. Each step adds new lines.")
                else ->
                    lang.pick("Telefonu sabitle, alttaki Konum ve Boyut ile çizimi hizala, sonra üstteki kilide bas.",
                        "Prop the phone, line up the drawing with Position and Size below, then tap the lock at the top.")
            }
            if (hint != null && !cleanView) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = hint,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Ui.Glass)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // ---------- Video hazırlanıyor ----------
        if (encoding && !cleanView) {
            EncodingCard({ encodeProgress }, lang, Modifier.align(Alignment.Center))
        }

        // ---------- Alt: bilgi, ders, ilerleme ve araç çubuğu (sade görünümde gizli) ----------
        if (!cleanView) Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                notice?.let { n ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Ui.PanelSolid)
                            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(n.text, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp,
                            modifier = Modifier.weight(1f, fill = false).padding(vertical = 6.dp))
                        if (n.action != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                n.action, color = Ui.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Ui.Accent)
                                    .clickable { n.onAction?.invoke(); notice = null }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        } else Spacer(Modifier.width(8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Ders: adım çubuğu
                if (lesson != null) {
                    LessonBar(
                        lesson = lesson,
                        lang = lang,
                        onPrev = { lesson.onStep(lesson.step - 1) },
                        onNext = {
                            val l = lesson.lesson
                            Library.markLesson(context, l.id, lesson.step + 1)
                            if (lesson.step + 1 < l.steps.size) {
                                lesson.onStep(lesson.step + 1)
                                notice = Notice(lang.pick(
                                    "${lesson.step + 2}. adım: önceki çizdiklerinin üstüne devam et",
                                    "Step ${lesson.step + 2}: keep going on top of what you drew"))
                            } else {
                                notice = Notice(
                                    lang.pick("Tebrikler, dersi bitirdin! 🎉", "Well done, lesson complete! 🎉"),
                                    lang.pick("Fotoğrafını çek", "Take a photo")
                                ) { takePhoto() }
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // Yüzde sık değişir: ayrı parçada okunur, ekranın geri kalanı yeniden kurulmaz
                ProgressAndPhoto(
                    tracker = tracker,
                    locked = locked,
                    showPhoto = lineArt != null,
                    lang = lang,
                    onPhoto = { takePhoto() }
                )
                Spacer(Modifier.height(10.dp))
            }

            // Araç çubuğu: tutamağa dokununca gizlenir / yarı açılır, yukarı çekince tam açılır, aşağı çekince bir kademe iner
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Ui.Panel)
                    .navigationBarsPadding()
            ) {
                var drag by remember { mutableFloatStateOf(0f) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(dragThreshold) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (drag > dragThreshold) setSheet(sheetLevel - 1)
                                    else if (drag < -dragThreshold) setSheet(SHEET_FULL)
                                    drag = 0f
                                },
                                onDragCancel = { drag = 0f }
                            ) { _, dy -> drag += dy }
                        }
                        .clickable { setSheet(if (sheetLevel == SHEET_HIDDEN) SHEET_PEEK else SHEET_HIDDEN) }
                        .padding(top = 8.dp, bottom = if (sheetLevel != SHEET_HIDDEN) 2.dp else 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.35f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (sheetLevel) {
                                SHEET_HIDDEN -> lang.pick("⌃  Araçlar", "⌃  Tools") + if (locked) lang.pick("  ·  kilitli", "  ·  locked") else ""
                                SHEET_PEEK -> lang.pick("⌃  Hepsi için yukarı çek  ·  dokun: gizle", "⌃  Pull up for all  ·  tap: hide")
                                else -> lang.pick("⌄  Araçları gizle", "⌄  Hide tools")
                            },
                            color = Ui.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }

                AnimatedVisibility(
                    visible = sheetLevel != SHEET_HIDDEN,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        // ----- Seçili aracın ayarı -----
                        val canEdit = !locked
                        when (tool) {
                            TOOL_MOVE -> ToolControl(lang.pick("Konum · açı ${"%.1f".format(rotation)}°", "Position · angle ${"%.1f".format(rotation)}°")) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RepeatButton("⟲", canEdit) { rotation -= ROTATE_STEP_DEG }
                                    RepeatButton("←", canEdit) { move(-1, 0) }
                                    RepeatButton("↑", canEdit) { move(0, -1) }
                                    RepeatButton("↓", canEdit) { move(0, 1) }
                                    RepeatButton("→", canEdit) { move(1, 0) }
                                    RepeatButton("⟳", canEdit) { rotation += ROTATE_STEP_DEG }
                                }
                            }
                            TOOL_SIZE -> ToolControl(lang.pick("Boyut %${(fixedW * 100).roundToInt()}", "Size ${(fixedW * 100).roundToInt()}%")) {
                                val sizeRange = 0.15f..1.6f
                                fun setSize(v: Float) { fixedW = v.coerceIn(sizeRange.start, sizeRange.endInclusive) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RepeatButton("−", canEdit, small = true) { setSize(fixedW - SIZE_STEP) }
                                    Slider(
                                        value = fixedW,
                                        onValueChange = { setSize(it) },
                                        valueRange = sizeRange,
                                        enabled = canEdit,
                                        colors = sliderColors(),
                                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                                    )
                                    RepeatButton("+", canEdit, small = true) { setSize(fixedW + SIZE_STEP) }
                                }
                            }
                            TOOL_OPACITY -> ToolControl(lang.pick("Saydamlık %${(opacity * 100).roundToInt()}", "Opacity ${(opacity * 100).roundToInt()}%")) {
                                Slider(
                                    value = opacity,
                                    onValueChange = { opacity = it },
                                    valueRange = 0.1f..1f,
                                    colors = sliderColors()
                                )
                            }
                            TOOL_COLOR -> ToolControl(lang.pick("Kılavuz rengi", "Guide colour")) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                                ) {
                                    GuideColors.forEachIndexed { i, c ->
                                        val sel = colorIndex == i
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, if (sel) Color.White else Color.White.copy(alpha = 0.2f), CircleShape)
                                                .padding(5.dp)
                                                .clip(CircleShape)
                                                .background(c ?: Color(0xFF111111))
                                                .clickable { colorIndex = i }
                                        )
                                    }
                                }
                            }
                            TOOL_BACKGROUND -> ToolControl(lang.pick("Arka plan", "Background")) {
                                Text(
                                    lang.pick("Çizimi düz zeminde gösterir; resme bakarak çizmek istersen kullan.",
                                        "Shows the drawing on a plain background, handy for drawing by looking at it."),
                                    color = Ui.Muted, fontSize = 12.sp, lineHeight = 16.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                                ) {
                                    BackgroundColors.forEachIndexed { i, c ->
                                        val sel = backdropIndex == i
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, if (sel) Ui.Accent else Color.White.copy(alpha = 0.25f), CircleShape)
                                                .padding(5.dp)
                                                .clip(CircleShape)
                                                .background(c ?: Color.Transparent)
                                                .clickable { backdropIndex = i },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // İlk seçenek: arka plan yok (kamera görünür)
                                            if (c == null) ToolIcon(Glyph.Close, Color.White, size = 18.dp)
                                        }
                                    }
                                }
                            }
                            TOOL_SENSE -> ToolControl(lang.pick("Algılama hassasiyeti", "Detection sensitivity")) {
                                Text(
                                    lang.pick("El önde, Çizdiklerimi gizle ve yüzde için. En sol önerilir: açık kalem izleri de sayılır, gölgeler el sanılmaz.",
                                        "For Hand in front, Hide my lines and the percentage. Far left is recommended: light pencil marks count and shadows aren't mistaken for your hand."),
                                    color = Ui.Muted, fontSize = 12.sp, lineHeight = 16.sp
                                )
                                Slider(
                                    value = handSensitivity,
                                    onValueChange = { handSensitivity = it },
                                    valueRange = 0f..1f,
                                    steps = SENSITIVITY_STEPS,
                                    colors = sliderColors()
                                )
                                // İki uçta ne yaptığı yazar; sol uç önerilen değerdir, dokununca oraya döner
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    val atRecommended = handSensitivity == SENSITIVITY_RECOMMENDED
                                    Text(
                                        lang.pick("Açık kalem izlerini de say (önerilen)", "Count light pencil marks (recommended)"),
                                        color = if (atRecommended) Ui.Accent else Color.White,
                                        fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = if (atRecommended) 0f else 0.12f))
                                            .clickable { handSensitivity = SENSITIVITY_RECOMMENDED }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        lang.pick("Gölgeleri de el say", "Treat shadows as hand"),
                                        color = Ui.Muted, fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.End,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // ----- Araçlar: 5'li sıralar -----
                        // Yarı açıkken ilk sıra tam, ikinci sıranın üstü azıcık görünür: aşağıda daha fazlası olduğu anlaşılır.
                        // Tam açıkken bütün sıralar görünür (ekran yetmezse yine kaydırılır)
                        fun pick(t: String) { tool = if (tool == t) null else t }
                        val toolScroll = rememberScrollState()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                                .then(if (sheetLevel == SHEET_FULL) Modifier else Modifier.heightIn(max = TOOL_ROW_H + TOOL_PEEK))
                                .nestedScroll(sheetScroll)
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(toolScroll)
                                    .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 10.dp)
                            ) {
                                // 1. sıra: konum ve görünüm ayarları (kilit ekranın üstünde)
                                Row(Modifier.fillMaxWidth()) {
                                    ToolButton(Glyph.Move, lang.pick("Konum", "Position"), selected = tool == TOOL_MOVE, enabled = canEdit,
                                        modifier = Modifier.weight(1f)) { pick(TOOL_MOVE) }
                                    ToolButton(Glyph.Resize, lang.pick("Boyut", "Size"), selected = tool == TOOL_SIZE, enabled = canEdit,
                                        modifier = Modifier.weight(1f)) { pick(TOOL_SIZE) }
                                    ToolButton(Glyph.Opacity, lang.pick("Saydamlık", "Opacity"), selected = tool == TOOL_OPACITY,
                                        modifier = Modifier.weight(1f)) { pick(TOOL_OPACITY) }
                                    ToolButton(Glyph.Color, lang.pick("Renk", "Colour"), selected = tool == TOOL_COLOR,
                                        enabled = !isPhoto, modifier = Modifier.weight(1f)) { pick(TOOL_COLOR) }
                                    // Arka plan seçiliyse renkli görünür; dokununca renk seçimi açılır
                                    ToolButton(Glyph.Backdrop, lang.pick("Arka plan", "Background"), selected = tool == TOOL_BACKGROUND,
                                        on = backdropIndex != 0 && !isPhoto, onColor = Ui.Mint, enabled = !isPhoto,
                                        modifier = Modifier.weight(1f)) { pick(TOOL_BACKGROUND) }
                                }
                                // 2. sıra: aç/kapa özellikler
                                Row(Modifier.fillMaxWidth()) {
                                    ToolButton(Glyph.Hand, lang.pick("El önde", "Hand in front"), on = handFront, onColor = Ui.Accent,
                                        modifier = Modifier.weight(1f)) {
                                        handFront = !handFront
                                        if (!handFront && !hideDrawn) handMask = null
                                    }
                                    ToolButton(Glyph.HideLines, lang.pick("Çizdiklerimi gizle", "Hide my lines"), on = hideDrawn, onColor = Ui.Mint,
                                        modifier = Modifier.weight(1f)) {
                                        hideDrawn = !hideDrawn
                                        if (!hideDrawn && !handFront) handMask = null
                                    }
                                    ToolButton(Glyph.Tune, lang.pick("Hassasiyet", "Sensitivity"), selected = tool == TOOL_SENSE,
                                        modifier = Modifier.weight(1f)) { pick(TOOL_SENSE) }
                                    ToolButton(Glyph.Grid, lang.pick("Izgara", "Grid"), on = gridOn, onColor = Ui.Accent,
                                        modifier = Modifier.weight(1f)) { gridOn = !gridOn }
                                    ToolButton(Glyph.Blink, lang.pick("Yanıp sönsün", "Blink"), on = blink, onColor = Ui.Sun,
                                        modifier = Modifier.weight(1f)) { blink = !blink }
                                }
                                // 3. sıra: aynala, yansıtmayı gizle ve sıfırla
                                Row(Modifier.fillMaxWidth()) {
                                    ToolButton(Glyph.Mirror, lang.pick("Aynala", "Mirror"), on = mirror, onColor = Ui.Accent, enabled = canEdit,
                                        modifier = Modifier.weight(1f)) { mirror = !mirror }
                                    // Açıkken çizim hiç görünmez, sadece kâğıt (kamera) görünür
                                    ToolButton(Glyph.HideGuide, lang.pick("Yansıtmayı gizle", "Hide guide"), on = guideHidden, onColor = Ui.Rose,
                                        modifier = Modifier.weight(1f)) { guideHidden = !guideHidden }
                                    // Kilitliyken yer değişmez, sadece yüzde sıfırlanır
                                    ToolButton(Glyph.Reset, if (locked) lang.pick("Yüzdeyi sıfırla", "Reset %") else lang.pick("Sıfırla", "Reset"),
                                        modifier = Modifier.weight(1f)) { resetPressed() }
                                    Spacer(Modifier.weight(2f))
                                }
                            }
                            // Alt kenarda hafif gölge: kesik sıra "aşağıda devamı var" gibi görünsün (en alta inince kaybolur)
                            if (toolScroll.canScrollForward) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(18.dp)
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Ui.Graphite.copy(alpha = 0.55f))))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Alt satır: ilerleme yüzdesi ve fotoğraf düğmesi. Yüzde sadece burada okunur. */
@Composable
private fun ProgressAndPhoto(
    tracker: ProgressTracker,
    locked: Boolean,
    showPhoto: Boolean,
    lang: Lang,
    onPhoto: () -> Unit
) {
    val pct = tracker.percent
    val done = pct >= (DONE_AT * 100).roundToInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (locked && pct >= 0) {
            ProgressPill(pct, done, lang)
            Spacer(Modifier.width(10.dp))
        }
        // Kâğıdın fotoğrafını çek (bitince yazılı ve parlak)
        if (showPhoto) {
            Row(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (done) Ui.Mint else Ui.PanelSolid)
                    .clickable(onClick = onPhoto)
                    .padding(horizontal = if (done) 16.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolIcon(Glyph.Camera, if (done) Ui.OnAccent else Color.White, size = 22.dp)
                if (done) {
                    Spacer(Modifier.width(8.dp))
                    Text(lang.pick("Fotoğrafını çek", "Take a photo"), color = Ui.OnAccent,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Kayıt sürerken: durdur, süre ve kare sayısı (her yeni karede sadece bu parça yenilenir) */
@Composable
private fun RecordChip(
    rec: TimelapseRecorder,
    startedAt: Long,
    height: Dp = 44.dp,
    showFrames: Boolean = true,
    enabled: Boolean = true,
    onStop: () -> Unit
) {
    val frames = rec.frameCount
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) { now = System.currentTimeMillis(); delay(1000) }
    }
    val secs = ((now - startedAt) / 1000).toInt().coerceAtLeast(0)
    Row(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Ui.Danger)
            .clickable(enabled = enabled, onClick = onStop)
            .padding(horizontal = if (showFrames) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolIcon(Glyph.Stop, Color.White, size = 16.dp)
        Spacer(Modifier.width(if (showFrames) 8.dp else 6.dp))
        // Dar ekranda sadece süre (üst sırada yer kalsın), genişte süre ve kare sayısı
        Text(
            if (showFrames) "%d:%02d · %d".format(secs / 60, secs % 60, frames) else "%d:%02d".format(secs / 60, secs % 60),
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
    }
}

/** "Video hazırlanıyor" kartı; ilerleme sadece burada okunur */
@Composable
private fun EncodingCard(progress: () -> Float, lang: Lang, modifier: Modifier) {
    val p = progress()
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Ui.PanelSolid)
            .padding(horizontal = 26.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(lang.pick("Video hazırlanıyor…", "Making your video…"), color = Color.White,
            fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Bar(p, Ui.Rose, Modifier.width(180.dp))
        Spacer(Modifier.height(8.dp))
        Text(lang.pick("%${(p * 100).roundToInt()}", "${(p * 100).roundToInt()}%"), color = Ui.Muted, fontSize = 13.sp)
    }
}

/** "%42 çizildi" ve ince ilerleme çubuğu */
@Composable
private fun ProgressPill(pct: Int, done: Boolean, lang: Lang) {
    val progress = pct / 100f
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Ui.PanelSolid)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            if (done) lang.pick("Bitti gibi! %$pct", "Nearly done! $pct%")
            else lang.pick("%$pct çizildi", "$pct% drawn"),
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(5.dp))
        Bar(progress, if (done) Ui.Mint else Ui.Accent, Modifier.width(120.dp))
    }
}

@Composable
private fun Bar(value: Float, color: Color, modifier: Modifier) {
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.15f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

/** Kamera izni verilmediyse gösterilen kart */
@Composable
private fun PermissionCard(onAsk: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Graphite)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            L("Kamera izni gerekli", "Camera permission needed"),
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            L("Çizimi kâğıdın üstünde gösterebilmek için kamerayı kullanıyoruz. Görüntü telefondan çıkmaz, kaydedilmez.",
                "We use the camera to show the drawing on your paper. Nothing is saved or sent anywhere."),
            color = Ui.Muted, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAsk,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ui.Accent, contentColor = Ui.OnAccent),
            modifier = Modifier.height(50.dp)
        ) { Text(L("İzin ver", "Allow camera"), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        Text(
            L("Geri dön", "Go back"),
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack)
                .padding(12.dp)
        )
    }
}

/**
 * Çizimi ekranda 4 köşeye gerer; maske varsa el üstünde ve çizdiğin yerlerde gizler.
 * İsteğe bağlı: kılavuz rengi, arka plan zemini, aynalama ve ızgara.
 */
@Composable
internal fun TraceOverlay(
    bitmap: () -> Bitmap?,
    corners: () -> FloatArray?,
    opacity: () -> Float,
    mask: () -> Bitmap?,
    tint: () -> Color?,
    backdrop: () -> Color?,
    mirror: () -> Boolean,
    grid: () -> Boolean
) {
    val matrix = remember { android.graphics.Matrix() }
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
    val backdropPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
    val gridPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    val maskPaint = remember {
        Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
    }
    val full = remember { RectF() }
    val layer = remember { RectF() }
    // Renk süzgeçleri bir kere oluşturulur (her çizimde yenisi yapılmasın)
    val filters = remember { HashMap<Int, PorterDuffColorFilter>() }
    val gridStroke = with(LocalDensity.current) { 1.5.dp.toPx() }

    Canvas(Modifier.fillMaxSize()) {
        val bmp = bitmap() ?: return@Canvas
        val dst = corners() ?: return@Canvas
        if (dst.size < 8) return@Canvas
        if (!drawingMatrix(bmp.width, bmp.height, dst, mirror(), matrix)) return@Canvas
        val alpha = (opacity() * 255).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return@Canvas // yanıp sönerken tamamen görünmez: hiç çizme
        paint.alpha = alpha
        val c = tint()
        paint.colorFilter = c?.let { col ->
            val argb = col.toArgb()
            filters.getOrPut(argb) { PorterDuffColorFilter(argb, PorterDuff.Mode.SRC_IN) }
        }
        full.set(0f, 0f, size.width, size.height)
        val m = mask()
        // Ara katmanı tüm ekran yerine sadece çizimin kapladığı alan kadar aç (GPU'yu yormaz)
        if (m != null) {
            layer.set(
                min(min(dst[0], dst[2]), min(dst[4], dst[6])) - 2f,
                min(min(dst[1], dst[3]), min(dst[5], dst[7])) - 2f,
                max(max(dst[0], dst[2]), max(dst[4], dst[6])) + 2f,
                max(max(dst[1], dst[3]), max(dst[5], dst[7])) + 2f
            )
            if (!layer.intersect(full)) return@Canvas // çizim tamamen ekran dışında
        }

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val save = if (m != null) nc.saveLayer(layer, null) else nc.save()
            nc.save()
            nc.concat(matrix)
            // Arka plan: çizimin altına düz zemin (saydamlık ve el maskesi zemine de uygulanır)
            backdrop()?.let { bg ->
                backdropPaint.color = bg.toArgb()
                backdropPaint.alpha = alpha
                nc.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), backdropPaint)
            }
            nc.drawBitmap(bmp, 0f, 0f, paint)
            if (grid()) {
                // Izgara çizimle aynı yerde, kare hücreler; çizgi kalınlığı ekranda sabit kalsın
                val bw = bmp.width.toFloat()
                val bh = bmp.height.toFloat()
                val scale = hypot(dst[2] - dst[0], dst[3] - dst[1]) / bw
                gridPaint.strokeWidth = gridStroke / max(scale, 0.0001f)
                // Kılavuz mavi değilse ızgara mavi, kılavuz maviyse pembe olsun
                gridPaint.color = (if (c == GuideColors[2]) Ui.Rose else Ui.AccentDeep).toArgb()
                gridPaint.alpha = (alpha * 0.75f).roundToInt()
                val cell = bw / GRID_COLS
                var x = 0f
                while (x <= bw + 0.5f) { nc.drawLine(x, 0f, x, bh, gridPaint); x += cell }
                var y = 0f
                while (y <= bh + 0.5f) { nc.drawLine(0f, y, bw, y, gridPaint); y += cell }
                if (bh - (y - cell) > cell * 0.2f) nc.drawLine(0f, bh, bw, bh, gridPaint)
            }
            nc.restore()
            if (m != null) nc.drawBitmap(m, null, full, maskPaint)
            nc.restoreToCount(save)
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Ui.Accent,
    activeTrackColor = Ui.Accent,
    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
)

/** Basılı tutulunca tekrar eden küçük tuş (ince ayar için) */
@Composable
private fun RepeatButton(label: String, enabled: Boolean, small: Boolean = false, onStep: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentStep by rememberUpdatedState(onStep)
    val currentEnabled by rememberUpdatedState(enabled)
    val side = if (small) 34.dp else 46.dp
    Box(
        modifier = Modifier
            .padding(2.dp)
            .size(side)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.12f else 0.04f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (currentEnabled) {
                        currentStep()
                        val job = scope.launch {
                            delay(400)
                            while (isActive) {
                                currentStep()
                                delay(70)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            fontSize = if (small) 18.sp else 22.sp
        )
    }
}

/** Yuvarlak, yarı saydam simge düğmesi (geri, kilit, sade görünüm, kayıt, fener, favori, yardım) */
@Composable
private fun RoundIcon(
    glyph: Glyph,
    active: Boolean = false,
    tint: Color? = null,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) Ui.Sun else Ui.Glass)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ToolIcon(
            glyph,
            color = when {
                active -> Ui.OnAccent
                !enabled -> Color.White.copy(alpha = 0.35f)
                tint != null -> tint
                else -> Color.White
            },
            size = size * 0.5f
        )
    }
}

/**
 * Araç çubuğu düğmesi.
 * selected: ayarı açık olan araç. on: aç/kapa özelliği açık (renkli dolu görünür).
 */
@Composable
private fun ToolButton(
    glyph: Glyph,
    label: String,
    selected: Boolean = false,
    on: Boolean = false,
    onColor: Color = Ui.Accent,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when {
        on -> onColor
        selected -> Color.White.copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (enabled) bg else Color.White.copy(alpha = 0.04f))
                .border(2.dp, if (selected) Ui.Accent else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            ToolIcon(
                glyph,
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.3f)
                    on -> Ui.OnAccent
                    else -> Color.White
                },
                size = 22.dp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (!enabled) Ui.Muted.copy(alpha = 0.4f) else if (on || selected) Color.White else Ui.Muted,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (on || selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2
        )
    }
}

/** Seçili aracın ayar alanı: başlık ve içerik */
@Composable
private fun ToolControl(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp)
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** Ders adımları: dersin adı, adım noktaları, geri ve "Sonraki adım" düğmeleri */
@Composable
private fun LessonBar(lesson: LessonInfo, lang: Lang, onPrev: () -> Unit, onNext: () -> Unit) {
    val l = lesson.lesson
    val last = lesson.step == l.steps.lastIndex
    val finished = (Library.lessonDone[l.id] ?: 0) >= l.steps.size
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Ui.PanelSolid)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (lesson.step > 0) 0.12f else 0.04f))
                .clickable(enabled = lesson.step > 0, onClick = onPrev),
            contentAlignment = Alignment.Center
        ) { Text("‹", color = Color.White.copy(alpha = if (lesson.step > 0) 1f else 0.3f), fontSize = 22.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(lang.pick(l.name, l.nameEn), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                l.steps.indices.forEach { i ->
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .size(width = if (i == lesson.step) 16.dp else 7.dp, height = 7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i <= lesson.step) l.color else Color.White.copy(alpha = 0.25f))
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(lang.pick("Adım ${lesson.step + 1}/${l.steps.size}", "Step ${lesson.step + 1}/${l.steps.size}"),
                    color = Ui.Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                !last -> lang.pick("Sonraki adım ›", "Next step ›")
                finished -> lang.pick("Bitti ✓", "Done ✓")
                else -> lang.pick("Bitir ✓", "Finish ✓")
            },
            color = Ui.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (last) Ui.Sun else Ui.Mint)
                .clickable(onClick = onNext)
                .padding(horizontal = 16.dp, vertical = 11.dp)
        )
    }
}
