package io.github.elaaps.respawnalarm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BossColors {
    val Night = Color(0xFF1B1244)
    val Violet = Color(0xFF2B1F6B)
    val Dusk = Color(0xFF3C2A8F)
    val Purple = Color(0xFF6A35E8)
    val Gold = Color(0xFFFFD54A)
    val Ink = Color(0xFF0E0828)
    val Pale = Color(0xFFF3EEFF)
    val Lilac = Color(0xFFDDD5FF)
    val Muted = Color(0xFF8F80D9)
    val Hp = Color(0xFFFF4D6D)
    val HpShade = Color(0xFFB8173B)
    val HpEmpty = Color(0xFF2A2150)
}

val BossFont = FontFamily(Font(R.font.jersey_10))

@Composable
fun BossBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BossColors.Night, BossColors.Violet, BossColors.Dusk)))
            .drawBehind { drawStars() },
        content = content,
    )
}

private fun DrawScope.drawStars() {
    val step = STAR_STEP.toPx()
    val dot = STAR_SIZE.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawRect(BossColors.Pale.copy(alpha = STAR_ALPHA), Offset(x, y), Size(dot, dot))
            x += step
        }
        y += step
    }
}

@Composable
fun HpBar(filled: Int, modifier: Modifier = Modifier, blinkLast: Boolean = false) {
    val blink = rememberInfiniteTransition(label = "hp blink")
    val lastAlpha by blink.animateFloat(
        1f,
        BLINK_ALPHA,
        infiniteRepeatable(tween(BLINK_MS, easing = StepEasing), RepeatMode.Reverse),
        label = "last segment",
    )
    Row(
        modifier
            .fillMaxWidth()
            .pixelBorder(BossColors.Pale, BAR_BORDER)
            .background(BossColors.Ink)
            .padding(BAR_BORDER + 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(HP_SEGMENTS) { index ->
            val isFilled = index < filled
            Box(
                Modifier
                    .weight(1f)
                    .height(SEGMENT_HEIGHT)
                    .alpha(if (blinkLast && index == filled - 1) lastAlpha else 1f)
                    .drawBehind {
                        drawRect(if (isFilled) BossColors.Hp else BossColors.HpEmpty)
                        if (isFilled) {
                            val shade = SEGMENT_SHADE.toPx()
                            drawRect(BossColors.HpShade, Offset(0f, size.height - shade), Size(size.width, shade))
                        }
                    },
            )
        }
    }
}

@Composable
fun HpCaption(current: Int, total: Int) {
    Row(Modifier.fillMaxWidth()) {
        PixelText(stringResource(R.string.hp_label), 17.sp, BossColors.Lilac, Modifier.weight(1f))
        PixelText(stringResource(R.string.hp_value, current, total), 17.sp, BossColors.Lilac)
    }
}

@Composable
fun PixelText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        textAlign = align,
        style = TextStyle(
            fontFamily = BossFont,
            fontSize = size,
            letterSpacing = (size.value * LETTER_SPACING).sp,
        ),
    )
}

@Composable
fun ShadowedPixelText(text: String, size: TextUnit, color: Color, nearShadow: Color, farShadow: Color, modifier: Modifier = Modifier) {
    val style = TextStyle(fontFamily = BossFont, fontSize = size, textAlign = TextAlign.Center)
    val step = (size.value * SHADOW_RATIO).dp
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, style = style, color = farShadow, modifier = Modifier.offset(step * 2, step * 2))
        Text(text, style = style, color = nearShadow, modifier = Modifier.offset(step, step))
        Text(text, style = style, color = color)
    }
}

@Composable
fun PixelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PixelButtonFrame(text, onClick, BossColors.Gold, BossColors.Night, BossColors.Pale, BUTTON_BORDER, 22.sp, BUTTON_PADDING, modifier)
}

@Composable
fun PixelGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PixelButtonFrame(text, onClick, Color.Transparent, BossColors.Pale, BossColors.Muted, GHOST_BORDER, 18.sp, GHOST_PADDING, modifier)
}

@Composable
private fun PixelButtonFrame(
    text: String,
    onClick: () -> Unit,
    fill: Color,
    content: Color,
    border: Color,
    borderWidth: Dp,
    size: TextUnit,
    padding: Dp,
    modifier: Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .pixelBorder(border, borderWidth)
            .padding(borderWidth)
            .background(fill)
            .padding(vertical = padding, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { PixelText(text, size, content, align = TextAlign.Center) }
}

fun Modifier.pixelBorder(color: Color, width: Dp): Modifier = drawBehind {
    val b = width.toPx()
    drawRect(color, Offset(b, 0f), Size(size.width - 2 * b, b))
    drawRect(color, Offset(b, size.height - b), Size(size.width - 2 * b, b))
    drawRect(color, Offset(0f, b), Size(b, size.height - 2 * b))
    drawRect(color, Offset(size.width - b, b), Size(b, size.height - 2 * b))
}

@Composable
fun PixelPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .pixelBorder(BossColors.Muted, BAR_BORDER)
            .padding(BAR_BORDER)
            .background(BossColors.Ink.copy(alpha = PANEL_ALPHA))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

private val STAR_STEP = 22.dp
private val STAR_SIZE = 1.5.dp
private const val STAR_ALPHA = 0.2f
private const val BLINK_ALPHA = 0.35f
private const val BLINK_MS = 500
private val StepEasing = Easing { if (it < HALF) 0f else 1f }
private const val HALF = 0.5f
private const val LETTER_SPACING = 0.06f
private val BAR_BORDER = 2.dp
private val SEGMENT_HEIGHT = 12.dp
private val SEGMENT_SHADE = 3.dp
private val BUTTON_BORDER = 4.dp
private val GHOST_BORDER = 3.dp
private val BUTTON_PADDING = 12.dp
private val GHOST_PADDING = 8.dp
private const val SHADOW_RATIO = 0.055f
private const val PANEL_ALPHA = 0.6f
