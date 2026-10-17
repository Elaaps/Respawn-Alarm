package io.github.elaaps.respawnalarm

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
enum class IconStyle(val polygon: RoundedPolygon) {
    STOP(MaterialShapes.Cookie4Sided),
    RINGING(MaterialShapes.Cookie6Sided),
    OTHER(MaterialShapes.Sunny),
}

data class RowIcon(@param:DrawableRes val icon: Int, val style: IconStyle)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapedIcon(icon: RowIcon, size: Dp = ROW_ICON_SIZE) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (icon.style) {
        IconStyle.STOP -> colors.primaryContainer to colors.onPrimaryContainer
        IconStyle.RINGING -> colors.tertiaryContainer to colors.onTertiaryContainer
        IconStyle.OTHER -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Box(Modifier.size(size).background(container, icon.style.polygon.toShape()), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon.icon), contentDescription = null, tint = content, modifier = Modifier.size(size * ICON_TO_SHAPE))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingShape(polygon: RoundedPolygon, color: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var from by remember { mutableStateOf(polygon) }
    var to by remember { mutableStateOf(polygon) }
    val progress = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(polygon) {
        if (polygon == to) return@LaunchedEffect
        from = to
        to = polygon
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }
    val morph = remember(from, to) { Morph(from, to) }
    val path = remember { Path() }
    Box(
        modifier.drawBehind {
            path.rewind()
            morph.toPath(progress.value, path)
            path.transform(Matrix().apply { scale(size.width, size.height) })
            drawPath(path, color)
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private val ROW_ICON_SIZE = 40.dp
private const val ICON_TO_SHAPE = 0.55f
