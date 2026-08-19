package com.epai.oblender

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Paleta "Nomad" (estilo de la card del prototipo). */
val NomadBackground = Color(0xFF121215)
val NomadCardBg = Color(0xFF1B1B20)
val NomadCardBorder = Color(0xFF2C2C36)
val NomadTrackBg = Color(0xFF262630)
val NomadTextMain = Color(0xFFF5F5F7)
val NomadTextMuted = Color(0xFF9E9EA8)
val NomadAccentStart = Color(0xFFFF8C00)
val NomadAccentEnd = Color(0xFFFF4500)
val NomadAmber = Color(0xFFFFC107)

/** Dimensiones de la card Nomad (dp). Se usa para dimensionar la ventana del overlay:
 *  el contenido scrollea hasta NOMAD_CONTENT_MAX_H y la ventana crece NOMAD_CARD_H. */
const val NOMAD_CARD_W_DP = 310f
const val NOMAD_CONTENT_MAX_H = 400f
const val NOMAD_CARD_H_DP = NOMAD_CONTENT_MAX_H + 44f // scroll max + paddings/header

/** Campos reales del brush usados por Draw / Draw Sharp (mismo IDs que el mini menu). */
private const val FIELD_AUTOSMOOTH = 1
private const val FIELD_HARDNESS = 10
private const val FIELD_NORMAL_RADIUS = 11
private const val FIELD_DIRECTION = 108
private const val FIELD_RADIUS_UNIT = 109
private const val FIELD_ACCUMULATE = 205
private const val FIELD_FRONT_FACES = 206

private const val RADIUS_MIN = 2
private const val RADIUS_MAX = 150

/** Formatea un float con 2 decimales (estilo del prototipo). */
private fun formatFloat(v: Float): String = String.format("%.2f", v)

/** Slider estilo Nomad: label + valor a la derecha (icono presión opcional) y barra
 *  con gradiente ámbar a la izquierda del thumb. Aplicación EN VIVO vía onValueChange. */
@Composable
private fun NomadSculptSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    min: Float = 0f,
    max: Float = 1f,
    valueText: String,
    showPressure: Boolean = false,
    tonePercent: Boolean = false
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showPressure) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(NomadAccentStart, NomadAccentEnd)))
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(label, color = NomadTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(valueText, color = NomadTextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (tonePercent) Text("%", color = NomadTextMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(min, max) {
                    detectTapGestures(onTap = { pos ->
                        val f = (pos.x / size.width).coerceIn(0f, 1f)
                        onValueChange(min + f * (max - min))
                    })
                }
                .pointerInput(min, max) {
                    var firstX = 0f
                    var firstFrac = frac
                    androidx.compose.foundation.gestures.detectDragGestures(
                        onDragStart = { start ->
                            firstX = start.x
                            firstFrac = (start.x / size.width).coerceIn(0f, 1f)
                            onValueChange(min + firstFrac * (max - min))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val f = ((firstFrac + (change.position.x - firstX) / size.width))
                                .coerceIn(0f, 1f)
                            onValueChange(min + f * (max - min))
                        }
                    )
                }
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                val cy = h / 2f
                val trackTop = 7.dp.toPx()
                val trackW = size.width
                drawRoundRect(
                    color = NomadTrackBg,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, cy - trackTop / 2f),
                    size = androidx.compose.ui.geometry.Size(trackW, trackTop),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackTop / 2f)
                )
                if (frac > 0.001f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(NomadAccentStart, NomadAccentEnd)),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, cy - trackTop / 2f),
                        size = androidx.compose.ui.geometry.Size(trackW * frac, trackTop),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackTop / 2f)
                    )
                }
                drawCircle(
                    color = NomadCardBg,
                    radius = 5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(trackW * frac, cy)
                )
                drawCircle(
                    brush = Brush.linearGradient(listOf(NomadAccentStart, NomadAccentEnd)),
                    radius = 4.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(trackW * frac, cy)
                )
            }
        }
    }
}

/** Control segmentado estilo Nomad: píldora ánbar animada para la opción activa. */
@Composable
private fun NomadSegmentedBtn(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(NomadTrackBg)
            .pointerInput(options.size) {
                detectTapGestures(onTap = { pos ->
                    val idx = ((pos.x / size.width) * options.size).toInt()
                        .coerceIn(0, options.size - 1)
                    onSelect(idx)
                })
            }
    ) {
        val segW = maxWidth / options.size.toFloat()
        val pillX by animateDpAsState(
            targetValue = segW * selectedIndex.toFloat(),
            animationSpec = tween(180),
            label = "nomadPill"
        )
        Box(
            Modifier
                .offset(x = pillX)
                .width(segW)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.horizontalGradient(listOf(NomadAccentStart, NomadAccentEnd)))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, opt ->
                Box(
                    Modifier
                        .width(segW)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        opt,
                        color = if (i == selectedIndex) Color(0xFF1A1A1A) else NomadTextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** Fila con título/subtítulo + Material3 Switch escalado (track ámbar, thumb blanco). */
@Composable
private fun NomadSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NomadTextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = NomadTextMuted, fontSize = 10.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.75f),
            colors = SwitchDefaults.colors(
                checkedTrackColor = NomadAmber,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = NomadTrackBg,
                uncheckedThumbColor = NomadTextMuted
            )
        )
    }
}

/** Card Material3 "Nomad" para Draw / Draw Sharp. Reemplaza al mini menu canvas para
 *  estos 2 tools. Lee los valores del brush al abrir y aplica cambios EN VIVO vía las
 *  statics de OBLNativeActivity (radio px, strength, y los field ids de extras). */
@Composable
fun NomadBrushPanel(
    modifier: Modifier = Modifier,
    label: String,
    onClose: () -> Unit,
    initialRadius: Int,
    initialStrength: Float,
    initialExtras: Map<Int, Float>
) {
    var radius by remember { mutableStateOf(initialRadius.coerceIn(RADIUS_MIN, RADIUS_MAX)) }
    var strength by remember { mutableStateOf(initialStrength.coerceIn(0f, 1f)) }
    var radiusUnit by remember { mutableStateOf((initialExtras[FIELD_RADIUS_UNIT] ?: 0f).toInt()) }
    var direction by remember { mutableStateOf((initialExtras[FIELD_DIRECTION] ?: 0f).toInt()) }
    var nrmRadius by remember { mutableStateOf(initialExtras[FIELD_NORMAL_RADIUS] ?: 0.5f) }
    var hardness by remember { mutableStateOf(initialExtras[FIELD_HARDNESS] ?: 0f) }
    var autoSmooth by remember { mutableStateOf(initialExtras[FIELD_AUTOSMOOTH] ?: 0f) }
    var accumulate by remember { mutableStateOf((initialExtras[FIELD_ACCUMULATE] ?: 0f) >= 0.5f) }
    var frontFaces by remember { mutableStateOf((initialExtras[FIELD_FRONT_FACES] ?: 0f) >= 0.5f) }

    // Re-lee los valores reales del brush tras activar el tool (el drain tarda unas
    // frames en aplicarlo), igual que el mini menu canvas.
    LaunchedEffect(label) {
        delay(300)
        radius = OBLNativeActivity.getActiveBrushRadiusStatic().coerceIn(RADIUS_MIN, RADIUS_MAX)
        strength = OBLNativeActivity.getActiveBrushStrengthStatic().coerceIn(0f, 1f)
        radiusUnit = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_RADIUS_UNIT).toInt()
        direction = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_DIRECTION).toInt()
        nrmRadius = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_NORMAL_RADIUS)
        hardness = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_HARDNESS)
        autoSmooth = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_AUTOSMOOTH)
        accumulate = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_ACCUMULATE) >= 0.5f
        frontFaces = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_FRONT_FACES) >= 0.5f
    }

    MaterialTheme {
        Column(
            modifier
                .width(NOMAD_CARD_W_DP.dp)
                .heightIn(max = NOMAD_CONTENT_MAX_H.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NomadCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, NomadCardBorder)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Header: dot ámbar + nombre del brush + X.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(NomadAccentStart, NomadAccentEnd)))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            color = NomadTextMain,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(NomadTrackBg)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { onClose() })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = NomadTextMuted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    NomadSculptSlider(
                        label = "Radius",
                        value = radius.toFloat(),
                        onValueChange = { r ->
                            radius = r.roundToInt().coerceIn(RADIUS_MIN, RADIUS_MAX)
                            OBLNativeActivity.setActiveBrushRadiusStatic(radius)
                        },
                        min = RADIUS_MIN.toFloat(),
                        max = RADIUS_MAX.toFloat(),
                        valueText = "${radius}px",
                        showPressure = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Radius Unit", color = NomadTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    NomadSegmentedBtn(
                        options = listOf("View", "Scene"),
                        selectedIndex = radiusUnit,
                        onSelect = {
                            radiusUnit = it
                            OBLNativeActivity.setActiveBrushExtraStatic(FIELD_RADIUS_UNIT, it.toFloat())
                        }
                    )
                    Spacer(Modifier.height(12.dp))

                    NomadSculptSlider(
                        label = "Strength",
                        value = strength,
                        onValueChange = { s ->
                            strength = s.coerceIn(0f, 1f)
                            OBLNativeActivity.setActiveBrushStrengthStatic(strength)
                        },
                        valueText = formatFloat(strength),
                        showPressure = true
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Direction", color = NomadTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    NomadSegmentedBtn(
                        options = listOf("Add", "Subtract"),
                        selectedIndex = direction,
                        onSelect = {
                            direction = it
                            OBLNativeActivity.setActiveBrushExtraStatic(FIELD_DIRECTION, it.toFloat())
                        }
                    )
                    Spacer(Modifier.height(12.dp))

                    NomadSculptSlider(
                        label = "Normal Radius",
                        value = nrmRadius,
                        onValueChange = { v ->
                            nrmRadius = v
                            OBLNativeActivity.setActiveBrushExtraStatic(FIELD_NORMAL_RADIUS, v)
                        },
                        max = 2f,
                        valueText = formatFloat(nrmRadius),
                        tonePercent = true
                    )
                    Spacer(Modifier.height(12.dp))

                    NomadSculptSlider(
                        label = "Hardness",
                        value = hardness,
                        onValueChange = { v ->
                            hardness = v
                            OBLNativeActivity.setActiveBrushExtraStatic(FIELD_HARDNESS, v)
                        },
                        valueText = formatFloat(hardness),
                        tonePercent = true
                    )
                    Spacer(Modifier.height(12.dp))

                    NomadSculptSlider(
                        label = "Auto Smooth",
                        value = autoSmooth,
                        onValueChange = { v ->
                            autoSmooth = v
                            OBLNativeActivity.setActiveBrushExtraStatic(FIELD_AUTOSMOOTH, v)
                        },
                        valueText = formatFloat(autoSmooth),
                        tonePercent = true
                    )
                    Spacer(Modifier.height(12.dp))

                    NomadSwitch(
                        title = "Front Faces Only",
                        subtitle = "sculpt faces facing view",
                        checked = frontFaces,
                        onCheckedChange = {
                            frontFaces = it
                            OBLNativeActivity.setActiveBrushExtraStatic(
                                FIELD_FRONT_FACES, if (it) 1f else 0f
                            )
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    NomadSwitch(
                        title = "Accumulate",
                        subtitle = "accumulate overlapping strokes",
                        checked = accumulate,
                        onCheckedChange = {
                            accumulate = it
                            OBLNativeActivity.setActiveBrushExtraStatic(
                                FIELD_ACCUMULATE, if (it) 1f else 0f
                            )
                        }
                    )
                }
            }
        }
    }
}