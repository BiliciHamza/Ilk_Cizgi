package com.alterationstudio.ilkcizgi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.max

/**
 * Uygulamanın renkleri.
 * Ana vurgu, çizerlerin taslakta kullandığı açık mavi kalemden ("non-photo blue") geliyor.
 * Her kategorinin de kendi kalem rengi var.
 */
object Ui {
    /**
     * Koyu tema açık mı? Ekranlar bu değeri okuduğu için tema değişince hepsi kendiliğinden yenilenir.
     * Sadece kâğıt/yazı renkleri temaya göre değişir; kamera ekranı zaten koyu.
     */
    private val modeState = mutableStateOf(0) // 0 telefona uy, 1 açık, 2 koyu

    /** Ayarlardaki tema seçimi (değişince bütün ekranlar yeni renklerle yeniden çizilir) */
    var themeMode: Int
        get() = modeState.value
        set(v) { modeState.value = v }

    /** Telefonun kendi koyu modu (uygulama açılırken MainActivity yazar) */
    var systemDark = false

    val isDark: Boolean
        get() = when (modeState.value) {
            1 -> false
            2 -> true
            else -> systemDark
        }

    // Kâğıt (açık temada kâğıt beyazı, koyu temada grafit)
    // Koyu temada simsiyah değil, yumuşak koyu gri
    val Paper: Color get() = if (isDark) Color(0xFF2B2E34) else Color(0xFFF3F4F0)
    val PaperCard: Color get() = if (isDark) Color(0xFF373B42) else Color(0xFFFFFFFF)
    val PaperLine: Color get() = if (isDark) Color(0xFF4A4F58) else Color(0xFFE2E4DE)
    val Ink: Color get() = if (isDark) Color(0xFFECEEF1) else Color(0xFF1F232A)
    val InkMuted: Color get() = if (isDark) Color(0xFF9AA1AB) else Color(0xFF6B717B)

    // Grafit (kamera ekranı ve koyu kartlar)
    val Graphite = Color(0xFF23272E)
    val Panel = Color(0xC723272E)       // biraz saydam: arkadaki çizim görünsün
    val PanelSolid = Color(0xF023272E)
    val Glass = Color(0x99141619)
    val Muted = Color(0xFFA7ADB6)

    // Mavi kalem
    val Accent = Color(0xFF7CC6E8)
    val AccentDeep = Color(0xFF2F7FA8)
    val OnAccent = Color(0xFF10293A)
    val Danger = Color(0xFFFF6B6B)

    // Renkli kalemler (kategoriler ve vurgular)
    val Mint = Color(0xFF6FD3A0)
    val Violet = Color(0xFFB59CFF)
    val Sun = Color(0xFFFFCC4D)
    val Rose = Color(0xFFFF8FB1)

    /** Sırayla kategorilere verilen renkler */
    val CategoryPalette = listOf(Accent, Mint, Violet, Sun, Rose)

    /**
     * Bir rengin zemin hâli (kart arka planı için).
     * Açık temada beyaza, koyu temada koyu karta doğru karıştırılır.
     */
    fun tint(c: Color, amount: Float = 0.18f): Color {
        if (!isDark) return Color(
            red = 1f - (1f - c.red) * amount,
            green = 1f - (1f - c.green) * amount,
            blue = 1f - (1f - c.blue) * amount
        )
        val b = DarkBase
        val a = amount * 0.8f
        return Color(
            red = b.red + (c.red - b.red) * a,
            green = b.green + (c.green - b.green) * a,
            blue = b.blue + (c.blue - b.blue) * a
        )
    }

    /**
     * Rengin yazı hâli (zemin üstünde okunur olsun diye).
     * Açık temada koyulaştırılır, koyu temada açılır.
     */
    fun deep(c: Color, amount: Float = 0.45f): Color {
        if (!isDark) return Color(red = c.red * amount, green = c.green * amount, blue = c.blue * amount)
        val k = amount * 0.6f
        return Color(
            red = 1f - (1f - c.red) * k,
            green = 1f - (1f - c.green) * k,
            blue = 1f - (1f - c.blue) * k
        )
    }

    private val DarkBase = Color(0xFF373B42)

    /**
     * Çizimlerin durduğu kâğıt: iki temada da beyaz. Çizimler hep siyah görünsün diye
     * koyu temada da bu alanlar açık kalır (kâğıt gibi).
     */
    val DrawPaper = Color(0xFFFFFFFF)

    /** Çizim kâğıdı üstündeki hafif renk (kareli çizgiler, zemin) — temadan bağımsız, hep açık */
    fun paperTint(c: Color, amount: Float = 0.18f) = Color(
        red = 1f - (1f - c.red) * amount,
        green = 1f - (1f - c.green) * amount,
        blue = 1f - (1f - c.blue) * amount
    )
}

// ---------------------------------------------------------------------------------------------
// Dil (Türkçe / İngilizce)
// ---------------------------------------------------------------------------------------------

enum class Lang { TR, EN }

val LocalLang = compositionLocalOf { Lang.TR }

/** Ekrandaki her yazı bunun üzerinden gelir: L("Türkçe", "English") */
@Composable
@ReadOnlyComposable
fun L(tr: String, en: String): String = if (LocalLang.current == Lang.EN) en else tr

/** Composable olmayan yerler için (ör. liste dönüşümleri içinde) */
fun Lang.pick(tr: String, en: String): String = if (this == Lang.EN) en else tr

/** Dil seçimi telefonda saklanır. Hiç seçilmediyse telefonun diline bakılır. */
object Settings {
    private const val PREFS = "ayarlar"
    private const val KEY_LANG = "dil"

    fun lang(context: Context): Lang {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, null)
        return when (saved) {
            "tr" -> Lang.TR
            "en" -> Lang.EN
            else -> if (Locale.getDefault().language == "tr") Lang.TR else Lang.EN
        }
    }

    fun setLang(context: Context, lang: Lang) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANG, if (lang == Lang.EN) "en" else "tr").apply()
    }

    /** Tema: 0 telefona uy, 1 açık, 2 koyu */
    fun theme(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("tema", 0).coerceIn(0, 2)

    fun setTheme(context: Context, v: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("tema", v).apply()
    }

    /** Tanıtım bir kere gösterilir (Ayarlar'dan tekrar açılabilir) */
    fun introSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("tanitim_goruldu", false)

    fun setIntroSeen(context: Context, v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("tanitim_goruldu", v).apply()
    }

    /** Çizim ekranı ayarları: her çizim bunlarla açılır */
    fun drawPrefs(context: Context): DrawPrefs {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = DrawPrefs()
        return DrawPrefs(
            opacity = p.getFloat("saydamlik", d.opacity).coerceIn(0.1f, 1f),
            colorIndex = p.getInt("kilavuz_rengi", d.colorIndex).coerceIn(0, GuideColors.lastIndex),
            handFront = p.getBoolean("el_onde", d.handFront),
            hideDrawn = p.getBoolean("cizdiklerimi_gizle", d.hideDrawn),
            grid = p.getBoolean("izgara", d.grid),
            blink = p.getBoolean("yanip_sonme", d.blink),
            blinkSeconds = p.getInt("yanip_sonme_saniye", d.blinkSeconds).coerceIn(1, MAX_BLINK_SECONDS),
            sensitivity = p.getFloat("hassasiyet", d.sensitivity).coerceIn(0f, 1f),
            videoFps = p.getInt("video_fps", d.videoFps).takeIf { it in VIDEO_FPS_OPTIONS } ?: d.videoFps,
            background = p.getInt("arka_plan", d.background).coerceIn(0, BackgroundColors.lastIndex)
        )
    }

    fun setDrawPrefs(context: Context, v: DrawPrefs) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("saydamlik", v.opacity)
            .putInt("kilavuz_rengi", v.colorIndex)
            .putBoolean("el_onde", v.handFront)
            .putBoolean("cizdiklerimi_gizle", v.hideDrawn)
            .putBoolean("izgara", v.grid)
            .putBoolean("yanip_sonme", v.blink)
            .putInt("yanip_sonme_saniye", v.blinkSeconds)
            .putFloat("hassasiyet", v.sensitivity)
            .putInt("video_fps", v.videoFps)
            .putInt("arka_plan", v.background)
            .apply()
    }
}

/**
 * Çizim ekranının başlangıç ayarları (Ayarlar sekmesinden değiştirilir).
 * blinkSeconds: yanıp sönen kılavuz kaç saniyede bir kaybolsun (1–60).
 * sensitivity: algılama hassasiyeti (0–1). Önerilen ve varsayılan 0 (bkz. SENSITIVITY_RECOMMENDED).
 * videoFps: kaydedilen hızlandırılmış videonun saniyedeki kare sayısı (bkz. VIDEO_FPS_OPTIONS).
 * background: çizimin arkasındaki düz zemin (BackgroundColors sırası; 0 = yok, kamera görünür).
 */
data class DrawPrefs(
    val opacity: Float = 0.7f,
    val colorIndex: Int = 0,
    val handFront: Boolean = true,
    val hideDrawn: Boolean = false,
    val grid: Boolean = false,
    val blink: Boolean = false,
    val blinkSeconds: Int = 3,
    val sensitivity: Float = SENSITIVITY_RECOMMENDED,
    val videoFps: Int = VIDEO_FPS_DEFAULT,
    val background: Int = 0
)

const val MAX_BLINK_SECONDS = 60

/**
 * Algılama hassasiyeti (El önde, Çizdiklerimi gizle ve ilerleme yüzdesi birlikte kullanır).
 * 0 en iyi sonucu veriyor: açık kalem izleri de yakalanır, gölgeler el sanılmaz.
 * Arttıkça kâğıttan biraz koyu her yer (gölge dahil) el sayılır ve sadece koyu çizgiler fark edilir.
 * Kaydırıcı 0–10 arası 11 kademe gösterir.
 */
const val SENSITIVITY_RECOMMENDED = 0f
const val SENSITIVITY_STEPS = 9 // 0, 0.1 … 1 (11 kademe)

/**
 * Hızlandırılmış videonun saniyedeki kare sayısı (kaydedilen dosyanın hızı).
 * Kareler her TIMELAPSE_INTERVAL_MS'de (1,2 sn) bir alınır; video hızı = fps × 1,2
 * (3 → ~4×, 6 → ~7×, 12 → ~14×, 24 → ~29×). Varsayılan ~7×.
 */
val VIDEO_FPS_OPTIONS = listOf(3, 6, 12, 24)
const val VIDEO_FPS_DEFAULT = 6

/** Videonun gerçek süreye göre kaç kat hızlı olduğu */
fun videoSpeedOf(fps: Int): Float = fps * TIMELAPSE_INTERVAL_MS / 1000f

/**
 * Yanıp sönen kılavuz: görünür kalma ve kaybolma süreleri (ms).
 * Kılavuz "seconds" saniye görünür, sonra kısa bir an kaybolur (kısa sürelerde daha da kısa).
 */
fun blinkTimes(seconds: Int): Pair<Long, Long> {
    val shown = seconds.coerceIn(1, MAX_BLINK_SECONDS) * 1000L
    val hidden = if (seconds <= 2) 600L else 1000L
    return shown to hidden
}

/** Kılavuz renkleri: null = çizimin kendi rengi (siyah) */
val GuideColors: List<Color?> = listOf(null, Color(0xFFE53935), Color(0xFF1E6FE0), Color(0xFF2E9E4F))

/**
 * Çizim ekranındaki "Arka plan": çizimin arkasına konan düz zemin (resme bakarak çizmek için).
 * null = arka plan yok (kamera görüntüsü görünür). Sıra: yok, beyaz, siyah, kırmızı, mavi, yeşil.
 * Renkli zeminler çizgiler okunsun diye biraz açık tutuldu.
 */
val BackgroundColors: List<Color?> = listOf(
    null, Color.White, Color(0xFF141414), Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784)
)
private const val BACKGROUND_BLACK = 2
// Renkli zemin → aynı renkteki kılavuz (zeminle aynı renk çizgi görünmez)
private val BackgroundGuideClash = mapOf(3 to 1, 4 to 2, 5 to 3)

/**
 * Arka plana göre çizgi rengi: çizgiler her zeminde okunsun.
 * Siyah zeminde siyah çizgi beyaz olur; zeminle aynı renkteki kılavuz çizimin kendi rengine (siyah) döner.
 */
fun guideTintOn(colorIndex: Int, background: Int): Color? {
    val c = GuideColors.getOrNull(colorIndex)
    return when {
        background == BACKGROUND_BLACK -> c ?: Color.White
        BackgroundGuideClash[background] == colorIndex -> null
        else -> c
    }
}

// ---------------------------------------------------------------------------------------------
// Çizimler ve kategoriler
// ---------------------------------------------------------------------------------------------

/**
 * Bir çizim: Türkçe ve İngilizce ad + yolu.
 * Yol "drawing/..." ise uygulamanın içindeki (assets) çizimdir, "file:" ile başlıyorsa
 * kullanıcının fotoğraftan yaptığı çizimdir.
 */
data class CatalogItem(val name: String, val nameEn: String, val path: String)

/** Çizim dosyasını açar (uygulama içi ya da kullanıcının fotoğraftan yaptığı dosya) */
fun openDrawingStream(context: Context, path: String): InputStream =
    if (path.startsWith(FILE_PREFIX)) FileInputStream(appFile(context, path.removePrefix(FILE_PREFIX)))
    else context.assets.open(path)

/**
 * Güvenlik: kayıtlı listelerden gelen dosya yolları sadece uygulamanın kendi klasöründeyse kullanılır.
 * Klasör dışına çıkan (ör. "../" ile) bir yol hata verir; çağıranlar bunu yakalar, uygulama çökmez.
 */
fun appFile(context: Context, path: String): File {
    val file = File(path).canonicalFile
    val root = context.filesDir.canonicalFile
    require(file.path.startsWith(root.path + File.separator)) { "Uygulama klasörü dışında dosya" }
    return file
}

const val FILE_PREFIX = "file:"

/**
 * Kullanıcının çizgiye dönüştürmeden, olduğu gibi kaydettiği fotoğraf mı? (fotograf_cizimleri/foto_*.jpg)
 * Bunlar çizim ekranında renkli fotoğraf olarak gösterilir (mürekkep dönüşümü yapılmaz),
 * kılavuz rengi ve ilerleme yüzdesi bunlarda kullanılmaz.
 */
fun isOriginalPhoto(path: String): Boolean =
    path.startsWith(FILE_PREFIX) && path.endsWith(".jpg", ignoreCase = true)

/** Alt kategori (ör. Anatomi > Eller) */
data class SubCategory(
    val folder: String,
    val name: String,
    val nameEn: String,
    val drawings: List<CatalogItem>
)

/** Ana kategori. Alt kategorileri ve doğrudan içindeki çizimleri olabilir. */
data class Category(
    val folder: String,
    val name: String,
    val nameEn: String,
    val color: Color,
    val subs: List<SubCategory>,
    val loose: List<CatalogItem>
) {
    val all: List<CatalogItem> get() = subs.flatMap { it.drawings } + loose
}

private const val ROOT = "drawing"
private val IMAGE_EXT = listOf(".png", ".jpg", ".jpeg", ".webp")
private fun isImage(name: String) = IMAGE_EXT.any { name.endsWith(it, ignoreCase = true) }
private fun isFolder(name: String) = !name.contains('.')

/** "kedi_yuzu" -> "Kedi yuzu" (çeviri bulunamazsa kullanılır) */
internal fun prettyName(fileName: String): String =
    fileName.substringBeforeLast('.')
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replaceFirstChar { it.uppercase() }

private val NUMBERED = Regex("^(.*)_(\\d+)$")

/** Türkçe alfabetik sıralama (Çapraz, Göz, Önden, Yandan gibi) */
private val TR_COLLATOR: java.text.Collator = java.text.Collator.getInstance(Locale.forLanguageTag("tr"))

private fun parseColor(hex: String?): Color? = runCatching {
    if (hex.isNullOrBlank()) null else Color(android.graphics.Color.parseColor(hex))
}.getOrNull()

/**
 * Bir klasördeki çizimleri ad vererek listeler.
 * "goz_3" -> "Göz 3" / "Eye 3". Klasörde o addan tek bir tane varsa numara yazılmaz.
 */
private fun listItems(
    context: Context, folder: String,
    names: Map<String, String>, namesEn: Map<String, String>
): List<CatalogItem> {
    val files = (context.assets.list(folder) ?: emptyArray()).filter(::isImage)
    val roots = files.map { f ->
        val base = f.substringBeforeLast('.')
        NUMBERED.matchEntire(base)?.groupValues?.get(1) ?: base
    }
    val rootCount = roots.groupingBy { it }.eachCount()

    class Entry(val item: CatalogItem, val label: String, val number: Int)

    return files.map { f ->
        val base = f.substringBeforeLast('.')
        val m = NUMBERED.matchEntire(base)
        val root = m?.groupValues?.get(1) ?: base
        val num = m?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val tr = names[root] ?: prettyName(root)
        val en = namesEn[root] ?: tr
        val showNum = m != null && (rootCount[root] ?: 0) > 1
        Entry(
            CatalogItem(
                if (showNum) "$tr $num" else tr,
                if (showNum) "$en $num" else en,
                "$folder/$f"
            ),
            tr, num
        )
    }
        // Önce ada (Türkçe alfabe sırasıyla: Ç, Ö, Ş doğru yerde), sonra numaraya göre (göz 2, göz 10'dan önce)
        .sortedWith(Comparator<Entry> { a, b -> TR_COLLATOR.compare(a.label, b.label) }.thenBy { it.number })
        .map { it.item }
}

/**
 * assets/drawing altındaki klasörleri kategori olarak okur.
 * Adlar, İngilizce karşılıkları, renkler ve sıra kategoriler.json'dan gelir;
 * orada olmayan klasörler de sona eklenir.
 */
fun loadCategories(context: Context): List<Category> {
    val am = context.assets
    val json = runCatching {
        am.open("$ROOT/kategoriler.json").bufferedReader().use { JSONObject(it.readText()) }
    }.getOrNull()

    fun readMap(key: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        json?.optJSONObject(key)?.let { o -> o.keys().forEach { k -> out[k] = o.optString(k) } }
        return out
    }
    val names = readMap("adlar")
    val namesEn = readMap("adlar_en")

    class SubMeta(val folder: String, val name: String, val nameEn: String)
    class Meta(val name: String, val nameEn: String, val color: Color?, val subs: List<SubMeta>)
    val metaOrder = mutableListOf<String>()
    val meta = mutableMapOf<String, Meta>()
    json?.optJSONArray("kategoriler")?.let { arr ->
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val folder = c.optString("klasor")
            if (folder.isBlank()) continue
            val subs = mutableListOf<SubMeta>()
            c.optJSONArray("alt")?.let { a ->
                for (j in 0 until a.length()) {
                    val s = a.optJSONObject(j) ?: continue
                    val sf = s.optString("klasor")
                    if (sf.isBlank()) continue
                    val sn = s.optString("ad", prettyName(sf))
                    subs += SubMeta(sf, sn, s.optString("ad_en", sn))
                }
            }
            metaOrder += folder
            val n = c.optString("ad", prettyName(folder))
            meta[folder] = Meta(n, c.optString("ad_en", n), parseColor(c.optString("renk")), subs)
        }
    }

    val onDisk = (am.list(ROOT) ?: emptyArray()).filter(::isFolder)
    val order = metaOrder.filter { it in onDisk } + onDisk.filter { it !in metaOrder }.sorted()

    val categories = order.mapIndexedNotNull { index, folder ->
        val path = "$ROOT/$folder"
        val m = meta[folder]
        val subFolders = (am.list(path) ?: emptyArray()).filter(::isFolder)
        val known = m?.subs?.map { it.folder } ?: emptyList()
        val subOrder = known.filter { it in subFolders } + subFolders.filter { it !in known }.sorted()
        val subs = subOrder.map { sf ->
            val sm = m?.subs?.firstOrNull { it.folder == sf }
            SubCategory(
                sf, sm?.name ?: prettyName(sf), sm?.nameEn ?: prettyName(sf),
                listItems(context, "$path/$sf", names, namesEn)
            )
        }.filter { it.drawings.isNotEmpty() }
        val loose = listItems(context, path, names, namesEn)
        Category(
            folder,
            m?.name ?: prettyName(folder),
            m?.nameEn ?: prettyName(folder),
            m?.color ?: Ui.CategoryPalette[index % Ui.CategoryPalette.size],
            subs, loose
        ).takeIf { it.all.isNotEmpty() }
    }

    // drawing klasörünün kökünde kalmış çizimler varsa "Diğer" olarak göster
    val rootItems = listItems(context, ROOT, names, namesEn)
    return if (rootItems.isEmpty()) categories
    else categories + Category("_diger", "Diğer", "Other", Ui.Rose, emptyList(), rootItems)
}

// ---------------------------------------------------------------------------------------------
// Önizleme resimleri (bir kere yüklenir, bellekte saklanır)
// ---------------------------------------------------------------------------------------------

/**
 * Önizleme resmi.
 * bitmap sadece mürekkebin koyuluğunu tutar (ALPHA_8): renkli resme göre 4 kat az bellek.
 * Ekranda Ui.Ink rengiyle boyanarak çizilir (Thumb).
 */
class ThumbInfo(val bitmap: Bitmap) {
    /** Compose'un çizdiği hâli: bir kere oluşturulur, her çizimde yeniden yapılmaz */
    val image: ImageBitmap by lazy(LazyThreadSafetyMode.NONE) { bitmap.asImageBitmap() }
}

private const val THUMB_SIDE = 300 // önizlemenin en uzun kenarı (piksel); kartlarda yeterince net

/** Kaydırıp geri gelince önizlemeler tekrar yüklenmesin diye bellek önbelleği */
object ThumbCache {
    private val cache = object : LruCache<String, ThumbInfo>(
        // Uygulama belleğinin en fazla 1/8'i (KB cinsinden)
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: ThumbInfo) = max(1, value.bitmap.byteCount / 1024)
    }

    fun get(path: String): ThumbInfo? = cache.get(path)

    fun load(context: Context, path: String): ThumbInfo? =
        cache.get(path) ?: loadThumbnail(context, path)?.also { cache.put(path, it) }

    fun remove(path: String) { cache.remove(path) }
}

/**
 * Önizleme üretir: resim küçültülerek açılır, her pikselin mürekkep koyuluğu hesaplanır
 * (koyu + opak = mürekkep).
 */
private fun loadThumbnail(context: Context, path: String): ThumbInfo? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openDrawingStream(context, path).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_SIDE) sample *= 2
    val decoded = openDrawingStream(context, path).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    } ?: return null
    // Tam olarak THUMB_SIDE boyutuna indir (kenarı en fazla 2 kat büyük gelebilir)
    val scale = THUMB_SIDE.toFloat() / max(decoded.width, decoded.height)
    val src = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            max(1, (decoded.width * scale).toInt()),
            max(1, (decoded.height * scale).toInt()),
            true
        ).also { if (it !== decoded) decoded.recycle() }
    } else decoded
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    src.recycle()
    // Sadece koyuluk (alfa) tutan küçük bitmap; satır uzunluğuna (rowBytes) dikkat ederek doldur
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
    val row = out.rowBytes
    val bytes = ByteArray(row * h)
    for (y in 0 until h) {
        val o = y * row
        val i0 = y * w
        for (x in 0 until w) {
            val c = px[i0 + x]
            val a = (c ushr 24) and 0xFF
            val lum = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            bytes[o + x] = ((255 - lum) * a / 255).toByte()
        }
    }
    out.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
    ThumbInfo(out)
}.getOrNull()

// ---------------------------------------------------------------------------------------------
// Favoriler ve son açılanlar (telefonda saklanır, ekranlar otomatik güncellenir)
// ---------------------------------------------------------------------------------------------

object Library {
    private const val PREFS = "kutuphane"
    private const val MAX_RECENT = 12
    val favorites = mutableStateListOf<String>()
    val recents = mutableStateListOf<String>()
    /** Derslerde tamamlanan adım sayısı (ders kimliği -> adım) */
    val lessonDone = mutableStateMapOf<String, Int>()
    private var ready = false

    fun init(context: Context) {
        if (ready) return
        ready = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        favorites.addAll(readList(p.getString("favoriler", null)))
        recents.addAll(readList(p.getString("son", null)))
        runCatching {
            val o = JSONObject(p.getString("dersler", null) ?: "{}")
            o.keys().forEach { k -> lessonDone[k] = o.optInt(k) }
        }
    }

    /** Dersin ilk "done" adımı bitti olarak işaretlenir (geri gitmez) */
    fun markLesson(context: Context, id: String, done: Int) {
        if ((lessonDone[id] ?: 0) >= done) return
        lessonDone[id] = done
        save(context)
    }

    fun isFavorite(path: String) = path in favorites

    fun toggleFavorite(context: Context, path: String) {
        if (path in favorites) favorites.remove(path) else favorites.add(0, path)
        save(context)
    }

    fun addRecent(context: Context, path: String) {
        recents.remove(path)
        recents.add(0, path)
        while (recents.size > MAX_RECENT) recents.removeAt(recents.lastIndex)
        save(context)
    }

    /**
     * Artık olmayan çizimleri (adı değişmiş, silinmiş) listelerden temizler.
     * Uygulama açılışında bir kere çağrılır: sayılar ve listeler hep tutarlı olur.
     */
    fun prune(context: Context, isValid: (String) -> Boolean, lessonIds: Set<String>) {
        val f = favorites.filter(isValid)
        val r = recents.filter(isValid)
        // Ders listesi okunamadıysa (boşsa) ilerlemeye dokunma
        val l = if (lessonIds.isEmpty()) emptyList() else lessonDone.keys.filter { it !in lessonIds }
        if (f.size == favorites.size && r.size == recents.size && l.isEmpty()) return
        favorites.retainAll(f.toSet())
        recents.retainAll(r.toSet())
        l.forEach { lessonDone.remove(it) }
        save(context)
    }

    /** Silinen fotoğraf çizimi listelerden de çıksın */
    fun forget(context: Context, path: String) {
        favorites.remove(path); recents.remove(path)
        save(context)
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("favoriler", JSONArray(favorites.toList()).toString())
            .putString("son", JSONArray(recents.toList()).toString())
            .putString("dersler", JSONObject(lessonDone.toMap()).toString())
            .apply()
    }

    private fun readList(s: String?): List<String> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())
}

// ---------------------------------------------------------------------------------------------
// Fotoğraftan yapılan çizimler (uygulamanın kendi klasöründe)
// Çizgiye dönüştürülenler cizim_<zaman>.png, olduğu gibi kaydedilen fotoğraflar foto_<zaman>.jpg
// ---------------------------------------------------------------------------------------------

object UserSketches {
    val items = mutableStateListOf<CatalogItem>()
    private var ready = false

    private fun dir(context: Context) = File(context.filesDir, "fotograf_cizimleri").apply { mkdirs() }

    fun init(context: Context) {
        if (ready) return
        ready = true
        reload(context)
    }

    private fun reload(context: Context) {
        val files = dir(context).listFiles { f -> f.name.endsWith(".png") || f.name.endsWith(".jpg") }
            ?.sortedBy { it.lastModified() } ?: emptyList()
        items.clear()
        // En yenisi başta
        items.addAll(files.mapIndexed { i, f ->
            CatalogItem("Fotoğrafım ${i + 1}", "My photo ${i + 1}", FILE_PREFIX + f.absolutePath)
        }.reversed())
    }

    /** Yeni çizimi dosyaya yazar (arka planda çağır) */
    fun saveFile(context: Context, bmp: Bitmap): File? = runCatching {
        val f = File(dir(context), "cizim_${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f
    }.getOrNull()

    /** Fotoğrafı dönüştürmeden, olduğu gibi kaydeder (arka planda çağır) */
    fun saveOriginal(context: Context, bmp: Bitmap): File? = runCatching {
        val f = File(dir(context), "foto_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        f
    }.getOrNull()

    /** Kaydedilen dosyayı listeye ekler (ana iş parçacığında çağır) */
    fun register(context: Context, file: File): CatalogItem? {
        reload(context)
        return items.firstOrNull { it.path == FILE_PREFIX + file.absolutePath }
    }

    fun delete(context: Context, item: CatalogItem) {
        if (!item.path.startsWith(FILE_PREFIX)) return
        runCatching { appFile(context, item.path.removePrefix(FILE_PREFIX)).delete() }
        ThumbCache.remove(item.path)
        Library.forget(context, item.path)
        LastSession.forgetIf(context, item.path)
        reload(context)
    }
}

// ---------------------------------------------------------------------------------------------
// Devam Et: en son açık olan çizim ve o çizimdeki ayarlar (yer, boyut, açı, saydamlık …)
// Çizim ekranı açıkken değişiklikler kısa aralıklarla buraya yazılır; yeni bir çizim açılınca onunla değişir.
// Son açtıkların: aynı ayarlar son açılan 12 çizimin her biri için ayrıca tutulur (RecentSettings).
// Kilit kaydedilmez: telefon kıpırdamış olabilir, kullanıcı hizayı kontrol edip yeniden kilitler.
// ---------------------------------------------------------------------------------------------

data class LastDrawing(
    val path: String,
    val lessonId: String?,
    val lessonStep: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val rotation: Float,
    val mirror: Boolean,
    val opacity: Float,
    val colorIndex: Int,
    val grid: Boolean,
    val blink: Boolean,
    val handFront: Boolean,
    val hideDrawn: Boolean,
    val sensitivity: Float,
    val background: Int = 0   // BackgroundColors sırası; 0 = arka plan yok
)

private fun LastDrawing.toJson(): JSONObject {
    val d = this
    return JSONObject().apply {
        put("yol", d.path)
        d.lessonId?.let { put("ders", it) }
        put("adim", d.lessonStep)
        put("x", d.x.toDouble()); put("y", d.y.toDouble()); put("w", d.w.toDouble())
        put("aci", d.rotation.toDouble()); put("ayna", d.mirror)
        put("saydamlik", d.opacity.toDouble()); put("renk", d.colorIndex)
        put("izgara", d.grid); put("yanip_sonme", d.blink)
        put("el_onde", d.handFront); put("cizdiklerimi_gizle", d.hideDrawn)
        put("hassasiyet", d.sensitivity.toDouble())
        put("arka_plan", d.background)
    }
}

private fun lastDrawingOf(o: JSONObject) = LastDrawing(
    path = o.getString("yol"),
    lessonId = if (o.has("ders")) o.optString("ders") else null,
    lessonStep = o.optInt("adim", 0),
    x = o.optDouble("x", 0.5).toFloat(),
    y = o.optDouble("y", 0.5).toFloat(),
    w = o.optDouble("w", 0.7).toFloat().coerceIn(0.15f, 1.6f),
    rotation = o.optDouble("aci", 0.0).toFloat(),
    mirror = o.optBoolean("ayna", false),
    opacity = o.optDouble("saydamlik", 0.7).toFloat().coerceIn(0.1f, 1f),
    colorIndex = o.optInt("renk", 0).coerceIn(0, GuideColors.lastIndex),
    grid = o.optBoolean("izgara", false),
    blink = o.optBoolean("yanip_sonme", false),
    handFront = o.optBoolean("el_onde", true),
    hideDrawn = o.optBoolean("cizdiklerimi_gizle", false),
    sensitivity = o.optDouble("hassasiyet", 0.0).toFloat().coerceIn(0f, 1f),
    background = o.optInt("arka_plan", 0).coerceIn(0, BackgroundColors.lastIndex)
)

object LastSession {
    private const val PREFS = "devam_et"
    private const val KEY = "son_cizim"

    /** Ekranlar (ortadaki Devam Et sekmesi) bunu okur; değişince kendiliğinden yenilenir */
    var current by mutableStateOf<LastDrawing?>(null)
        private set
    private var ready = false

    fun init(context: Context) {
        if (ready) return
        ready = true
        current = runCatching {
            val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return@runCatching null
            lastDrawingOf(JSONObject(s))
        }.getOrNull()
    }

    /** Son durumu kaydeder (ana iş parçacığında çağır) */
    fun save(context: Context, v: LastDrawing) {
        if (v == current) return
        current = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, v.toJson().toString()).apply()
    }

    /** Son çizim silindiyse Devam Et boşa çıkar */
    fun forgetIf(context: Context, path: String) {
        if (current?.path != path) return
        current = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

/**
 * Son açtıkların: her çizimin en son bırakıldığı yer, boyut ve ayarlar.
 * Sadece Kısayollar > "Son açtıkların" listesinden açılınca kullanılır; başka yerden açılan çizim
 * Ayarlar'daki varsayılanlarla açılır. Liste Library.recents ile aynıdır (en fazla 12 çizim):
 * listeden düşen çizimin ayarları bir sonraki kayıtta silinir, kayıt hiç büyümez (~5 KB).
 * Dersler burada tutulmaz (dersler kalınan adımdan devam eder). Kilit kaydedilmez.
 */
object RecentSettings {
    private const val PREFS = "son_ayarlar"
    private const val KEY = "cizimler"
    private val byPath = HashMap<String, LastDrawing>()
    private var ready = false

    fun init(context: Context) {
        if (ready) return
        ready = true
        runCatching {
            val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return@runCatching
            val o = JSONObject(s)
            for (k in o.keys()) runCatching { byPath[k] = lastDrawingOf(o.getJSONObject(k)) }
        }
    }

    /** Son açtıklarındaki bir çizimin kayıtlı ayarları (yoksa null: varsayılanlarla açılır) */
    fun get(path: String): LastDrawing? = if (path in Library.recents) byPath[path] else null

    /** Çizim ekranı açıkken kısa aralıklarla çağrılır (ana iş parçacığında). Değişmediyse yazmaz. */
    fun save(context: Context, v: LastDrawing) {
        if (v.lessonId != null) return
        val recent = Library.recents.toSet()
        if (v.path !in recent) return
        val stale = byPath.keys.any { it !in recent }
        if (byPath[v.path] == v && !stale) return
        byPath[v.path] = v
        byPath.keys.retainAll(recent)
        val o = JSONObject()
        byPath.forEach { (k, d) -> o.put(k, d.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }
}

// ---------------------------------------------------------------------------------------------
// Çizmeyi öğren: adım adım dersler (kategoriler.json > "dersler")
// ---------------------------------------------------------------------------------------------

/**
 * Dersin bir adımı. x, y, w: adımın ders tuvalindeki yeri (tuval genişliğine göre oran).
 * Adımlar üst üste hizalı durur: yeni adım, önceki adımda çizdiğin çizgilerin tam üstüne gelir.
 */
class LessonStep(val item: CatalogItem, val x: Float, val y: Float, val w: Float)

/** aspect: tuvalin yüksekliği / genişliği */
class Lesson(
    val id: String,
    val name: String,
    val nameEn: String,
    val color: Color,
    val aspect: Float,
    val steps: List<LessonStep>
)

fun loadLessons(context: Context): List<Lesson> {
    val json = runCatching {
        context.assets.open("$ROOT/kategoriler.json").bufferedReader().use { JSONObject(it.readText()) }
    }.getOrNull() ?: return emptyList()
    val arr = json.optJSONArray("dersler") ?: return emptyList()
    // Klasör listelerini bir kere al: olmayan dosyaları atla
    val listing = HashMap<String, Set<String>>()
    fun exists(path: String): Boolean {
        val folder = path.substringBeforeLast('/', "")
        val files = listing.getOrPut(folder) {
            (context.assets.list(if (folder.isEmpty()) ROOT else "$ROOT/$folder") ?: emptyArray()).toSet()
        }
        return path.substringAfterLast('/') in files
    }
    val out = mutableListOf<Lesson>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val name = o.optString("ad", "Ders ${i + 1}")
        val nameEn = o.optString("ad_en", name)
        val stepsJson = o.optJSONArray("adimlar") ?: continue
        val raw = mutableListOf<Pair<String, JSONObject?>>()
        for (j in 0 until stepsJson.length()) {
            // Adım ya sadece dosya yolu ("insan/eller/basit_el_1.webp") ya da {yol, x, y, w}
            val so = stepsJson.optJSONObject(j)
            val path = so?.optString("yol") ?: stepsJson.optString(j)
            if (path.isNullOrBlank() || !exists(path)) continue
            raw += path to so
        }
        if (raw.size < 2) continue
        val n = raw.size
        val steps = raw.mapIndexed { j, (path, so) ->
            LessonStep(
                CatalogItem("$name (${j + 1}/$n)", "$nameEn (${j + 1}/$n)", "$ROOT/$path"),
                so?.optDouble("x", 0.0)?.toFloat() ?: 0f,
                so?.optDouble("y", 0.0)?.toFloat() ?: 0f,
                so?.optDouble("w", 1.0)?.toFloat() ?: 1f
            )
        }
        out += Lesson(
            o.optString("id", "ders_$i"), name, nameEn,
            parseColor(o.optString("renk")) ?: Ui.CategoryPalette[i % Ui.CategoryPalette.size],
            o.optDouble("oran", 1.4).toFloat(),
            steps
        )
    }
    return out
}
