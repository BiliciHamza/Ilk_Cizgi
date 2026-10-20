package com.alterationstudio.ilkcizgi

import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * BÖLÜM EKRANLARI
 * Ana sayfadan açılanlar: Çizmeyi öğren (dersler), Kategoriler, Arama (sol üstte geri tuşu).
 * Kısayollar sekmesinin listeleri: Son açtıkların, Favorilerim, Fotoğraflarım.
 */

// Ana sayfadan açılan ekranlar
const val SECTION_LESSONS = "dersler"
const val SECTION_SEARCH = "ara"
const val SECTION_CATEGORIES = "kategoriler"
// Kısayollar sekmesindeki listeler
const val SECTION_RECENT = "son"
const val SECTION_FAVORITES = "favoriler"
const val SECTION_PHOTOS = "fotograflar"

/** Sade üst başlık: sol üstte geri tuşu, yanında başlık ve kısa açıklama (Nasıl kullanılır da kullanır) */
@Composable
fun SectionHeader(title: String, subtitle: String, color: Color, onBack: () -> Unit) {
    val deep = Ui.deep(color, 0.42f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Ui.tint(color, 0.35f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { ToolIcon(Glyph.Back, deep, size = 22.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ui.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Ui.InkMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** Boş liste için kısa açıklama */
@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        color = Ui.InkMuted,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp)
    )
}

/** Çizmeyi öğren: bütün dersler */
@Composable
fun LessonsScreen(lessons: List<Lesson>, onBack: () -> Unit, onOpenLesson: (Lesson) -> Unit) {
    val finished = lessons.count { (Library.lessonDone[it.id] ?: 0) >= it.steps.size }
    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Paper)
    ) {
        SectionHeader(
            L("Çizmeyi öğren", "Learn to draw"),
            L("Adım adım: her adımda öncekinin üstüne yeni çizgiler eklenir · $finished/${lessons.size} bitti",
                "Step by step: each step adds new lines on top of the last · $finished/${lessons.size} done"),
            Ui.Violet, onBack
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
        ) {
            items(lessons, key = { it.id }) { l ->
                LessonCard(l, Modifier.fillMaxWidth()) { onOpenLesson(l) }
            }
        }
    }
}

/**
 * Kısayollar sekmesi: Son açtıkların, Favorilerim, Fotoğraflarım.
 * Üstteki üç karttan biri seçilir, altında o listenin çizimleri görünür.
 * Son açtıkların'dan açılan çizim en son bırakıldığı ayarlarla gelir (onOpenRecent).
 */
@Composable
fun ShortcutsScreen(
    lookup: (String) -> Pair<CatalogItem, Color>?,
    onOpenDrawing: (String) -> Unit,
    onOpenRecent: (String) -> Unit,
    onPreview: (path: String, fromRecent: Boolean) -> Unit
) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf(SECTION_RECENT) }
    var toDelete by remember { mutableStateOf<CatalogItem?>(null) }

    val recents = Library.recents.mapNotNull(lookup)
    val favs = Library.favorites.mapNotNull(lookup)
    val photos = UserSketches.items.map { it to Ui.Rose }
    val list = when (section) {
        SECTION_FAVORITES -> favs
        SECTION_PHOTOS -> photos
        else -> recents
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Ui.Paper)
            .statusBarsPadding()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(L("Kısayollar", "Shortcuts"), color = Ui.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutChoice(Glyph.Clock, L("Son açtıkların", "Recent"), recents.size, Ui.Accent,
                        section == SECTION_RECENT, Modifier.weight(1f)) { section = SECTION_RECENT }
                    ShortcutChoice(Glyph.HeartFilled, L("Favorilerim", "Favourites"), favs.size, Ui.Rose,
                        section == SECTION_FAVORITES, Modifier.weight(1f)) { section = SECTION_FAVORITES }
                    ShortcutChoice(Glyph.Picture, L("Fotoğraflarım", "My photos"), photos.size, Ui.Sun,
                        section == SECTION_PHOTOS, Modifier.weight(1f)) { section = SECTION_PHOTOS }
                }
                if (section == SECTION_PHOTOS && photos.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(L("Silmek için çizime uzun bas", "Long-press a drawing to delete it"),
                        color = Ui.InkMuted, fontSize = 12.sp)
                }
            }
        }
        if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyNote(
                    when (section) {
                        SECTION_FAVORITES -> L("Henüz favorin yok. Beğendiğin çizimin köşesindeki ♡'e dokun.",
                            "No favourites yet. Tap the ♡ on a drawing you like.")
                        SECTION_PHOTOS -> L("Henüz fotoğraftan çizim yapmadın. Ana sayfadaki \"Kendi fotoğraflarını çizime dönüştür\" ile başla.",
                            "You haven't made a drawing from a photo yet. Start with \"Turn your photos into drawings\" on the home screen.")
                        else -> L("Henüz bir çizim açmadın. Açtığın çizimler burada görünür.",
                            "You haven't opened a drawing yet. Drawings you open show up here.")
                    }
                )
            }
        } else {
            items(list, key = { "$section:${it.first.path}" }) { (item, c) ->
                DrawingTile(
                    item = item,
                    color = c,
                    onLongClick = if (section == SECTION_PHOTOS) ({ toDelete = item }) else null,
                    onPreview = { onPreview(item.path, section == SECTION_RECENT) },
                    onClick = { if (section == SECTION_RECENT) onOpenRecent(item.path) else onOpenDrawing(item.path) }
                )
            }
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(L("Silinsin mi?", "Delete it?")) },
            text = {
                Text(L("\"${item.name}\" çizimi silinecek. Bu geri alınamaz.",
                    "\"${item.nameEn}\" will be deleted. This can't be undone."))
            },
            confirmButton = {
                TextButton(onClick = { UserSketches.delete(context, item); toDelete = null }) {
                    Text(L("Sil", "Delete"), color = Ui.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(L("Vazgeç", "Cancel")) } }
        )
    }
}

/** Kısayollar sekmesindeki seçim kartı: simge, ad ve kaç çizim olduğu */
@Composable
private fun ShortcutChoice(
    icon: Glyph, title: String, count: Int, color: Color,
    selected: Boolean, modifier: Modifier, onClick: () -> Unit
) {
    val deep = Ui.deep(color, 0.42f)
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Ui.tint(color, 0.55f) else Ui.tint(color, 0.22f))
            .border(2.dp, if (selected) deep else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Ui.PaperCard),
                contentAlignment = Alignment.Center
            ) { ToolIcon(icon, deep, size = 18.dp) }
            Spacer(Modifier.weight(1f))
            Text("$count", color = deep, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = deep, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
