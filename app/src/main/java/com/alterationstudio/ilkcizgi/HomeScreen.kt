package com.alterationstudio.ilkcizgi

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    categories: List<Category>,
    lessons: List<Lesson>,
    onOpenSection: (String) -> Unit,
    onHowTo: () -> Unit,
    onPhoto: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Ui.Paper)
            .statusBarsPadding()
    ) {
        // Başlık
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo_mark),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("İlk Çizgi", color = Ui.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        L("Kâğıda yansıt ve çizime başla", "Project it on paper and start drawing"),
                        color = Ui.InkMuted, fontSize = 14.sp
                    )
                }
                // Nasıl kullanılır (her zaman sağ üstte)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Ui.PaperCard)
                        .border(1.5.dp, Ui.PaperLine, CircleShape)
                        .clickable(onClick = onHowTo),
                    contentAlignment = Alignment.Center
                ) { ToolIcon(Glyph.Help, Ui.Ink, size = 22.dp) }
            }
        }

        // Arama kutusu (dokununca arama ekranı açılır)
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Ui.PaperCard)
                    .border(1.5.dp, Ui.PaperLine, RoundedCornerShape(50))
                    .clickable { onOpenSection(SECTION_SEARCH) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolIcon(Glyph.Search, Ui.InkMuted, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(L("Çizim ara: göz, kedi, küp…", "Search: eye, cat, cube…"), color = Ui.InkMuted, fontSize = 15.sp)
            }
        }

        // ---------- Üç ana düğme: alt alta, eşit boyutta ----------
        item {
            val finished = lessons.count { (Library.lessonDone[it.id] ?: 0) >= it.steps.size }
            HomeButton(
                title = L("Çizmeyi öğren", "Learn to draw"),
                sub = L("Adım adım ${lessons.size} ders · $finished bitti", "${lessons.size} step-by-step lessons · $finished done"),
                colors = listOf(Ui.Violet, Ui.Rose),
                onClick = { onOpenSection(SECTION_LESSONS) }
            ) {
                // Dersin son adımının resmi
                lessons.firstOrNull()?.let { l -> Thumb(l.steps.last().item.path, Modifier.fillMaxSize().padding(8.dp)) }
            }
        }
        item {
            HomeButton(
                title = L("Kendi fotoğraflarını çizime dönüştür", "Turn your photos into drawings"),
                sub = null,
                colors = listOf(Ui.Rose, Ui.Sun),
                onClick = onPhoto
            ) {
                ToolIcon(Glyph.Camera, Ui.deep(Ui.Rose, 0.45f), size = 34.dp)
            }
        }
        item {
            HomeButton(
                title = L("Kategoriler", "Categories"),
                sub = null,
                colors = listOf(Ui.Accent, Ui.Mint),
                onClick = { onOpenSection(SECTION_CATEGORIES) }
            ) {
                // Dört kategoriden birer küçük resim
                val previews = remember(categories) { categories.mapNotNull { it.all.firstOrNull() }.take(4) }
                Column(Modifier.fillMaxSize().padding(5.dp)) {
                    for (row in 0 until 2) {
                        Row(Modifier.weight(1f)) {
                            for (col in 0 until 2) {
                                Box(Modifier.weight(1f).fillMaxSize().padding(2.dp), contentAlignment = Alignment.Center) {
                                    previews.getOrNull(row * 2 + col)?.let { Thumb(it.path, Modifier.fillMaxSize()) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Alt bilgi: geliştirici / marka
        item {
            Text(
                "Alteration Studio",
                color = Ui.InkMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 4.dp)
            )
        }
    }
}

/** Ana sayfa düğmesi: solda beyaz kâğıt üstünde resim/simge, sağda başlık ve (varsa) kısa açıklama. Üçü de aynı yükseklikte. */
@Composable
private fun HomeButton(
    title: String,
    sub: String?,
    colors: List<Color>,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors.map { Ui.tint(it, 0.55f) }))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Ui.DrawPaper)
                .graphPaper(12.dp, Ui.paperTint(colors.first(), 0.22f)),
            contentAlignment = Alignment.Center
        ) { leading() }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ui.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, maxLines = 2)
            if (sub != null) {
                Spacer(Modifier.height(3.dp))
                Text(sub, color = Ui.Ink.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2)
            }
        }
        Text("›", color = Ui.Ink, fontSize = 28.sp)
    }
}

/** Kategoriler: ana sayfadaki "Kategoriler" düğmesinden açılan sade ekran (her kategori bir kart) */
@Composable
fun CategoriesScreen(categories: List<Category>, onBack: () -> Unit, onOpenCategory: (Category) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Paper)
    ) {
        SectionHeader(
            L("Kategoriler", "Categories"),
            L("Bir kategori seç", "Pick a category"),
            Ui.Accent, onBack
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
        ) {
            if (categories.isEmpty()) {
                item {
                    Text(
                        L("Çizimler yüklenemedi. Uygulamayı kapatıp yeniden aç.",
                            "Drawings couldn't be loaded. Close the app and open it again."),
                        color = Ui.InkMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, Ui.PaperLine, RoundedCornerShape(20.dp))
                            .padding(20.dp)
                    )
                }
            } else {
                items(categories, key = { it.folder }) { cat ->
                    CategoryCard(cat) { onOpenCategory(cat) }
                }
            }
        }
    }
}

/** Renkli kategori kartı: solda 4 çizimlik önizleme, sağda ad ve çizim sayısı (altında açıklama yok) */
@Composable
private fun CategoryCard(cat: Category, onClick: () -> Unit) {
    val previews = remember(cat) {
        (cat.subs.mapNotNull { it.drawings.firstOrNull() } + cat.all).distinct().take(4)
    }
    val deep = Ui.deep(cat.color, 0.42f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Ui.tint(cat.color, 0.30f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .size(118.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Ui.DrawPaper)
                .graphPaper(12.dp, Ui.paperTint(cat.color, 0.25f))
                .padding(6.dp)
        ) {
            for (row in 0 until 2) {
                Row(Modifier.weight(1f)) {
                    for (col in 0 until 2) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            previews.getOrNull(row * 2 + col)?.let { Thumb(it.path, Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(L(cat.name, cat.nameEn), color = deep, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                L("${cat.all.size} çizim", "${cat.all.size} drawings"),
                color = Ui.PaperCard,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(deep)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/** Ders kartı: son adımın resmi, adım sayısı, ilerleme noktaları */
@Composable
fun LessonCard(lesson: Lesson, modifier: Modifier = Modifier.width(158.dp), onClick: () -> Unit) {
    val done = Library.lessonDone[lesson.id] ?: 0
    val n = lesson.steps.size
    val finished = done >= n
    val deep = Ui.deep(lesson.color, 0.42f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Ui.tint(lesson.color, 0.30f))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Ui.DrawPaper)
                .graphPaper(12.dp, Ui.paperTint(lesson.color, 0.25f))
        ) {
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                Thumb(lesson.steps.last().item.path, Modifier.fillMaxSize())
            }
            Text(
                L("$n adım", "$n steps"),
                color = Ui.PaperCard, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(deep)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            if (finished) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Ui.Mint),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = Ui.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(L(lesson.name, lesson.nameEn), color = deep, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            for (i in 0 until n) {
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(width = 14.dp, height = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i < done) deep else Ui.Ink.copy(alpha = 0.18f))
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    finished -> L("Tekrar", "Again")
                    done > 0 -> L("Devam", "Resume")
                    else -> L("Başla", "Start")
                },
                color = deep, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}
