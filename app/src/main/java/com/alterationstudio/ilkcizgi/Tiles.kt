package com.alterationstudio.ilkcizgi

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** Kareli defter kâğıdı dokusu */
fun Modifier.graphPaper(step: Dp = 14.dp, line: Color = Ui.PaperLine): Modifier = drawBehind {
    val s = step.toPx()
    var x = s
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
        x += s
    }
    var y = s
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += s
    }
}

/**
 * Önizlemeler aynı anda en fazla 3 tane açılır: hızlı kaydırırken onlarcası birden
 * belleği doldurmasın. Ekrandan çıkan kartın yüklemesi iptal olur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val ThumbDispatcher = Dispatchers.IO.limitedParallelism(3)

/**
 * Önizlemeler mürekkep renginde boyanır (önizleme sadece koyuluk tutar).
 * Çizimler iki temada da siyah: koyu temada da beyaz kâğıt üstünde durur.
 */
private val InkTint = ColorFilter.tint(Color(0xFF1F232A))

/** Önizleme bilgisi: önbellekte varsa anında, yoksa arka planda yüklenir */
@Composable
fun rememberThumb(path: String): ThumbInfo? {
    val context = LocalContext.current
    val info by produceState<ThumbInfo?>(ThumbCache.get(path), path) {
        value = ThumbCache.get(path) ?: withContext(ThumbDispatcher) { ThumbCache.load(context, path) }
    }
    return info
}

/** Önizleme resmi */
@Composable
fun Thumb(path: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    rememberThumb(path)?.let {
        Image(
            bitmap = it.image,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = InkTint,
            modifier = modifier
        )
    }
}

/** Favori kalbi (kartların sağ üst köşesinde) */
@Composable
fun HeartButton(path: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val context = LocalContext.current
    val fav = Library.isFavorite(path)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Ui.PaperCard.copy(alpha = 0.92f))
            .clickable { Library.toggleFavorite(context, path) },
        contentAlignment = Alignment.Center
    ) {
        ToolIcon(
            if (fav) Glyph.HeartFilled else Glyph.Heart,
            color = if (fav) Ui.deep(Ui.Rose, 0.85f) else Ui.InkMuted,
            size = size * 0.55f
        )
    }
}

/** Kareli kâğıt üstünde çizim kartı. Kategori renginde ince bir çerçeve ve favori kalbi taşır. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawingTile(item: CatalogItem, color: Color, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val name = L(item.name, item.nameEn)
    val info = rememberThumb(item.path)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Ui.PaperCard)
            .border(1.5.dp, Ui.tint(color, 0.45f), RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Ui.paperTint(color, 0.08f))
                .graphPaper(line = Ui.paperTint(color, 0.22f))
        ) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                info?.let {
                    Image(
                        bitmap = it.image,
                        contentDescription = name,
                        contentScale = ContentScale.Fit,
                        colorFilter = InkTint,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            HeartButton(item.path, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                name,
                color = Ui.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
