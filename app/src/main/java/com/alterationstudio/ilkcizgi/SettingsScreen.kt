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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(lang: Lang, onLangChange: (Lang) -> Unit, onShowIntro: () -> Unit) {
    val context = LocalContext.current
    // Çizim ekranı ayarları: her değişiklik hemen kaydedilir
    var prefs by remember { mutableStateOf(Settings.drawPrefs(context)) }
    fun update(v: DrawPrefs) {
        prefs = v
        Settings.setDrawPrefs(context, v)
    }
    // Yanıp sönme süresi kutusu (boş bırakılabilir; geçerli son değer saklanır)
    var secondsText by remember { mutableStateOf(prefs.blinkSeconds.toString()) }
    var secondsWarning by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(secondsWarning) {
        if (secondsWarning != null) {
            delay(2500)
            secondsWarning = null
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Ui.Paper)
            .statusBarsPadding()
    ) {
        item {
            Text(L("Ayarlar", "Settings"), color = Ui.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        // Dil
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Ui.tint(Ui.Violet, 0.28f))
                    .padding(18.dp)
            ) {
                Text(L("Uygulama dili", "App language"), color = Ui.deep(Ui.Violet, 0.4f),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LangOption("Türkçe", lang == Lang.TR) { onLangChange(Lang.TR) }
                Spacer(Modifier.height(8.dp))
                LangOption("English", lang == Lang.EN) { onLangChange(Lang.EN) }
            }
        }

        // Tema: açık / koyu. Hiç seçilmediyse uygulama telefonun temasına uyar ve o seçili görünür.
        item {
            SettingsCard(Ui.Violet, L("Tema", "Theme"), "") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to L("☀ Açık", "☀ Light"), 2 to L("☾ Koyu", "☾ Dark"))
                        .forEach { (mode, label) ->
                            val selected = if (mode == 2) Ui.isDark else !Ui.isDark
                            ChoiceChip(label, selected, Ui.Violet, Modifier.weight(1f)) {
                                Ui.themeMode = mode
                                Settings.setTheme(context, mode)
                            }
                        }
                }
            }
        }

        // Çizim ekranı varsayılanları
        item {
            SettingsCard(Ui.Mint, L("Varsayılan ayarlar", "Default settings"), "") {
                Text(
                    L("Saydamlık %${(prefs.opacity * 100).roundToInt()}", "Opacity ${(prefs.opacity * 100).roundToInt()}%"),
                    color = Ui.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                Slider(
                    value = prefs.opacity,
                    onValueChange = { prefs = prefs.copy(opacity = it) },
                    onValueChangeFinished = { update(prefs) },
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Ui.deep(Ui.Mint, 0.6f),
                        activeTrackColor = Ui.Mint,
                        inactiveTrackColor = Ui.tint(Ui.Mint, 0.4f)
                    )
                )
                Text(L("Kılavuz rengi", "Guide colour"), color = Ui.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideColors.forEachIndexed { i, c ->
                        val sel = prefs.colorIndex == i
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, if (sel) Ui.deep(Ui.Mint, 0.45f) else Color.Transparent, CircleShape)
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(c ?: Color(0xFF111111))
                                .border(1.dp, Ui.InkMuted.copy(alpha = 0.5f), CircleShape)
                                .clickable { update(prefs.copy(colorIndex = i)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingSwitch(L("El önde", "Hand in front"),
                    L("Elin ve kalemin çizimin üstünde görünür", "Your hand and pencil show above the drawing"),
                    prefs.handFront) { update(prefs.copy(handFront = it)) }
                SettingSwitch(L("Çizdiklerimi gizle", "Hide what I've drawn"),
                    L("Çizdiğin yerlerde kılavuz kaybolur", "The guide disappears where you've drawn"),
                    prefs.hideDrawn) { update(prefs.copy(hideDrawn = it)) }
                // Algılama hassasiyeti: önerilen ve varsayılan 0
                Spacer(Modifier.height(6.dp))
                Text(
                    L("Algılama hassasiyeti: ${(prefs.sensitivity * 10).roundToInt()}",
                        "Detection sensitivity: ${(prefs.sensitivity * 10).roundToInt()}"),
                    color = Ui.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    L("El önde, Çizdiklerimi gizle ve yüzde için. 0 önerilir: açık kalem izleri de yakalanır, gölgeler el sanılmaz. Artırınca gölgeler de el sayılır, sadece koyu çizgiler fark edilir.",
                        "For Hand in front, Hide what I've drawn and the percentage. 0 is recommended: light pencil marks are caught and shadows aren't mistaken for your hand. Higher values treat shadows as hand and only notice dark lines."),
                    color = Ui.InkMuted, fontSize = 12.sp, lineHeight = 16.sp
                )
                Slider(
                    value = prefs.sensitivity,
                    onValueChange = { prefs = prefs.copy(sensitivity = it) },
                    onValueChangeFinished = { update(prefs) },
                    valueRange = 0f..1f,
                    steps = SENSITIVITY_STEPS,
                    colors = SliderDefaults.colors(
                        thumbColor = Ui.deep(Ui.Mint, 0.6f),
                        activeTrackColor = Ui.Mint,
                        inactiveTrackColor = Ui.tint(Ui.Mint, 0.4f)
                    )
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val atRecommended = prefs.sensitivity == SENSITIVITY_RECOMMENDED
                    Text(
                        L("0 · önerilen", "0 · recommended"),
                        color = Ui.deep(Ui.Mint, 0.45f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (atRecommended) Ui.tint(Ui.Mint, 0.5f) else Color.Transparent)
                            .border(1.dp, Ui.tint(Ui.Mint, 0.8f), RoundedCornerShape(50))
                            .clickable { update(prefs.copy(sensitivity = SENSITIVITY_RECOMMENDED)) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text("10", color = Ui.InkMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                SettingSwitch(L("Izgara", "Grid"),
                    L("Çizimin üstünde oran kareleri", "Proportion squares over the drawing"),
                    prefs.grid) { update(prefs.copy(grid = it)) }
                SettingSwitch(L("Yanıp sönen kılavuz", "Blinking guide"),
                    L("Kılavuz arada bir kaybolur, kendi çizgilerini temiz görürsün",
                        "The guide fades out now and then so you see your own lines clearly"),
                    prefs.blink) { update(prefs.copy(blink = it)) }
                Spacer(Modifier.height(4.dp))
                // Kaç saniyede bir kaybolsun: kutuya yazılır (en fazla 60, aşılırsa kısa bir uyarı çıkar)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(L("Yanıp sönme süresi", "Blink interval"), color = Ui.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(L("Kılavuz kaç saniyede bir kaybolsun", "How many seconds between blinks"),
                            color = Ui.InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(3)
                            val n = digits.toIntOrNull()
                            when {
                                n == null -> secondsText = ""
                                n > MAX_BLINK_SECONDS -> {
                                    secondsText = MAX_BLINK_SECONDS.toString()
                                    update(prefs.copy(blinkSeconds = MAX_BLINK_SECONDS))
                                    secondsWarning = lang.pick("En fazla $MAX_BLINK_SECONDS saniye olabilir", "It can be $MAX_BLINK_SECONDS seconds at most")
                                }
                                n < 1 -> {
                                    secondsText = digits
                                    secondsWarning = lang.pick("En az 1 saniye olmalı", "It must be at least 1 second")
                                }
                                else -> {
                                    secondsText = digits
                                    update(prefs.copy(blinkSeconds = n))
                                }
                            }
                        },
                        singleLine = true,
                        suffix = { Text(L("sn", "s")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Ui.deep(Ui.Mint, 0.6f),
                            cursorColor = Ui.deep(Ui.Mint, 0.6f)
                        ),
                        modifier = Modifier.width(96.dp)
                    )
                }
                // Kendiliğinden kaybolan küçük uyarı (onaylamaya gerek yok)
                AnimatedVisibility(visible = secondsWarning != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        secondsWarning ?: "",
                        color = Ui.Danger, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Varsayılana dön: kutu içinde, tam genişlikte, kolay basılır
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ui.PaperCard)
                        .border(1.5.dp, Ui.deep(Ui.Mint, 0.5f), RoundedCornerShape(14.dp))
                        .clickable {
                            update(DrawPrefs())
                            secondsText = DrawPrefs().blinkSeconds.toString()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        L("↺  Varsayılana dön", "↺  Restore defaults"),
                        color = Ui.deep(Ui.Mint, 0.45f), fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Tanıtım
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ui.PaperCard)
                    .clickable(onClick = onShowIntro)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("👋", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text(L("Tanıtımı tekrar göster", "Show the introduction again"), color = Ui.Ink,
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("›", color = Ui.InkMuted, fontSize = 22.sp)
            }
        }

        // Hakkında
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Ui.PaperCard)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_mark),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("İlk Çizgi", color = Ui.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Alteration Studio", color = Ui.InkMuted, fontSize = 14.sp)
                    Text(L("Sürüm 1.0", "Version 1.0"), color = Ui.InkMuted, fontSize = 13.sp)
                }
            }
        }

    }
}

@Composable
private fun LangOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val deep = Ui.deep(Ui.Violet, 0.42f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.PaperCard)
            .border(2.dp, if (selected) deep else Ui.PaperCard, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Ui.Ink, fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f))
        // Seçim halkası
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) deep else Ui.PaperLine, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (selected) deep else Ui.PaperCard)
        )
    }
}

/** Renkli ayar kartı: başlık, açıklama ve içerik */
@Composable
private fun SettingsCard(color: Color, title: String, body: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Ui.tint(color, 0.28f))
            .padding(18.dp)
    ) {
        Text(title, color = Ui.deep(color, 0.4f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(body, color = Ui.Ink.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Yan yana seçeneklerden biri */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val deep = Ui.deep(color, 0.42f)
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) deep else Ui.PaperCard)
            .border(1.5.dp, if (selected) deep else Ui.PaperLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Ui.PaperCard else Ui.Ink, fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

/** Başlık + kısa açıklama + aç/kapa */
@Composable
private fun SettingSwitch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ui.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = Ui.InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ui.PaperCard,
                checkedTrackColor = Ui.deep(Ui.Mint, 0.6f)
            )
        )
    }
}
