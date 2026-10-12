package com.alterationstudio.ilkcizgi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/*
 * UYGULAMA SİMGELERİ (araç çubuğu, çizim ekranının üst düğmeleri, ana sayfa, geri tuşları, favori kalbi)
 * Hepsi aynı dille çizilir: ince, yuvarlak uçlu çizgiler, aynı kalınlık, aynı boşluk.
 * Böylece yan yana dururken hiçbiri sırıtmaz (emoji gibi her biri ayrı renkte/biçimde değil).
 */

enum class Glyph {
    // Araç çubuğu
    Lock, Unlock, Move, Resize, Opacity, Color, Hand, HideLines, Tune, Grid, Blink, Mirror, Reset,
    // Genel: geri, kayıt, durdur, fener, favori, yardım, kamera, fikir, arama, kapat, saat, resim
    Back, Record, Stop, Torch, Heart, HeartFilled, Help, Camera, Bulb, Search, Close, Clock, Picture,
    // Devam Et (oynat üçgeni)
    Resume
}

@Composable
fun ToolIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)

        when (glyph) {
            Glyph.Lock, Glyph.Unlock -> {
                // Gövde
                drawRoundRect(color, p(0.2f, 0.46f), Size(0.6f * s, 0.42f * s), CornerRadius(0.08f * s), style = stroke)
                // Kilit halkası (açıkken sağ ayağı yukarıda)
                val shackle = Path().apply {
                    moveTo(0.33f * s, 0.46f * s)
                    lineTo(0.33f * s, 0.32f * s)
                    arcTo(Rect(0.33f * s, 0.13f * s, 0.67f * s, 0.47f * s), 180f, 180f, false)
                    if (glyph == Glyph.Lock) lineTo(0.67f * s, 0.46f * s) else lineTo(0.67f * s, 0.36f * s)
                }
                drawPath(shackle, color, style = stroke)
                drawCircle(color, 0.055f * s, p(0.5f, 0.66f))
            }
            Glyph.Move -> {
                // Dört yöne ok
                line(0.5f, 0.12f, 0.5f, 0.88f)
                line(0.12f, 0.5f, 0.88f, 0.5f)
                arrowHead(p(0.5f, 0.12f), -90f, s, color, stroke)
                arrowHead(p(0.5f, 0.88f), 90f, s, color, stroke)
                arrowHead(p(0.12f, 0.5f), 180f, s, color, stroke)
                arrowHead(p(0.88f, 0.5f), 0f, s, color, stroke)
            }
            Glyph.Resize -> {
                // Köşegen iki yönlü ok: büyüt/küçült
                line(0.27f, 0.73f, 0.73f, 0.27f)
                line(0.73f, 0.27f, 0.5f, 0.27f); line(0.73f, 0.27f, 0.73f, 0.5f)
                line(0.27f, 0.73f, 0.5f, 0.73f); line(0.27f, 0.73f, 0.27f, 0.5f)
            }
            Glyph.Opacity -> {
                // Yarısı dolu daire
                drawCircle(color, 0.36f * s, p(0.5f, 0.5f), style = stroke)
                drawArc(color, 90f, 180f, true, p(0.14f, 0.14f), Size(0.72f * s, 0.72f * s))
            }
            Glyph.Color -> {
                // Damla
                val drop = Path().apply {
                    moveTo(0.5f * s, 0.1f * s)
                    cubicTo(0.5f * s, 0.1f * s, 0.24f * s, 0.42f * s, 0.24f * s, 0.62f * s)
                    arcTo(Rect(0.24f * s, 0.36f * s, 0.76f * s, 0.88f * s), 180f, -180f, false)
                    cubicTo(0.76f * s, 0.42f * s, 0.5f * s, 0.1f * s, 0.5f * s, 0.1f * s)
                    close()
                }
                drawPath(drop, color, style = stroke)
                drawArc(color, 110f, 70f, false, p(0.34f, 0.5f), Size(0.32f * s, 0.26f * s), style = Stroke(width = s * 0.06f, cap = StrokeCap.Round))
            }
            Glyph.Hand -> {
                // Açık el: dört parmak, başparmak ve avuç
                line(0.36f, 0.5f, 0.36f, 0.24f)
                line(0.48f, 0.46f, 0.48f, 0.14f)
                line(0.6f, 0.46f, 0.6f, 0.18f)
                line(0.72f, 0.52f, 0.72f, 0.3f)
                val palm = Path().apply {
                    moveTo(0.72f * s, 0.5f * s)
                    lineTo(0.72f * s, 0.66f * s)
                    quadraticTo(0.72f * s, 0.9f * s, 0.5f * s, 0.9f * s)
                    quadraticTo(0.34f * s, 0.9f * s, 0.26f * s, 0.74f * s)
                    lineTo(0.16f * s, 0.54f * s)
                    quadraticTo(0.14f * s, 0.46f * s, 0.22f * s, 0.46f * s)
                    quadraticTo(0.28f * s, 0.46f * s, 0.36f * s, 0.6f * s)
                }
                drawPath(palm, color, style = stroke)
            }
            Glyph.HideLines -> {
                // Kalem + üstünden geçen çizgi: "kalemle çizdiğin yerleri gizle"
                val pencil = Path().apply {
                    moveTo(0.64f * s, 0.16f * s)
                    lineTo(0.84f * s, 0.36f * s)
                    lineTo(0.4f * s, 0.8f * s)
                    lineTo(0.16f * s, 0.84f * s)
                    lineTo(0.2f * s, 0.6f * s)
                    close()
                }
                drawPath(pencil, color, style = stroke)
                line(0.56f, 0.24f, 0.76f, 0.44f)
                line(0.12f, 0.12f, 0.88f, 0.88f)
            }
            Glyph.Tune -> {
                // Ayar kaydırıcıları: hassasiyet
                line(0.12f, 0.26f, 0.88f, 0.26f)
                line(0.12f, 0.5f, 0.88f, 0.5f)
                line(0.12f, 0.74f, 0.88f, 0.74f)
                for ((x, y) in listOf(0.64f to 0.26f, 0.34f to 0.5f, 0.56f to 0.74f)) {
                    drawCircle(color, 0.1f * s, p(x, y))
                }
            }
            Glyph.Grid -> {
                drawRoundRect(color, p(0.14f, 0.14f), Size(0.72f * s, 0.72f * s), CornerRadius(0.1f * s), style = stroke)
                line(0.38f, 0.14f, 0.38f, 0.86f); line(0.62f, 0.14f, 0.62f, 0.86f)
                line(0.14f, 0.38f, 0.86f, 0.38f); line(0.14f, 0.62f, 0.86f, 0.62f)
            }
            Glyph.Blink -> {
                // Dört köşeli parıltı
                val star = Path().apply {
                    moveTo(0.5f * s, 0.1f * s)
                    quadraticTo(0.56f * s, 0.44f * s, 0.9f * s, 0.5f * s)
                    quadraticTo(0.56f * s, 0.56f * s, 0.5f * s, 0.9f * s)
                    quadraticTo(0.44f * s, 0.56f * s, 0.1f * s, 0.5f * s)
                    quadraticTo(0.44f * s, 0.44f * s, 0.5f * s, 0.1f * s)
                    close()
                }
                drawPath(star, color, style = stroke)
            }
            Glyph.Mirror -> {
                // Ortada kesikli ayna çizgisi, iki yanda aynalı üçgenler
                for (i in 0 until 4) line(0.5f, 0.12f + i * 0.22f, 0.5f, 0.17f + i * 0.22f)
                val left = Path().apply {
                    moveTo(0.35f * s, 0.26f * s); lineTo(0.35f * s, 0.76f * s); lineTo(0.08f * s, 0.76f * s); close()
                }
                val right = Path().apply {
                    moveTo(0.65f * s, 0.26f * s); lineTo(0.65f * s, 0.76f * s); lineTo(0.92f * s, 0.76f * s); close()
                }
                drawPath(left, color, style = stroke)
                drawPath(right, color)
            }
            Glyph.Back -> {
                // Sola ok
                line(0.82f, 0.5f, 0.2f, 0.5f)
                line(0.2f, 0.5f, 0.44f, 0.26f)
                line(0.2f, 0.5f, 0.44f, 0.74f)
            }
            Glyph.Record -> {
                // Halka içinde dolu daire
                drawCircle(color, 0.38f * s, p(0.5f, 0.5f), style = stroke)
                drawCircle(color, 0.2f * s, p(0.5f, 0.5f))
            }
            Glyph.Stop -> {
                drawRoundRect(color, p(0.22f, 0.22f), Size(0.56f * s, 0.56f * s), CornerRadius(0.1f * s))
            }
            Glyph.Torch -> {
                // Şimşek
                val bolt = Path().apply {
                    moveTo(0.58f * s, 0.08f * s)
                    lineTo(0.24f * s, 0.56f * s)
                    lineTo(0.48f * s, 0.56f * s)
                    lineTo(0.42f * s, 0.92f * s)
                    lineTo(0.76f * s, 0.42f * s)
                    lineTo(0.52f * s, 0.42f * s)
                    close()
                }
                drawPath(bolt, color, style = stroke)
            }
            Glyph.Heart, Glyph.HeartFilled -> {
                val heart = Path().apply {
                    moveTo(0.5f * s, 0.84f * s)
                    cubicTo(0.2f * s, 0.64f * s, 0.08f * s, 0.46f * s, 0.14f * s, 0.3f * s)
                    cubicTo(0.22f * s, 0.12f * s, 0.44f * s, 0.12f * s, 0.5f * s, 0.3f * s)
                    cubicTo(0.56f * s, 0.12f * s, 0.78f * s, 0.12f * s, 0.86f * s, 0.3f * s)
                    cubicTo(0.92f * s, 0.46f * s, 0.8f * s, 0.64f * s, 0.5f * s, 0.84f * s)
                    close()
                }
                if (glyph == Glyph.HeartFilled) drawPath(heart, color)
                drawPath(heart, color, style = stroke)
            }
            Glyph.Help -> {
                // Daire içinde soru işareti
                drawCircle(color, 0.4f * s, p(0.5f, 0.5f), style = stroke)
                val q = Path().apply {
                    moveTo(0.38f * s, 0.4f * s)
                    cubicTo(0.38f * s, 0.25f * s, 0.62f * s, 0.25f * s, 0.62f * s, 0.39f * s)
                    cubicTo(0.62f * s, 0.5f * s, 0.5f * s, 0.5f * s, 0.5f * s, 0.6f * s)
                }
                drawPath(q, color, style = stroke)
                drawCircle(color, 0.055f * s, p(0.5f, 0.73f))
            }
            Glyph.Camera -> {
                // Gövde, üst çıkıntı ve objektif
                drawRoundRect(color, p(0.1f, 0.3f), Size(0.8f * s, 0.54f * s), CornerRadius(0.1f * s), style = stroke)
                val top = Path().apply {
                    moveTo(0.34f * s, 0.3f * s)
                    lineTo(0.4f * s, 0.18f * s)
                    lineTo(0.6f * s, 0.18f * s)
                    lineTo(0.66f * s, 0.3f * s)
                }
                drawPath(top, color, style = stroke)
                drawCircle(color, 0.15f * s, p(0.5f, 0.57f), style = stroke)
            }
            Glyph.Bulb -> {
                // Ampul: altı açık daire, iki taban çizgisi
                drawArc(color, 135f, 270f, false, p(0.24f, 0.12f), Size(0.52f * s, 0.52f * s), style = stroke)
                line(0.316f, 0.564f, 0.4f, 0.7f)
                line(0.684f, 0.564f, 0.6f, 0.7f)
                line(0.4f, 0.72f, 0.6f, 0.72f)
                line(0.43f, 0.86f, 0.57f, 0.86f)
            }
            Glyph.Search -> {
                drawCircle(color, 0.27f * s, p(0.43f, 0.43f), style = stroke)
                line(0.63f, 0.63f, 0.86f, 0.86f)
            }
            Glyph.Close -> {
                line(0.24f, 0.24f, 0.76f, 0.76f)
                line(0.76f, 0.24f, 0.24f, 0.76f)
            }
            Glyph.Clock -> {
                drawCircle(color, 0.4f * s, p(0.5f, 0.5f), style = stroke)
                line(0.5f, 0.5f, 0.5f, 0.28f)
                line(0.5f, 0.5f, 0.66f, 0.6f)
            }
            Glyph.Picture -> {
                // Çerçeve, dağ ve güneş
                drawRoundRect(color, p(0.08f, 0.16f), Size(0.84f * s, 0.68f * s), CornerRadius(0.1f * s), style = stroke)
                drawCircle(color, 0.07f * s, p(0.66f, 0.36f))
                val hill = Path().apply {
                    moveTo(0.16f * s, 0.76f * s)
                    lineTo(0.4f * s, 0.5f * s)
                    lineTo(0.56f * s, 0.66f * s)
                    lineTo(0.68f * s, 0.56f * s)
                    lineTo(0.84f * s, 0.76f * s)
                }
                drawPath(hill, color, style = stroke)
            }
            Glyph.Resume -> {
                // Yuvarlak köşeli oynat üçgeni
                val tri = Path().apply {
                    moveTo(0.34f * s, 0.2f * s)
                    lineTo(0.8f * s, 0.5f * s)
                    lineTo(0.34f * s, 0.8f * s)
                    close()
                }
                drawPath(tri, color)
                drawPath(tri, color, style = stroke)
            }
            Glyph.Reset -> {
                // Geri dönen ok
                drawArc(color, 20f, 290f, false, p(0.18f, 0.18f), Size(0.64f * s, 0.64f * s), style = stroke)
                val a = Math.toRadians(310.0)
                val tip = Offset((0.5f + 0.32f * cos(a).toFloat()) * s, (0.5f + 0.32f * sin(a).toFloat()) * s)
                arrowHead(tip, 40f, s, color, stroke)
            }
        }
    }
}

/** Ucu "tip" noktasında, "deg" yönüne bakan ok başı (iki kısa çizgi) */
private fun DrawScope.arrowHead(tip: Offset, deg: Float, s: Float, color: Color, stroke: Stroke) {
    val len = 0.16f * s
    for (side in listOf(-1, 1)) {
        val a = Math.toRadians((deg + 180f + side * 40f).toDouble())
        val end = Offset(tip.x + len * cos(a).toFloat(), tip.y + len * sin(a).toFloat())
        drawLine(color, tip, end, stroke.width, StrokeCap.Round)
    }
}
