package com.alterationstudio.ilkcizgi

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bir kategorinin sayfası: üstte renkli başlık ve alt kategori sekmeleri, altta çizimler */
@Composable
fun CategoryScreen(category: Category, onBack: () -> Unit, onOpen: (CatalogItem) -> Unit) {
    // null = "Tümü"
    var selectedSub by rememberSaveable(category.folder) { mutableStateOf<String?>(null) }
    val shown = remember(category, selectedSub) {
        category.subs.firstOrNull { it.folder == selectedSub }?.drawings ?: category.all
    }
    val gridState = rememberLazyGridState()
    // Sekme değişince başa dön (çizimden geri gelince kaldığın yer korunur)
    var lastSub by rememberSaveable(category.folder) { mutableStateOf(selectedSub) }
    LaunchedEffect(selectedSub) {
        if (selectedSub != lastSub) {
            lastSub = selectedSub
            gridState.scrollToItem(0)
        }
    }

    val color = category.color
    val deep = Ui.deep(color, 0.42f)

    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Paper)
    ) {
        // Renkli başlık alanı
        Column(
            Modifier
                .fillMaxWidth()
                .background(Ui.tint(color, 0.32f))
                .statusBarsPadding()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Ui.PaperCard)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { ToolIcon(Glyph.Back, deep, size = 22.dp) }
                Spacer(Modifier.width(14.dp))
                Text(
                    L(category.name, category.nameEn),
                    color = deep,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    L("${shown.size} çizim", "${shown.size} drawings"),
                    color = deep.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
            }

            // Alt kategori sekmeleri
            if (category.subs.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SubChip(L("Tümü", "All"), selectedSub == null, color) { selectedSub = null }
                    }
                    items(category.subs, key = { it.folder }) { sub ->
                        SubChip(L(sub.name, sub.nameEn), selectedSub == sub.folder, color) {
                            selectedSub = sub.folder
                        }
                    }
                }
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
        ) {
            items(shown, key = { it.path }) { item ->
                DrawingTile(item = item, color = color, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun SubChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val deep = Ui.deep(color, 0.42f)
    Text(
        label,
        color = if (selected) Ui.PaperCard else deep,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) deep else Ui.PaperCard)
            .border(1.dp, if (selected) deep else Ui.tint(color, 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}
