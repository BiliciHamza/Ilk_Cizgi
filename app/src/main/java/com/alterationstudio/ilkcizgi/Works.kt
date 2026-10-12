package com.alterationstudio.ilkcizgi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/*
 * ÇİZİMLERİM
 * Bitirdiğin çizimlerin fotoğrafları ve hızlandırılmış videoları burada saklanır.
 * Fotoğraflar uygulamanın kendi klasöründedir; videolar telefonun galerisine de kaydedilir.
 */

/** Kayıtlı bir eser: fotoğraf ya da video */
data class Work(
    val id: Long,
    val isVideo: Boolean,
    val file: String,          // fotoğraf: dosya yolu, video: içerik adresi (uri)
    val drawingPath: String,   // hangi çizimden yapıldı (önce/sonra için)
    val name: String,
    val nameEn: String,
    val progress: Int,         // çekildiği andaki ilerleme yüzdesi (-1: bilinmiyor)
    val time: Long
)

object Works {
    val items = mutableStateListOf<Work>()
    private var ready = false

    fun dir(context: Context) = File(context.filesDir, "eserler").apply { mkdirs() }
    private fun index(context: Context) = File(dir(context), "eserler.json")

    fun init(context: Context) {
        if (ready) return
        ready = true
        val list = runCatching {
            val a = JSONArray(index(context).readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Work(
                    o.getLong("id"), o.optBoolean("video"), o.getString("dosya"),
                    o.optString("cizim"), o.optString("ad"), o.optString("ad_en"),
                    o.optInt("ilerleme", -1), o.optLong("zaman")
                )
            }
        }.getOrDefault(emptyList())
        // Dosyası silinmiş fotoğrafları atla
        items.addAll(list.filter { it.isVideo || File(it.file).exists() }.distinctBy { it.id }.sortedByDescending { it.time })
        lastId = items.maxOfOrNull { it.id } ?: 0L
    }

    private fun save(context: Context) {
        val a = JSONArray()
        items.forEach { w ->
            a.put(JSONObject().apply {
                put("id", w.id); put("video", w.isVideo); put("dosya", w.file)
                put("cizim", w.drawingPath); put("ad", w.name); put("ad_en", w.nameEn)
                put("ilerleme", w.progress); put("zaman", w.time)
            })
        }
        runCatching { index(context).writeText(a.toString()) }
    }

    /** Kâğıdın fotoğrafını kaydeder (arka planda çağır) */
    private var lastId = 0L
    /** Benzersiz kimlik (aynı milisaniyede iki kayıt olsa bile çakışmaz) */
    @Synchronized private fun newId(): Long {
        val t = System.currentTimeMillis()
        lastId = if (t > lastId) t else lastId + 1
        return lastId
    }

    fun addPhoto(context: Context, bmp: Bitmap, drawing: CatalogItem, progress: Int): Work? = runCatching {
        val id = newId()
        val f = File(dir(context), "foto_$id.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val w = Work(id, false, f.absolutePath, drawing.path, drawing.name, drawing.nameEn, progress, id)
        addItem(context, w)
        w
    }.getOrNull()

    fun addVideo(context: Context, uri: Uri, drawing: CatalogItem, progress: Int): Work {
        val id = newId()
        val w = Work(id, true, uri.toString(), drawing.path, drawing.name, drawing.nameEn, progress, id)
        addItem(context, w)
        return w
    }

    // Liste ekranda gözüktüğü için ana iş parçacığında değiştirilir
    private fun addItem(context: Context, w: Work) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            items.add(0, w)
            save(context)
        }
    }

    /** Fotoğraf dosyası silinir (sadece uygulamanın kendi klasöründeyse). Video galeride kalır, sadece listeden çıkar. */
    fun delete(context: Context, w: Work) {
        if (!w.isVideo) runCatching { appFile(context, w.file).delete() }
        items.remove(w)
        save(context)
    }
}

// ---------------------------------------------------------------------------------------------
// Paylaşma ve açma
// ---------------------------------------------------------------------------------------------

/** Uygulamanın kendi dosyalarını başka uygulamalara güvenle vermek için adres */
fun fileUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.dosyalar", file)

fun shareUri(context: Context, uri: Uri, mime: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
}

fun openVideo(context: Context, uri: Uri, lang: Lang) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, lang.pick("Video açacak uygulama bulunamadı", "No app found to play the video"),
            Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, lang.pick("Video bulunamadı, silinmiş olabilir", "Video not found, it may have been deleted"),
            Toast.LENGTH_SHORT).show()
    }
}

/** Büyük fotoğrafı küçültüp açar */
fun loadScaledFile(path: String, maxSide: Int): Bitmap? = runCatching {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, b)
    var s = 1
    while (max(b.outWidth, b.outHeight) / (s * 2) >= maxSide) s *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
}.getOrNull()

// Tarih biçimlendiricileri bir kere oluşturulur (her kartta yeniden yapılmasın; sadece ana iş parçacığında kullanılır)
private val dateFormats = HashMap<Lang, DateFormat>()

private fun formatDate(time: Long, lang: Lang): String =
    dateFormats.getOrPut(lang) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, if (lang == Lang.EN) Locale.ENGLISH else Locale.forLanguageTag("tr"))
    }.format(Date(time))

// ---------------------------------------------------------------------------------------------
// Ekran
// ---------------------------------------------------------------------------------------------

@Composable
fun WorksScreen(onDraw: () -> Unit) {
    val lang = LocalLang.current
    var open by remember { mutableStateOf<Work?>(null) }
    val color = Ui.Rose
    val deep = Ui.deep(color, 0.42f)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Ui.Paper)
            .statusBarsPadding()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(L("Çizimlerim", "My drawings"), color = Ui.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (Works.items.isEmpty()) L("Bitirdiğin çizimler burada birikecek.", "Your finished drawings will collect here.")
                    else L("${Works.items.size} kayıt", "${Works.items.size} saved"),
                    color = Ui.InkMuted, fontSize = 15.sp
                )
            }
        }

        if (Works.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Ui.tint(color, 0.28f))
                        .padding(20.dp)
                ) {
                    Text(L("Henüz bir şey yok", "Nothing here yet"), color = deep, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L("Çizerken üstteki kırmızı kayıt düğmesiyle hızlandırılmış video kaydet. Bitirince alttaki kamera düğmesiyle kâğıdının fotoğrafını çek. Hepsi burada önce ve sonra hâliyle görünür.",
                            "While drawing, tap the red record button at the top to record a time-lapse. When you finish, tap the camera button at the bottom to photograph your paper. Everything shows up here as before and after."),
                        color = Ui.Ink, fontSize = 14.sp, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        L("Bir çizim seç", "Pick a drawing"),
                        color = Ui.PaperCard, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(deep)
                            .clickable(onClick = onDraw)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        } else {
            items(Works.items, key = { it.id }) { w ->
                WorkCard(w, lang) { open = w }
            }
        }
    }

    open?.let { w -> WorkDetail(w, onClose = { open = null }) }
}

@Composable
private fun rememberFileBitmap(path: String, maxSide: Int): Bitmap? {
    val key = "$maxSide:$path"
    val bmp by produceState<Bitmap?>(WorkImages.get(key), key) {
        value = WorkImages.get(key) ?: withContext(Dispatchers.IO) {
            loadScaledFile(path, maxSide)?.also { WorkImages.put(key, it) }
        }
    }
    return bmp
}

/** Eser resimleri önbelleği: listede aşağı yukarı kaydırınca fotoğraflar tekrar açılmasın */
private object WorkImages : android.util.LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap) = maxOf(1, value.byteCount / 1024)
}

@Composable
private fun WorkCard(w: Work, lang: Lang, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Ui.PaperCard)
            .border(1.5.dp, Ui.tint(Ui.Rose, 0.45f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .background(Ui.Graphite)
        ) {
            if (w.isVideo) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ui.DrawPaper)
                        .graphPaper(line = Ui.paperTint(Ui.Rose, 0.22f))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) { Thumb(w.drawingPath, Modifier.fillMaxSize()) }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Ui.Glass),
                    contentAlignment = Alignment.Center
                ) { Text("▶", color = Color.White, fontSize = 22.sp) }
            } else {
                rememberFileBitmap(w.file, 480)?.let {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                // Sol altta küçük "önce" resmi
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ui.DrawPaper)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) { Thumb(w.drawingPath, Modifier.fillMaxSize()) }
            }
            Text(
                if (w.isVideo) L("Video", "Video") else L("Fotoğraf", "Photo"),
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Ui.Glass)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(lang.pick(w.name, w.nameEn), color = Ui.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(w.time, lang), color = Ui.InkMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (w.progress >= 0) {
                    Text(lang.pick("%${w.progress}", "${w.progress}%"),
                        color = Ui.deep(Ui.Mint, 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Tam ekran ayrıntı: fotoğrafta Önce / Sonra, videoda oynat. Paylaş ve sil. */
@Composable
private fun WorkDetail(w: Work, onClose: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    var showAfter by remember(w.id) { mutableStateOf(true) }
    var confirmDelete by remember(w.id) { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Ui.Graphite)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lang.pick(w.name, w.nameEn), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDate(w.time, lang), color = Ui.Muted, fontSize = 13.sp)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 18.sp) }
            }
            Spacer(Modifier.height(14.dp))

            if (!w.isVideo) {
                // Önce / Sonra seçici
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(4.dp)
                ) {
                    listOf(false to L("Önce", "Before"), true to L("Sonra", "After")).forEach { (after, label) ->
                        val sel = showAfter == after
                        Text(
                            label,
                            color = if (sel) Ui.OnAccent else Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) Ui.Rose else Color.Transparent)
                                .clickable { showAfter = after }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (w.isVideo || !showAfter) Ui.DrawPaper else Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!w.isVideo && showAfter) {
                    rememberFileBitmap(w.file, 1600)?.let {
                        Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphPaper(line = Ui.paperTint(Ui.Rose, 0.22f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { Thumb(w.drawingPath, Modifier.fillMaxSize()) }
                    if (w.isVideo) {
                        Box(
                            Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Ui.Rose)
                                .clickable { openVideo(context, Uri.parse(w.file), lang) },
                            contentAlignment = Alignment.Center
                        ) { Text("▶", color = Ui.OnAccent, fontSize = 30.sp) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailButton(L("Paylaş", "Share"), Ui.Accent, Modifier.weight(1f)) {
                    if (w.isVideo) {
                        shareUri(context, Uri.parse(w.file), "video/mp4", lang.pick("Videoyu paylaş", "Share video"))
                    } else {
                        shareUri(context, fileUri(context, File(w.file)), "image/jpeg", lang.pick("Fotoğrafı paylaş", "Share photo"))
                    }
                }
                DetailButton(
                    if (w.isVideo) L("Listeden kaldır", "Remove") else L("Sil", "Delete"),
                    Color.White.copy(alpha = 0.12f), Modifier.weight(1f), light = true
                ) { confirmDelete = true }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (w.isVideo) L("Listeden kaldırılsın mı?", "Remove from list?") else L("Fotoğraf silinsin mi?", "Delete photo?")) },
            text = {
                Text(
                    if (w.isVideo) L("Video telefonunun galerisinde kalır.", "The video stays in your phone's gallery.")
                    else L("Bu fotoğraf geri getirilemez.", "This photo can't be recovered.")
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; Works.delete(context, w); onClose() }) {
                    Text(if (w.isVideo) L("Kaldır", "Remove") else L("Sil", "Delete"), color = Ui.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L("Vazgeç", "Cancel")) } }
        )
    }
}

@Composable
private fun DetailButton(label: String, color: Color, modifier: Modifier, light: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (light) Color.White else Ui.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
