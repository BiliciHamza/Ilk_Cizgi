package com.alterationstudio.ilkcizgi

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/*
 * İLK GİRİŞ TANITIMI
 * Uygulama ilk açıldığında 4 kısa sayfa. Kaydırarak ya da "İleri" ile geçilir, "Atla" ile kapanır.
 * Ayarlar'dan tekrar açılabilir.
 */

private class IntroPage(val icon: String, val color: Color, val title: String, val body: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val pages = listOf(
        IntroPage(
            "", Ui.Accent,
            L("İlk Çizgi'ye hoş geldin", "Welcome to İlk Çizgi"),
            L("Telefonun kâğıda bir kılavuz yansıtır, sen de üstünden çizersin. Çizim bilmene gerek yok.",
                "Your phone projects a guide onto paper and you trace over it. No drawing experience needed.")
        ),
        IntroPage(
            "📱", Ui.Mint,
            L("Telefonu sabitle, kâğıda yansıt", "Prop the phone, project onto paper"),
            L("Telefonu bir bardağa ya da kitaplara yasla. Alttaki araçlarla çizimi kâğıda hizala ve kilitle. Elin çizimin önünde görünür.",
                "Lean the phone on a glass or some books. Line the drawing up with the tools at the bottom and lock it. Your hand shows in front of the drawing.")
        ),
        IntroPage(
            "✏️", Ui.Violet,
            L("Adım adım öğren", "Learn step by step"),
            L("\"Çizmeyi öğren\" derslerinde her adım öncekinin üstüne yeni çizgiler ekler. Geometrik şekillerle başla, sonra ellere ve yüzlere geç.",
                "In \"Learn to draw\" each step adds new lines on top of the last. Start with geometric shapes, then move on to hands and faces.")
        ),
        IntroPage(
            "📷", Ui.Rose,
            L("Kendi fotoğrafın, kendi eserin", "Your photo, your artwork"),
            L("Fotoğrafını çizgilere çevir, çizerken hızlandırılmış video kaydet, bitince kâğıdının fotoğrafını çek. Hepsi Çizimlerim'de durur.",
                "Turn your photo into lines, record a time-lapse while you draw and photograph your paper at the end. It all stays in My art.")
        )
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    val current = pages[pager.currentPage]

    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Atla
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (!last) {
                Text(
                    L("Atla", "Skip"),
                    color = Ui.InkMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDone)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            } else Spacer(Modifier.height(40.dp))
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val p = pages[index]
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Ui.tint(p.color, 0.55f), Ui.tint(p.color, 0.25f)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (p.icon.isEmpty()) {
                        Image(
                            painter = painterResource(R.drawable.logo_mark),
                            contentDescription = null,
                            modifier = Modifier.size(104.dp)
                        )
                    } else {
                        Text(p.icon, fontSize = 72.sp)
                    }
                }
                Spacer(Modifier.height(36.dp))
                Text(
                    p.title, color = Ui.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, lineHeight = 32.sp
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    p.body, color = Ui.InkMuted, fontSize = 16.sp, lineHeight = 23.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Sayfa noktaları + İleri / Başlayalım
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.forEachIndexed { i, p ->
                    Box(
                        Modifier
                            .size(width = if (i == pager.currentPage) 22.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == pager.currentPage) Ui.deep(p.color, 0.6f) else Ui.PaperLine)
                    )
                }
            }
            Text(
                if (last) L("Başlayalım ✓", "Let's start ✓") else L("İleri ›", "Next ›"),
                color = Ui.OnAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(current.color)
                    .clickable {
                        if (last) onDone()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            )
        }
    }
}
