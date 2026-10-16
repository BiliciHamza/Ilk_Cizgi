package com.alterationstudio.ilkcizgi

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * ÖNİZLE
 * Çizime kamera açmadan bakılır (kamera izni de istenmez). Beyaz kâğıt üstünde, çizim ekranıyla aynı çizimle
 * (TraceOverlay) gösterilir. Elle: bir parmakla kaydır, iki parmakla büyüt/küçült ve döndür.
 * Ayarlar: boyut, aynala, ızgara, kılavuz rengi, arka plan; göz düğmesi ayarları gizler (kâğıt büyür).
 * Büyütülmüş çizimin her kenarına kaydırılarak bakılabilir.
 * "Çizmeye başla" çizim ekranını bu ayarlarla açar; kalan ayarlar (saydamlık, el önde …) Ayarlar'dakidir.
 */

private const val PREVIEW_MIN_W = 0.15f
private const val PREVIEW_MAX_W = 1.6f

@Composable
fun PreviewScreen(
    drawing: CatalogItem,
    color: Color,
    // Son açtıklarından açıldıysa o çizimin en son ayarları (yoksa Ayarlar'daki başlangıç değerleri)
    start: LastDrawing?,
    onBack: () -> Unit,
    onStart: (LastDrawing) -> Unit
) {
    val context = LocalContext.current
    val deep = Ui.deep(color, 0.42f)
    val prefs = remember { Settings.drawPrefs(context) }
    val isPhoto = isOriginalPhoto(drawing.path)

    // Çizim ekranındakiyle aynı resim (beyaz saydam, çizgi koyu); arka planda bir kere yüklenir
    val art by produceState<Bitmap?>(null, drawing.path) {
        value = withContext(Dispatchers.Default) { loadLineArt(context, drawing.path) }
    }
    val aspect = art?.let { it.height.toFloat() / it.width.toFloat() } ?: 1f
    // Hareket dinleyicisi bir kere kurulur: oran ona güncel değeriyle ulaşsın
    val aspectNow by rememberUpdatedState(aspect)

    // Konum önizleme alanının oranı, boyut genişliğinin oranı olarak (çizim ekranıyla aynı anlam)
    var x by remember { mutableFloatStateOf(start?.x?.coerceIn(0f, 1f) ?: 0.5f) }
    var y by remember { mutableFloatStateOf(start?.y?.coerceIn(0f, 1f) ?: 0.5f) }
    var w by remember { mutableFloatStateOf(start?.w ?: 0.7f) }
    var rotation by remember { mutableFloatStateOf(start?.rotation ?: 0f) }
    var mirror by remember { mutableStateOf(start?.mirror ?: false) }
    var gridOn by remember { mutableStateOf(start?.grid ?: prefs.grid) }
    var colorIndex by remember { mutableIntStateOf(start?.colorIndex ?: prefs.colorIndex) }
    var backdropIndex by remember { mutableIntStateOf(start?.background ?: prefs.background) }
    var viewW by remember { mutableIntStateOf(0) }
    var viewH by remember { mutableIntStateOf(0) }
    // Ayarları gizle: kâğıt ekranı kaplar (geri tuşu önce ayarları geri getirir)
    var controlsHidden by remember { mutableStateOf(false) }
    BackHandler(enabled = controlsHidden) { controlsHidden = false }

    /**
     * Çizimin ortası, kâğıtta görünebileceği aralıkta tutulur:
     * kâğıttan küçükse tamamı kâğıtta kalır; büyükse her kenarı kâğıdın kenarına kadar getirilebilir
     * (büyütünce üstüne, altına, yanlarına bakılabilsin). Döndürülmüşse kapladığı dikdörtgene göre.
     */
    fun clampCenter() {
        if (viewW == 0 || viewH == 0) return
        val dw = w * viewW
        val dh = dw * aspectNow
        val r = Math.toRadians(rotation.toDouble())
        val c = abs(cos(r)).toFloat()
        val s = abs(sin(r)).toFloat()
        val bw = dw * c + dh * s
        val bh = dw * s + dh * c
        fun fit(pos: Float, view: Float, size: Float): Float {
            val a = size / 2f
            val b = view - size / 2f
            return pos.coerceIn(minOf(a, b), maxOf(a, b))
        }
        x = fit(x * viewW, viewW.toFloat(), bw) / viewW
        y = fit(y * viewH, viewH.toFloat(), bh) / viewH
    }
    fun setSize(v: Float) { w = v.coerceIn(PREVIEW_MIN_W, PREVIEW_MAX_W); clampCenter() }
    fun reset() { x = 0.5f; y = 0.5f; w = 0.7f; rotation = 0f; clampCenter() }
    // Kâğıdın boyu değişince (ayarlar gizlenip açılınca) ya da çizim yüklenince yer yeniden sınırlanır
    LaunchedEffect(viewW, viewH, aspect) { clampCenter() }
    fun startDrawing() = onStart(
        LastDrawing(
            path = drawing.path, lessonId = null, lessonStep = 0,
            x = x, y = y, w = w, rotation = rotation, mirror = mirror,
            opacity = start?.opacity ?: prefs.opacity, colorIndex = colorIndex, grid = gridOn,
            blink = start?.blink ?: prefs.blink, handFront = start?.handFront ?: prefs.handFront,
            hideDrawn = start?.hideDrawn ?: prefs.hideDrawn, sensitivity = start?.sensitivity ?: prefs.sensitivity,
            background = backdropIndex
        )
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Paper)
    ) {
        // ---------- Başlık ----------
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ui.tint(color, 0.32f))
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
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
            Column(Modifier.weight(1f)) {
                Text(L(drawing.name, drawing.nameEn), color = deep, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(L("Önizleme · kamera açılmaz", "Preview · no camera"), color = Ui.InkMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            // Ayarları gizle / göster
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (controlsHidden) Ui.Sun else Ui.PaperCard)
                    .clickable { controlsHidden = !controlsHidden },
                contentAlignment = Alignment.Center
            ) { ToolIcon(if (controlsHidden) Glyph.Eye else Glyph.EyeOff, deep, size = 20.dp) }
            Spacer(Modifier.width(8.dp))
            HeartButton(drawing.path, size = 40.dp)
        }

        // ---------- Kâğıt ve çizim ----------
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Ui.DrawPaper)
                .border(1.5.dp, Ui.tint(color, 0.45f), RoundedCornerShape(24.dp))
                .onSizeChanged { viewW = it.width; viewH = it.height }
                // Bir parmakla kaydır; iki parmakla büyüt/küçült ve döndür (parmakların arasındaki nokta yerinde kalır)
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, turn ->
                        if (viewW > 0 && viewH > 0) {
                            val newW = (w * zoom).coerceIn(PREVIEW_MIN_W, PREVIEW_MAX_W)
                            val k = newW / w
                            // Ortanın parmak noktasına göre yeri: ölçek ve dönüş uygulanır, sonra kaydırılır
                            val dx = x * viewW - centroid.x
                            val dy = y * viewH - centroid.y
                            val a = Math.toRadians(turn.toDouble())
                            val ca = cos(a).toFloat()
                            val sa = sin(a).toFloat()
                            x = (centroid.x + (dx * ca - dy * sa) * k + pan.x) / viewW
                            y = (centroid.y + (dx * sa + dy * ca) * k + pan.y) / viewH
                            w = newW
                            rotation = (rotation + turn) % 360f
                            clampCenter()
                        }
                    }
                }
        ) {
            TraceOverlay(
                bitmap = { art },
                corners = {
                    if (viewW == 0) null
                    else rotatedRect(x * viewW, y * viewH, w * viewW, w * viewW * aspect, rotation)
                },
                opacity = { 1f },
                mask = { null },
                tint = { if (isPhoto) null else guideTintOn(colorIndex, backdropIndex) },
                backdrop = { if (isPhoto) null else BackgroundColors.getOrNull(backdropIndex) },
                mirror = { mirror },
                grid = { gridOn }
            )
            if (art == null) CircularProgressIndicator(color = deep, modifier = Modifier.align(Alignment.Center))
        }

        // ---------- Ayarlar (kamera gerektirmeyenler) ----------
        // Küçük ekranda kâğıt yeterince büyük kalsın: ayarlar ekranın en fazla yarısını kaplar, sığmazsa kayar
        val maxControls = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
        if (!controlsHidden) Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxControls)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp)
        ) {
            Text(
                L("Sürükle: kaydır · İki parmak: büyüt, küçült ve döndür", "Drag: move · Two fingers: zoom and rotate"),
                color = Ui.InkMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Boyut %${(w * 100).roundToInt()}", "Size ${(w * 100).roundToInt()}%"),
                    color = Ui.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(92.dp))
                StepButton("−", color) { setSize(w - 0.05f) }
                Slider(
                    value = w,
                    onValueChange = { setSize(it) },
                    valueRange = PREVIEW_MIN_W..PREVIEW_MAX_W,
                    colors = SliderDefaults.colors(
                        thumbColor = Ui.deep(color, 0.6f),
                        activeTrackColor = color,
                        inactiveTrackColor = Ui.tint(color, 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )
                StepButton("+", color) { setSize(w + 0.05f) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewAction(Glyph.Mirror, L("Aynala", "Mirror"), color, mirror, Modifier.weight(1f)) { mirror = !mirror }
                PreviewAction(Glyph.Grid, L("Izgara", "Grid"), color, gridOn, Modifier.weight(1f)) { gridOn = !gridOn }
                // Sıfırla: ortaya alır, %70 boyut ve 0° açı; o anki açı burada görünür
                val deg = ((rotation % 360f + 360f) % 360f).roundToInt()
                PreviewAction(Glyph.Reset, L("Sıfırla · $deg°", "Reset · $deg°"), color, false, Modifier.weight(1f)) { reset() }
            }
            // Olduğu gibi kaydedilmiş renkli fotoğrafta renk ve arka plan kullanılmaz (çizim ekranındaki gibi)
            if (!isPhoto) {
                Spacer(Modifier.height(12.dp))
                Text(L("Kılavuz rengi", "Guide colour"), color = Ui.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuideColors.forEachIndexed { i, c ->
                        Swatch(c ?: Color(0xFF111111), colorIndex == i, color) { colorIndex = i }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(L("Arka plan", "Background"), color = Ui.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackgroundColors.forEachIndexed { i, c ->
                        Swatch(c, backdropIndex == i, color) { backdropIndex = i }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- Çizmeye başla ----------
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Ui.Accent, Ui.Mint)))
                .clickable { startDrawing() },
            contentAlignment = Alignment.Center
        ) {
            Text(L("Çizmeye başla →", "Start drawing →"), color = Ui.OnAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Boyut için − / + */
@Composable
private fun StepButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ui.tint(color, 0.25f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Ui.deep(color, 0.45f), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

/** Simgeli küçük ayar düğmesi; aç/kapa ayarlarda açıkken dolu renkli */
@Composable
private fun PreviewAction(glyph: Glyph, label: String, color: Color, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) Ui.tint(color, 0.55f) else Ui.PaperCard)
            .border(1.5.dp, if (on) Ui.deep(color, 0.45f) else Ui.tint(color, 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ToolIcon(glyph, Ui.deep(color, 0.45f), size = 22.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Ui.Ink, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

/** Renk seçimi noktası; null = "yok" (çarpı simgesi) */
@Composable
private fun Swatch(c: Color?, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .border(2.5.dp, if (selected) Ui.deep(accent, 0.45f) else Color.Transparent, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(c ?: Color.Transparent)
            .border(1.dp, Ui.InkMuted.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (c == null) ToolIcon(Glyph.Close, Ui.InkMuted, size = 16.dp)
    }
}
