package com.alterationstudio.ilkcizgi

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alterationstudio.ilkcizgi.ui.theme.IlkCizgiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tema ekran kurulmadan önce belirlenir (ilk karede yanlış renk görünmesin).
        // Telefonun koyu modu değişirse Android ekranı yeniden açar, burası tekrar çalışır.
        Ui.systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        Ui.themeMode = Settings.theme(this)
        setContent {
            IlkCizgiTheme(darkTheme = Ui.isDark, dynamicColor = false) {
                AppRoot()
            }
        }
    }
}

// Alt sekmeler
private const val TAB_DRAWINGS = 0
private const val TAB_WORKS = 1
private const val TAB_SHORTCUTS = 2
private const val TAB_SETTINGS = 3

/**
 * Alt sekmeler: Çizimler, Çizimlerim, Kısayollar, Ayarlar.
 * Kategori, fotoğraftan çizim ve çizim ekranları tam ekran açılır.
 * "Nasıl kullanılır" sekme değil: ana sayfadaki kart, ana sayfanın ? düğmesi ya da çizim ekranının ?
 * düğmesiyle her şeyin üstünde açılır; kapatınca kaldığın yere (ör. kilitli çizime) dönülür.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    // Küçük kayıtlar (ayarlar, favoriler, eserler listesi) hemen okunur
    remember {
        Library.init(context)
        UserSketches.init(context)
        Works.init(context)
        LastSession.init(context)
        RecentSettings.init(context)
        // Önceki açılışta yarım kalmış video kayıtlarının geçici dosyalarını temizle (süreç başına bir kere)
        cleanStaleTimelapse(context)
        true
    }
    // Çizim kataloğu (klasörler + json) arka planda okunur: açılışta ekran donmasın
    // Bir kere okunur ve saklanır: ekran dönünce ya da uygulamaya geri gelince tekrar okunmaz
    val catalog by produceState(CatalogStore.cached) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { Catalog(loadCategories(context), loadLessons(context)) }
                    .getOrElse { Catalog(emptyList(), emptyList()) } // okunamazsa uygulama çökmesin
            }.also { CatalogStore.cached = it }
        }
    }
    // Ekranlar arası geçişte kaydırma yeri ve seçili sekme gibi durumlar korunur
    val screens = rememberSaveableStateHolder()
    val categories = catalog?.categories ?: emptyList()
    val lessons = catalog?.lessons ?: emptyList()
    // Yoldan çizimi bulmak için sözlük (her aramada listeyi baştan taramasın)
    val byPath = remember(categories) {
        HashMap<String, Pair<CatalogItem, Color>>().apply {
            categories.forEach { c -> c.all.forEach { put(it.path, it to c.color) } }
        }
    }
    val lookup: (String) -> Pair<CatalogItem, Color>? = remember(byPath) {
        { path ->
            byPath[path] ?: UserSketches.items.firstOrNull { it.path == path }?.let { it to Ui.Rose }
        }
    }
    // Adı değişmiş ya da silinmiş çizimleri favori / son açılan listelerinden temizle
    LaunchedEffect(catalog) {
        val c = catalog ?: return@LaunchedEffect
        Library.prune(context, isValid = { lookup(it) != null }, lessonIds = c.lessons.map { it.id }.toSet())
    }

    var lang by remember { mutableStateOf(Settings.lang(context)) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_DRAWINGS) }
    var categoryFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var drawingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var photoFlow by rememberSaveable { mutableStateOf(false) }
    var lessonId by rememberSaveable { mutableStateOf<String?>(null) }
    // Ana sayfadan açılan sade ekran: dersler, son açtıkların, favoriler, fotoğraflarım
    var homeSection by rememberSaveable { mutableStateOf<String?>(null) }
    // İlk açılışta tanıtım (Ayarlar'dan tekrar açılabilir)
    var showIntro by rememberSaveable { mutableStateOf(!Settings.introSeen(context)) }
    var lessonStep by rememberSaveable { mutableIntStateOf(0) }
    // Nasıl kullanılır: üstte açılan katman (alttaki ekran, çizim ekranı dahil, olduğu gibi kalır)
    var showHelp by rememberSaveable { mutableStateOf(false) }
    // Devam Et ile mi açıldı? Öyleyse çizim son kaydedilen yer, boyut ve ayarlarla açılır
    var resume by rememberSaveable { mutableStateOf(false) }
    // Son açtıkların listesinden mi açıldı? Öyleyse o çizimin en son bırakıldığı ayarlarla açılır
    var fromRecent by rememberSaveable { mutableStateOf(false) }
    // Önizle: kamerasız önizleme ekranındaki çizim (ve Son açtıklarından açıldıysa onun ayarları)
    var previewPath by rememberSaveable { mutableStateOf<String?>(null) }
    var previewFromRecent by rememberSaveable { mutableStateOf(false) }
    // Önizlemede "Çizmeye başla" ile seçilen ayarlar: çizim ekranı bunlarla açılır
    var previewStart by remember { mutableStateOf<LastDrawing?>(null) }
    val lesson = lessons.firstOrNull { it.id == lessonId }

    val category = categories.firstOrNull { it.folder == categoryFolder }
    val drawing = drawingPath?.let(lookup)
    val preview = previewPath?.let(lookup)

    fun openDrawing(path: String) {
        Library.addRecent(context, path)
        resume = false
        fromRecent = false
        previewStart = null
        drawingPath = path
    }

    /** Son açtıklarından: çizim en son bıraktığın yer, boyut ve ayarlarla açılır (kayıt yoksa varsayılanlarla) */
    fun openRecent(path: String) {
        resume = false
        fromRecent = true
        previewStart = null
        drawingPath = path
        Library.addRecent(context, path)
    }

    /** Önizle: kamera açılmadan bakılır (son açtıklarına eklenmez; çizmeye başlayınca eklenir) */
    fun openPreview(path: String, recent: Boolean) {
        previewFromRecent = recent
        previewPath = path
    }

    /** Önizlemeden "Çizmeye başla": çizim ekranı önizlemedeki ayarlarla açılır */
    fun startFromPreview(d: LastDrawing) {
        Library.addRecent(context, d.path)
        resume = false
        fromRecent = false
        previewStart = d
        previewPath = null
        drawingPath = d.path
    }

    // Son kaydedilen durum, çizim açıldığı anda bir kere alınır (sonraki kayıtlar açık çizimi etkilemez)
    val resumeState = remember(resume, fromRecent, drawingPath, lessonId, previewStart) {
        val fromPreview = previewStart
        when {
            fromPreview != null && fromPreview.path == drawingPath -> fromPreview
            resume -> LastSession.current
            fromRecent -> drawingPath?.let { RecentSettings.get(it) }
            else -> null
        }
    }

    /** Ortadaki Devam Et: en son çizimi aynı yer, boyut ve ayarlarla açar */
    fun resumeLast() {
        val last = LastSession.current
        if (last == null) {
            Toast.makeText(context, lang.pick("Önce bir çizim aç; sonra buradan kaldığın yerden devam edersin.",
                "Open a drawing first; you can carry on from here later."), Toast.LENGTH_SHORT).show()
            return
        }
        val l = last.lessonId?.let { id -> lessons.firstOrNull { it.id == id } }
        if (l != null) {
            lessonStep = last.lessonStep.coerceIn(0, l.steps.lastIndex)
            resume = true
            lessonId = l.id
            return
        }
        if (lookup(last.path) == null) {
            Toast.makeText(context, lang.pick("Son çizimin artık yok.", "Your last drawing isn't available any more."), Toast.LENGTH_SHORT).show()
            return
        }
        Library.addRecent(context, last.path)
        resume = true
        fromRecent = false
        previewStart = null
        drawingPath = last.path
    }

    // Durum çubuğu simgeleri: açık zeminde koyu, koyu zeminde (ve kamerada) açık
    val view = LocalView.current
    val dark = Ui.isDark
    val onCamera = (lesson != null || drawing != null) && !showHelp
    LaunchedEffect(dark, onCamera) {
        val act = view.context as? ComponentActivity ?: return@LaunchedEffect
        val darkBars = dark || onCamera
        // Simge rengi ve alt çubuğun zemini birlikte ayarlanır (eski telefonlarda da doğru görünsün)
        act.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
            ) { darkBars },
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.argb(0xE6, 0xFF, 0xFF, 0xFF),
                android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)
            ) { darkBars }
        )
    }

    CompositionLocalProvider(LocalLang provides lang) {
      Box(Modifier.fillMaxSize()) {
        when {
            showIntro -> {
                val finish = { Settings.setIntroSeen(context, true); showIntro = false }
                BackHandler { finish() }
                IntroScreen(onDone = finish)
            }
            // Katalog yüklenirken (çok kısa) sade bir açılış ekranı
            catalog == null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ui.Paper),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo_mark),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
            lesson != null -> {
                BackHandler { lessonId = null }
                val step = lessonStep.coerceIn(0, lesson.steps.lastIndex)
                DrawingScreen(
                    drawing = lesson.steps[step].item,
                    onBack = { lessonId = null },
                    onHelp = { showHelp = true },
                    lesson = LessonInfo(lesson, step) { lessonStep = it.coerceIn(0, lesson.steps.lastIndex) },
                    resume = resumeState?.takeIf { it.lessonId == lesson.id }
                )
            }
            drawing != null -> {
                BackHandler { drawingPath = null }
                DrawingScreen(
                    drawing = drawing.first,
                    onBack = { drawingPath = null },
                    onHelp = { showHelp = true },
                    resume = resumeState?.takeIf { it.path == drawing.first.path }
                )
            }
            // Önizle: kategori / arama / kısayolların üstünde açılır, geri ile listeye dönülür
            preview != null -> {
                BackHandler { previewPath = null }
                val path = preview.first.path
                val previewInit = remember(path, previewFromRecent) {
                    if (previewFromRecent) RecentSettings.get(path) else null
                }
                // Başka bir çizimin önizlemesine geçilirse ayarlar baştan kurulsun
                key(path) {
                    PreviewScreen(
                        drawing = preview.first,
                        color = preview.second,
                        start = previewInit,
                        onBack = { previewPath = null },
                        onStart = { startFromPreview(it) }
                    )
                }
            }
            photoFlow -> {
                BackHandler { photoFlow = false }
                PhotoSketchScreen(
                    onBack = { photoFlow = false },
                    onCreated = { photoFlow = false; openDrawing(it.path) }
                )
            }
            // Kategori, Kategoriler ekranının üstünde açılır (geri ile Kategoriler'e dönülür)
            category != null -> {
                BackHandler { categoryFolder = null }
                screens.SaveableStateProvider("kategori:${category.folder}") {
                    CategoryScreen(
                        category = category,
                        onBack = { categoryFolder = null },
                        onOpen = { openDrawing(it.path) },
                        onPreview = { openPreview(it.path, false) }
                    )
                }
            }
            homeSection == SECTION_CATEGORIES -> {
                BackHandler { homeSection = null }
                screens.SaveableStateProvider("kategoriler") {
                    CategoriesScreen(
                        categories = categories,
                        onBack = { homeSection = null },
                        onOpenCategory = { categoryFolder = it.folder }
                    )
                }
            }
            homeSection == SECTION_LESSONS -> {
                BackHandler { homeSection = null }
                screens.SaveableStateProvider("dersler") { LessonsScreen(
                    lessons = lessons,
                    onBack = { homeSection = null },
                    onOpenLesson = { l ->
                        // Kaldığın adımdan devam et (bitmişse baştan)
                        val done = Library.lessonDone[l.id] ?: 0
                        lessonStep = if (done >= l.steps.size) 0 else done
                        resume = false
                        fromRecent = false
                        lessonId = l.id
                    }
                ) }
            }
            homeSection == SECTION_SEARCH -> {
                // Aramadan çıkınca yazı ve filtreler sıfırlanır; çizim açıp geri gelince korunur
                val closeSearch = { screens.removeState("ara"); homeSection = null }
                BackHandler { closeSearch() }
                screens.SaveableStateProvider("ara") {
                    SearchScreen(
                        categories = categories,
                        onBack = closeSearch,
                        onOpenDrawing = { openDrawing(it) },
                        onPreviewDrawing = { openPreview(it, false) }
                    )
                }
            }
            homeSection != null -> {
                BackHandler { homeSection = null }
                val sec = homeSection ?: SECTION_RECENT
                screens.SaveableStateProvider("bolum:$sec") {
                    CollectionScreen(
                        section = sec,
                        lookup = lookup,
                        onBack = { homeSection = null },
                        onOpenDrawing = { openDrawing(it) },
                        onOpenRecent = { openRecent(it) },
                        onPreview = { p, recent -> openPreview(p, recent) }
                    )
                }
            }
            else -> {
                if (tab != TAB_DRAWINGS) BackHandler { tab = TAB_DRAWINGS }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Ui.Paper)
                ) {
                    Box(Modifier.weight(1f)) {
                        screens.SaveableStateProvider("sekme:$tab") {
                        when (tab) {
                            TAB_WORKS -> WorksScreen(onDraw = { tab = TAB_DRAWINGS })
                            TAB_SHORTCUTS -> ShortcutsScreen(
                                lookup = lookup,
                                onOpenDrawing = { openDrawing(it) },
                                onOpenRecent = { openRecent(it) },
                                onPreview = { p, recent -> openPreview(p, recent) }
                            )
                            TAB_SETTINGS -> SettingsScreen(
                                lang = lang,
                                onLangChange = { lang = it; Settings.setLang(context, it) },
                                onShowIntro = { showIntro = true }
                            )
                            else -> HomeScreen(
                                categories = categories,
                                lessons = lessons,
                                onOpenSection = { homeSection = it },
                                onHowTo = { showHelp = true },
                                onPhoto = { photoFlow = true }
                            )
                        }
                        }
                    }
                    BottomTabs(
                        selected = tab,
                        onSelect = { tab = it },
                        canResume = LastSession.current != null,
                        onResume = { resumeLast() }
                    )
                }
            }
        }

        // Nasıl kullanılır katmanı: her şeyin üstünde, geri ile kapanır
        if (showHelp && !showIntro) {
            BackHandler { showHelp = false }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ui.Paper)
                    // Boş yerlere dokunma alttaki ekrana (ör. kamera düğmelerine) geçmesin
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                HowToScreen(onBack = { showHelp = false })
            }
        }
      }
    }
}

/** Uygulamadaki çizimler ve dersler */
private class Catalog(val categories: List<Category>, val lessons: List<Lesson>)

/** Katalog uygulama açık kaldıkça bellekte tutulur */
private object CatalogStore {
    @Volatile var cached: Catalog? = null
}

/**
 * Alt sekme çubuğu: solda 2, ortada biraz daha büyük "Devam Et", sağda 2.
 * Her sekmenin kendi rengi var. Devam Et sekme değil, son çizimi açan düğmedir.
 */
@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit, canResume: Boolean, onResume: () -> Unit) {
    val tabs = listOf(
        Triple(L("Çizimler", "Drawings"), Ui.Accent, TAB_DRAWINGS),
        Triple(L("Çizimlerim", "My art"), Ui.Rose, TAB_WORKS),
        Triple(L("Kısayollar", "Shortcuts"), Ui.Sun, TAB_SHORTCUTS),
        Triple(L("Ayarlar", "Settings"), Ui.Violet, TAB_SETTINGS)
    )
    @Composable
    fun RowScope.Tab(label: String, color: Color, index: Int) {
        val isSel = selected == index
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isSel) Ui.tint(color, 0.35f) else Color.Transparent)
                .clickable { onSelect(index) }
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabIcon(index, if (isSel) Ui.deep(color, 0.55f) else Ui.InkMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = if (isSel) Ui.deep(color, 0.45f) else Ui.InkMuted,
                fontSize = 11.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp)
            .background(Ui.PaperCard)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.take(2).forEach { (label, color, index) -> Tab(label, color, index) }
        // Ortadaki Devam Et: diğerlerinden biraz büyük, dolu mavi daire
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onResume)
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .shadow(if (canResume) 4.dp else 0.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (canResume) Ui.Accent else Ui.PaperLine),
                contentAlignment = Alignment.Center
            ) { ToolIcon(Glyph.Resume, if (canResume) Ui.OnAccent else Ui.InkMuted, size = 22.dp) }
            Spacer(Modifier.height(3.dp))
            Text(
                L("Devam Et", "Continue"),
                color = if (canResume) Ui.deep(Ui.Accent, 0.45f) else Ui.InkMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        tabs.drop(2).forEach { (label, color, index) -> Tab(label, color, index) }
    }
}

/** Basit çizilmiş sekme simgeleri (ek kütüphane gerektirmez) */
@Composable
private fun TabIcon(index: Int, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.1f)
        when (index) {
            TAB_DRAWINGS -> { // 4 kare: çizim galerisi
                val g = s * 0.12f
                val c = (s - g) / 2f
                for (i in 0..1) for (j in 0..1) {
                    drawRoundRect(
                        color, Offset(i * (c + g), j * (c + g)), Size(c, c),
                        CornerRadius(s * 0.1f), style = stroke
                    )
                }
            }
            TAB_WORKS -> { // çerçeveli resim: dağ ve güneş
                drawRoundRect(color, Offset(s * 0.06f, s * 0.12f), Size(s * 0.88f, s * 0.76f),
                    CornerRadius(s * 0.1f), style = stroke)
                drawCircle(color, s * 0.08f, Offset(s * 0.68f, s * 0.36f))
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.14f, s * 0.78f)
                    lineTo(s * 0.4f, s * 0.48f)
                    lineTo(s * 0.58f, s * 0.66f)
                    lineTo(s * 0.7f, s * 0.56f)
                    lineTo(s * 0.86f, s * 0.78f)
                }
                drawPath(path, color, style = Stroke(width = s * 0.09f))
            }
            TAB_SHORTCUTS -> { // yıldız
                val path = androidx.compose.ui.graphics.Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) s * 0.48f else s * 0.2f
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val x = s / 2 + (r * kotlin.math.cos(a)).toFloat()
                    val y = s * 0.53f + (r * kotlin.math.sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color, style = Stroke(width = s * 0.09f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            else -> { // iki ayar kaydırıcısı
                drawLine(color, Offset(s * 0.08f, s * 0.32f), Offset(s * 0.92f, s * 0.32f), s * 0.1f)
                drawLine(color, Offset(s * 0.08f, s * 0.7f), Offset(s * 0.92f, s * 0.7f), s * 0.1f)
                drawCircle(color, s * 0.13f, Offset(s * 0.35f, s * 0.32f))
                drawCircle(color, s * 0.13f, Offset(s * 0.65f, s * 0.7f))
            }
        }
    }
}
