package com.alterationstudio.ilkcizgi

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nasıl kullanılır: adım adım kurulum, alttaki araç çubuğu ve sorun giderme.
 * Sekme değil; ana sayfadan ya da çizim ekranının ? düğmesinden üstte açılır, sol üstteki geri ile kapanır.
 */
@Composable
fun HowToScreen(onBack: () -> Unit) {
  Column(
      Modifier
          .fillMaxSize()
          .background(Ui.Paper)
  ) {
    SectionHeader(
        L("Nasıl kullanılır", "How to use"),
        L("6 adımda ilk çizimini kâğıda geçir ve sakla.", "Get your first drawing onto paper and keep it in 6 steps."),
        Ui.Mint, onBack
    )
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .weight(1f)
            .navigationBarsPadding()
    ) {

        item {
            StepCard(1, Ui.Accent, L("Kâğıdı hazırla", "Prepare the paper"),
                L("Beyaz kâğıdı masaya koy. Kâğıdı bantla sabitlersen kaymaz. Ortam aydınlık olsun, kâğıtta parlama olmasın.",
                    "Put white paper on the table. Tape it down so it can't slide. Use good light and avoid glare on the paper."))
        }
        item {
            StepCard(2, Ui.Mint, L("Çizim seç", "Pick a drawing"),
                L("Ana sayfadaki \"Kategoriler\"den bir kategori aç. Her çizimin altında iki düğme var: \"Çiz\" kamerayı açıp çizime başlatır, \"Önizle\" kamera açmadan çizime bakmanı sağlar; orada parmaklarınla kaydırıp büyütebilir ve döndürebilir, aynasını, ızgarasını, rengini ve arka planını deneyebilir (göz düğmesi ayarları gizler), sonra \"Çizmeye başla\" ile aynı ayarlarla başlayabilirsin. Aradığını hızlı bulmak için ana sayfadaki arama kutusunu kullan; kategoriye göre de süzebilirsin. ♡ ile favorilerine ekleyebilirsin, Kısayollar sekmesinde bulursun. İstersen \"Kendi fotoğraflarını çizime dönüştür\" ile kendi fotoğrafını çizgilere çevirebilir ya da olduğu gibi kullanabilirsin. Yeni başlıyorsan \"Çizmeyi öğren\" derslerinden başla: önce 1. adımı çiz, \"Sonraki adım\"a basınca yeni çizgiler öncekilerin tam üstüne gelir.",
                    "Open a category from \"Categories\" on the home screen. Each drawing has two buttons: \"Draw\" opens the camera and starts drawing, \"Preview\" lets you look at it without the camera; there you can move, zoom and rotate it with your fingers and try its mirror, grid, colour and background (the eye button hides the settings), then tap \"Start drawing\" to begin with the same settings. To find something fast, use the search box on the home screen; you can also filter by category. Tap ♡ to add it to your favourites; you'll find them in the Shortcuts tab. With \"Turn your photos into drawings\" you can turn your own photo into outlines or use it as it is. New to drawing? Start with the \"Learn to draw\" lessons: draw step 1, then tap \"Next step\" and the new lines land right on top of the old ones."))
        }
        item {
            StepCard(3, Ui.Violet, L("Telefonu sabitle", "Prop up the phone"),
                L("Telefonu kâğıdın 30–40 cm üstünde bir bardağa, kitap yığınına ya da telefon standına yasla. Çizeceğin alan ekranda görünsün; çizerken telefon kıpırdamasın.",
                    "Lean the phone on a glass, a stack of books or a phone stand, 30–40 cm above the paper. Keep the area you'll draw on screen, and don't let the phone move while you draw."))
        }
        // 4. adım: alttaki araç çubuğu
        item {
            StepCard(4, Ui.Sun, L("Alttaki araçlarla hizala", "Line it up with the tools below"),
                L("Çizim ekranının altında araç çubuğu açık gelir. Bir araca dokununca onun ayarı hemen üstünde açılır: Konum'a dokununca yön tuşları, Boyut'a dokununca boyut çubuğu gelir. Aç/kapa özellikleri açıkken renkli görünür. Araçlar sıra sıradır: önce ilk sıra ve alttaki sıranın ucu görünür. Tutamağı ya da araçları yukarı çekince çubuk tam açılır, bütün araçları görürsün; aşağı çekince bir kademe küçülür. Tutamağa dokunarak çubuğu gizler ya da geri açarsın.",
                    "The toolbar is open at the bottom of the drawing screen. Tap a tool and its control opens just above: Position shows the arrow keys, Size shows the size slider. On/off features turn colourful when on. Tools sit in rows: first you see the top row and the edge of the next. Pull the handle or the tools up to open the bar fully and see every tool; pull down to shrink it a step. Tap the handle to hide or show the bar.")) {
                Spacer(Modifier.height(12.dp))
                // Araç çubuğunun küçük bir örneği
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ui.Graphite)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(Glyph.Move to L("Konum", "Position"), Glyph.Resize to L("Boyut", "Size"), Glyph.Opacity to L("Saydamlık", "Opacity"),
                        Glyph.Color to L("Renk", "Colour"), Glyph.Hand to L("El önde", "Hand")).forEachIndexed { i, (icon, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (i == 4) Ui.Accent else Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) { ToolIcon(icon, color = if (i == 4) Ui.OnAccent else Color.White, size = 18.dp) }
                            Spacer(Modifier.height(3.dp))
                            Text(label, color = Ui.Muted, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PanelRow(Glyph.Move, L("Konum: ↑ ↓ ← → çizimi milim milim kaydırır, ⟲ ⟳ yarım derece döndürür. Basılı tutarsan devam eder.",
                    "Position: ↑ ↓ ← → nudge the drawing a tiny step, ⟲ ⟳ rotate it by half a degree. Hold to keep going."))
                PanelRow(Glyph.Resize, L("Boyut: kaydırıcıyla ya da %1'lik adımlarla büyütüp küçültür.",
                    "Size: grow or shrink with the slider or in 1% steps."))
                PanelRow(Glyph.Mirror, L("Aynala: çizimi yatayda ters çevirir. Dövme şablonları için kullanışlı.",
                    "Mirror: flips the drawing horizontally. Handy for tattoo stencils."))
                PanelRow(Glyph.Opacity, L("Saydamlık: çizimi daha açık ya da koyu gösterir.",
                    "Opacity: makes the drawing lighter or darker."))
                PanelRow(Glyph.Color, L("Kılavuz rengi: çizimi kırmızı, mavi ya da yeşil göster; kalem çizginle karışmaz.",
                    "Guide colour: show the drawing in red, blue or green so it doesn't blend with your pencil."))
                PanelRow(Glyph.Backdrop, L("Arka plan: çizimi beyaz, siyah, kırmızı, mavi ya da yeşil düz zeminde gösterir; resme bakarak çizmek istersen kullanışlı. Siyah zeminde çizgiler beyaz görünür.",
                    "Background: shows the drawing on a plain white, black, red, blue or green background, handy if you'd rather draw by looking at it. On black, the lines turn white."))
                PanelRow(Glyph.HideGuide, L("Yansıtmayı gizle: çizimi tamamen gizler, ekranda sadece kâğıdın kalır; çizdiğini kontrol etmek için aç, devam etmek için kapat.",
                    "Hide guide: hides the drawing completely so you only see your paper; turn it on to check your work, off to carry on."))
                PanelRow(Glyph.Grid, L("Izgara: çizimin üstüne kareler koyar. Oranları kareler üzerinden kontrol edersin.",
                    "Grid: puts squares over the drawing so you can check proportions square by square."))
                PanelRow(Glyph.Blink, L("Yanıp sönsün: kılavuz arada bir kaybolur, kendi çizgilerini temiz görürsün. Kaç saniyede bir kaybolacağını Ayarlar'dan belirlersin.",
                    "Blink guide: the guide fades out now and then so you can see your own lines. Set how many seconds between blinks in Settings."))
                PanelRow(Glyph.Hand, L("El önde: elin ve kalemin çizimin üstünde görünür.",
                    "Hand in front: your hand and pencil show above the drawing."))
                PanelRow(Glyph.HideLines, L("Çizdiklerimi gizle: kalemle çizdiğin yerlerde kılavuz kaybolur, neyin kaldığını kolayca görürsün.",
                    "Hide what I've drawn: the guide disappears where you've already drawn, so you can see what's left."))
                PanelRow(Glyph.Tune, L("Hassasiyet: El önde, Çizdiklerimi gizle ve yüzde için. En sol (\"Açık kalem izlerini de say\") önerilir: açık kalem izleri de sayılır, gölgeler el sanılmaz. Sağa (\"Gölgeleri de el say\") gittikçe gölgeler de el sayılır, sadece koyu çizgiler fark edilir. Başlangıç değerini Ayarlar'dan seçersin.",
                    "Sensitivity: for Hand in front, Hide what I've drawn and the percentage. Far left (\"Count light pencil marks\") is recommended: light pencil marks count and shadows aren't mistaken for your hand. Further right (\"Treat shadows as hand\") treats shadows as hand and only notices dark lines. Pick the starting value in Settings."))
                PanelRow(Glyph.Lock, L("Kilit (ekranın üstünde): konum tuşlarını kapatır, kameranın odağını ve ışığını sabitler, ilerleme yüzdesini saymaya başlar. Yüzde 5 saniyede bir kâğıda yeniden bakılarak hesaplanır.",
                    "Lock (at the top of the screen): disables the position buttons, fixes the camera's focus and exposure, and starts counting your progress. The percentage is re-checked against the paper every 5 seconds."))
                PanelRow(Glyph.EyeOff, L("Sade görünüm (kilidin yanındaki göz): ekrandaki her şeyi gizler, sadece kilit ve bu düğme kalır; çizerken ekran tertemiz olur. Tekrar basınca ya da geri tuşuyla hepsi geri gelir.",
                    "Clean view (the eye next to the lock): hides everything on screen except the lock and this button, so the screen stays clear while you draw. Tap it again or press back to bring it all back."))
                PanelRow(Glyph.Reset, L("Sıfırla: kilitli değilken çizimi ortaya alır, boyutunu ve açısını baştaki hâline döndürür. Kilitliyken \"Yüzdeyi sıfırla\" olur: çizim yerinde kalır, ilerleme yüzdesi baştan sayılır.",
                    "Reset: when unlocked, centres the drawing and restores its size and angle. When locked it becomes \"Reset %\": the drawing stays put and your progress is counted again from zero."))
            }
        }

        item {
            StepCard(5, Ui.Rose, L("Kilitle ve çiz", "Lock and draw"),
                L("Ayarın bitince üstteki kilit düğmesine bas ve ekrana bakarak kalemle çizgilerin üstünden geç. Altta ne kadarını çizdiğini yüzde olarak görürsün. Ekranı sadeleştirmek için kilidin yanındaki göz düğmesine bas. Ortam karanlıksa üstteki şimşek düğmesiyle feneri açabilirsin. Çizimden çıksan bile alttaki ortadaki \"Devam Et\" son çizimini aynı yer, boyut ve ayarlarla açar; Kısayollar'daki \"Son açtıkların\"dan açtığın çizimler de en son bıraktığın gibi gelir. Hizayı kontrol edip yeniden kilitlemen yeter.",
                    "When it lines up, tap the lock at the top and trace the lines with your pencil while watching the screen. At the bottom you'll see how much you've drawn as a percentage. Tap the eye next to the lock for a clean screen. If it's dark, turn on the torch with the lightning button at the top. Even after you leave, \"Continue\" in the middle of the bottom bar opens your last drawing with the same position, size and settings, and drawings you open from \"Recent\" in Shortcuts come back just as you left them. Just check the alignment and lock again."))
        }

        // Ekstra araçlar
        item {
            StepCard(6, Ui.Violet, L("Kaydet ve paylaş", "Save and share"),
                L("Çizimini saklamanın iki yolu var. Hepsi Çizimlerim sekmesinde önce ve sonra hâliyle durur.",
                    "There are two ways to keep your drawing. Everything stays in the My art tab, as before and after.")) {
                Spacer(Modifier.height(12.dp))
                PanelRow(Glyph.Record, L("Hızlandırılmış video: çizerken üstteki kırmızı kayıt düğmesine bas, bitince kare durdur düğmesiyle durdur. Video galeriye de kaydedilir; Çizimlerim'de uygulamanın içinde istediğin hızda (0,25×–2×) izlersin. Kaydedilen videonun hızını Ayarlar'dan seçersin.",
                    "Time-lapse: tap the red record button at the top while you draw, then the square stop button. The video is also saved to your gallery; watch it in My art inside the app at the speed you like (0.25×–2×). Pick the saved video's speed in Settings."))
                PanelRow(Glyph.Camera, L("Fotoğraf: alttaki kamera düğmesiyle kâğıdının fotoğrafını çek. Çizim bitince bu düğme yeşil yanar.",
                    "Photo: tap the camera button at the bottom to photograph your paper. It lights up green when you're nearly done."))
                PanelRow("✏️ %", L("İlerleme: kilitliyken kalem izlerini sayar ve 5 saniyede bir baştan hesaplar. Telefon kayar ya da gölge düşerse yanlış sayılan yerler kendiliğinden düşer. Çok açık çizgiler sayılmayabilir.",
                    "Progress: counts your pencil marks while locked and recalculates every 5 seconds. If the phone slips or a shadow falls, wrongly counted spots drop out on their own. Very light lines may not count."))
            }
        }

        item {
            Text(
                L("Sorun mu var?", "Having trouble?"),
                color = Ui.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
        item {
            TipCard(L("Çizdiğim yerler gizlenmiyor", "What I've drawn doesn't hide"),
                L("Alttaki araç çubuğunda \"Çizdiklerimi gizle\" açık olsun. \"Hassasiyet\" en solda olsun (önerilen); sağa gittikçe açık çizgiler fark edilmez. Kaleme biraz daha bastır ya da daha koyu bir kalem kullan, ortamı aydınlat.",
                    "Make sure \"Hide my lines\" is on in the bottom toolbar. Keep \"Sensitivity\" at the far left (recommended); further right misses light lines. Press a bit harder or use a darker pencil, and add light."))
        }
        item {
            TipCard(L("Elimin üstünde çizim görünüyor", "The drawing shows on top of my hand"),
                L("Alttaki araç çubuğunda \"El önde\" açık olsun ve ortamı aydınlat. Hâlâ görünüyorsa \"Hassasiyet\"i biraz sağa al; gölgeli yerlerde çizim kaybolmaya başlarsa ya da çizdiklerin gizlenmezse yeniden en sola al.",
                    "Turn on \"Hand in front\" in the bottom toolbar and add light. If it still shows, move \"Sensitivity\" a little to the right; if the drawing starts vanishing in shadows or your lines stop hiding, move it back to the far left."))
        }
        item {
            TipCard(L("Çizim kâğıttan kaydı", "The drawing slipped off the paper"),
                L("Telefon ya da kâğıt kıpırdamış olabilir. Kilidi aç, oklarla tekrar hizala, sonra yeniden kilitle. Kâğıdı bantlamak kaymayı önler.",
                    "The phone or paper may have moved. Unlock, line it up again with Position, then lock again. Taping the paper down stops it sliding."))
        }
        item {
            TipCard(L("Fotoğraftan çizim çok karışık çıktı", "My photo sketch looks too busy"),
                L("\"Ayrıntı\"yı azalt ve sade arka planlı bir fotoğraf dene. Çizgiler kopuksa \"Çizgi kalınlığı\"nı artır. Ya da \"Orijinal fotoğraf\"ı seçip fotoğrafın üstünden saydamlığını ayarlayarak çiz.",
                    "Lower \"Detail\" and try a photo with a plain background. If the lines are broken, raise \"Line weight\". Or choose \"Original photo\" and trace over the photo with the opacity turned down."))
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
  }
}

@Composable
private fun StepCard(
    number: Int,
    color: Color,
    title: String,
    body: String,
    extra: @Composable () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Ui.tint(color, 0.28f))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Ui.deep(color, 0.45f)),
                contentAlignment = Alignment.Center
            ) { Text("$number", color = Ui.PaperCard, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Text(title, color = Ui.deep(color, 0.4f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(body, color = Ui.Ink, fontSize = 15.sp, lineHeight = 21.sp)
        extra()
    }
}

@Composable
private fun PanelRow(glyph: Glyph, text: String) = PanelRow(symbol = "", text = text, glyph = glyph)

@Composable
private fun PanelRow(symbol: String, text: String, glyph: Glyph? = null) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ui.PaperCard)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (glyph != null) ToolIcon(glyph, color = Ui.Ink, size = 20.dp)
            else Text(symbol, color = Ui.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Ui.Ink, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun TipCard(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ui.PaperCard)
            .padding(16.dp)
    ) {
        Text(title, color = Ui.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Ui.InkMuted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
