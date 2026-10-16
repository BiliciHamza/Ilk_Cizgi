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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import java.util.Locale

/*
 * ARAMA VE FİLTRE
 * Bütün çizimlerde adla arar (Türkçe harflere duyarsız: "goz" yazınca "Göz" de bulunur)
 * ve kategoriye göre süzer.
 */

private val TR = Locale.forLanguageTag("tr")
private val MARKS = Regex("\\p{Mn}+")

/** Aramada harf farklarını yok say: küçük harf, şapka/nokta işaretleri yok, ı = i */
fun searchKey(s: String): String =
    Normalizer.normalize(s.lowercase(TR), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace('ı', 'i')

/** Aranabilir çizim: çizim, rengi, kategorisi ve önceden hazırlanmış arama metni */
private class SearchEntry(val item: CatalogItem, val color: Color, val category: String, val key: String)

@Composable
fun SearchScreen(
    categories: List<Category>,
    onBack: () -> Unit,
    onOpenDrawing: (String) -> Unit,
    onPreviewDrawing: (String) -> Unit
) {
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }

    // Bütün çizimler (bir kere hazırlanır). Kategori ve alt kategori adları da aranır.
    val userItems = UserSketches.items.toList()
    val entries = remember(categories, userItems) {
        val list = mutableListOf<SearchEntry>()
        categories.forEach { c ->
            val subOf = HashMap<String, SubCategory>()
            c.subs.forEach { s -> s.drawings.forEach { subOf[it.path] = s } }
            c.all.forEach { item ->
                val s = subOf[item.path]
                val text = listOfNotNull(item.name, item.nameEn, c.name, c.nameEn, s?.name, s?.nameEn).joinToString(" ")
                list += SearchEntry(item, c.color, c.folder, searchKey(text))
            }
        }
        userItems.forEach { item ->
            list += SearchEntry(item, Ui.Rose, "_foto", searchKey("${item.name} ${item.nameEn} fotoğraf photo"))
        }
        list
    }

    val results = remember(entries, query, categoryFilter) {
        val words = searchKey(query).split(' ').filter { it.isNotBlank() }
        entries.filter { e ->
            (categoryFilter == null || e.category == categoryFilter) &&
                    words.all { e.key.contains(it) }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focusRequester.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Paper)
    ) {
        // Üst: geri + arama kutusu
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
                    .background(Ui.tint(Ui.Accent, 0.35f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { ToolIcon(Glyph.Back, Ui.deep(Ui.Accent, 0.42f), size = 22.dp) }
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Ui.PaperCard)
                    .border(1.5.dp, Ui.tint(Ui.Accent, 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolIcon(Glyph.Search, Ui.InkMuted, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(L("Çizim ara: göz, kedi, küp…", "Search: eye, cat, cube…"), color = Ui.InkMuted, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(40) },
                        singleLine = true,
                        textStyle = TextStyle(color = Ui.Ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(Ui.AccentDeep),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable { query = "" }
                            .padding(6.dp)
                    ) { ToolIcon(Glyph.Close, Ui.InkMuted, size = 16.dp) }
                }
            }
        }

        // Kategori
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { FilterChip(L("Her kategori", "All categories"), categoryFilter == null, Ui.Violet) { categoryFilter = null } }
            items(categories, key = { it.folder }) { c ->
                FilterChip(L(c.name, c.nameEn), categoryFilter == c.folder, c.color) { categoryFilter = c.folder }
            }
            if (userItems.isNotEmpty()) {
                item { FilterChip(L("Fotoğraflarım", "My photos"), categoryFilter == "_foto", Ui.Rose) { categoryFilter = "_foto" } }
            }
        }

        Text(
            L("${results.size} sonuç", "${results.size} results"),
            color = Ui.InkMuted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
        )

        if (results.isEmpty()) {
            Text(
                L("Bu aramaya uyan çizim yok. Başka bir kelime dene ya da filtreleri kaldır.",
                    "No drawings match. Try another word or clear the filters."),
                color = Ui.InkMuted, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 40.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding()
            ) {
                items(results, key = { it.item.path }) { e ->
                    DrawingTile(item = e.item, color = e.color, onPreview = {
                        focus.clearFocus()
                        onPreviewDrawing(e.item.path)
                    }, onClick = {
                        focus.clearFocus()
                        onOpenDrawing(e.item.path)
                    })
                }
            }
        }
    }
}

/** Filtre düğmesi (seçiliyken dolu renkli) */
@Composable
private fun FilterChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val deep = Ui.deep(color, 0.42f)
    Text(
        label,
        color = if (selected) Ui.PaperCard else deep,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) deep else Ui.PaperCard)
            .border(1.dp, if (selected) deep else Ui.tint(color, 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
