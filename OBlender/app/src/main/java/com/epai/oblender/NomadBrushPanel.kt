package com.epai.oblender

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.round

// Paleta de Colores Nomad / Blender UI
val NomadBackground = Color(0xFF121215)
val NomadCardBg = Color(0xFF1B1B20)
val NomadCardBorder = Color(0xFF2C2C36)
val NomadTrackBg = Color(0xFF262630)
val NomadAccentStart = Color(0xFFFF8C00)
val NomadAccentEnd = Color(0xFFFF4500)
val NomadTextMain = Color(0xFFF5F5F7)
val NomadTextMuted = Color(0xFF9E9EA8)

/** Dimensiones de la card Nomad (dp). Se usa para dimensionar la ventana del overlay:
 *  el contenido scrollea hasta NOMAD_CONTENT_MAX_H y la ventana crece NOMAD_CARD_H. */
const val NOMAD_CARD_W_DP = 300f
const val NOMAD_CONTENT_MAX_H = 360f
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
private fun formatFloat(value: Float): String {
    val rounded = round(value * 100) / 100f
    return if (rounded % 1f == 0f) {
        "${rounded.toInt()}.00"
    } else {
        val str = rounded.toString()
        val parts = str.split(".")
        if (parts.size == 2 && parts[1].length == 1) "${str}0" else str
    }
}

/** Slider estilo Nomad (igual que el prototipo que se portó): barra gruesa con relleno
 *  degradado proporcional, label + icono de presión a la izquierda y valor editable
 *  con unidad a la derecha. Aplicación EN VIVO vía onValueChange. */
@Composable
private fun NomadSculptSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    unit: String = "",
    isInt: Boolean = false,
    hasPressureIcon: Boolean = false,
    isPressureActive: Boolean = false,
    onPressureToggle: () -> Unit = {}
) {
    var isEditing by remember { mutableStateOf(false) }
    var inputText by remember(value, isEditing) {
        mutableStateOf(if (isInt) "${value.toInt()}" else formatFloat(value))
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val progress = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NomadTrackBg)
            .pointerInput(range) {
                detectTapGestures { offset ->
                    if (!isEditing) {
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        val newValue = range.start + newProgress * (range.endInclusive - range.start)
                        onValueChange(newValue)
                    }
                }
            }
            // detectHorizontalDragGestures no bloquea el scroll vertical de la pantalla
            .pointerInput(range) {
                detectHorizontalDragGestures { change, _ ->
                    if (!isEditing) {
                        change.consume()
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        val newValue = range.start + newProgress * (range.endInclusive - range.start)
                        onValueChange(newValue)
                    }
                }
            }
    ) {
        // Barra de progreso
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0.01f, 1f))
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(NomadAccentStart, NomadAccentEnd)
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasPressureIcon) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isPressureActive) NomadAccentStart.copy(alpha = 0.8f)
                                else Color.White.copy(alpha = 0.15f)
                            )
                            .clickable { if (!isEditing) onPressureToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🖊",
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = label,
                    color = NomadTextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.40f))
                    .clickable { isEditing = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = if (isEditing) inputText
                                else (if (isInt) "${value.toInt()}" else formatFloat(value)),
                        onValueChange = { newText ->
                            inputText = newText
                            newText.toFloatOrNull()?.let { parsed ->
                                onValueChange(parsed.coerceIn(range.start, range.endInclusive))
                            }
                        },
                        modifier = Modifier
                            .widthIn(min = 28.dp, max = 55.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isEditing = true
                                } else {
                                    isEditing = false
                                    inputText.toFloatOrNull()?.let { parsed ->
                                        onValueChange(parsed.coerceIn(range.start, range.endInclusive))
                                    }
                                }
                            },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isInt) KeyboardType.Number else KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                isEditing = false
                                inputText.toFloatOrNull()?.let { parsed ->
                                    onValueChange(parsed.coerceIn(range.start, range.endInclusive))
                                }
                                keyboardController?.hide()
                            }
                        ),
                        cursorBrush = SolidColor(NomadAccentStart)
                    )

                    if (unit.isNotEmpty() && !isEditing) {
                        Text(
                            text = unit,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            runCatching {
                delay(50)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
}

/** Botón segmentado estilo Nomad (igual que el prototipo): píldora individual con
 *  fondo/texto animados. Se usa dentro de una Row con Weight(1f) en una track box. */
@Composable
private fun NomadSegmentedBtn(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) NomadAccentStart else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "nomadSegBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else NomadTextMuted,
        animationSpec = tween(durationMillis = 180),
        label = "nomadSegText"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** Fila con título/subtítulo + Material3 Switch escalado (track ámbar, thumb blanco). */
@Composable
private fun NomadSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                color = NomadTextMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = NomadTextMuted,
                fontSize = 10.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NomadAccentStart,
                uncheckedThumbColor = NomadTextMuted,
                uncheckedTrackColor = NomadTrackBg,
                uncheckedBorderColor = NomadCardBorder
            ),
            modifier = Modifier.scale(0.75f)
        )
    }
}

/** Card Material3 "Nomad" para Draw / Draw Sharp (igual al prototipo portado).
 *  Reemplaza al mini menu canvas para estos 2 tools. Lee los valores del brush al
 *  abrir y aplica cambios EN VIVO vía las statics de OBLNativeActivity. */
@Composable
fun NomadBrushPanel(
    modifier: Modifier = Modifier,
    label: String,
    onClose: () -> Unit,
    initialRadius: Int,
    initialStrength: Float,
    initialExtras: Map<Int, Float>
) {
    val scrollState = rememberScrollState()

    var radius by remember { mutableStateOf(initialRadius.coerceIn(RADIUS_MIN, RADIUS_MAX).toFloat()) }
    var radiusUnit by remember { mutableStateOf((initialExtras[FIELD_RADIUS_UNIT] ?: 0f).toInt()) }
    var strength by remember { mutableStateOf(initialStrength.coerceIn(0f, 1f)) }
    var hardness by remember { mutableStateOf(initialExtras[FIELD_HARDNESS] ?: 0f) }
    var normalRadius by remember { mutableStateOf(initialExtras[FIELD_NORMAL_RADIUS] ?: 0.5f) }
    var autoSmooth by remember { mutableStateOf(initialExtras[FIELD_AUTOSMOOTH] ?: 0f) }
    var direction by remember { mutableStateOf((initialExtras[FIELD_DIRECTION] ?: 0f).toInt()) }
    var isFrontFaces by remember { mutableStateOf((initialExtras[FIELD_FRONT_FACES] ?: 0f) >= 0.5f) }
    var isAccumulate by remember { mutableStateOf((initialExtras[FIELD_ACCUMULATE] ?: 0f) >= 0.5f) }
    // Presión: solo visual por ahora (blender usa BRUSH_USE_PRESSURE_SIZE/STRENGTH).
    var radiusPressure by remember { mutableStateOf(false) }
    var strengthPressure by remember { mutableStateOf(false) }

    // Re-lee los valores reales del brush tras activar el tool (el drain tarda unas
    // frames en aplicarlo), igual que el mini menu canvas.
    LaunchedEffect(label) {
        delay(300)
        radius = OBLNativeActivity.getActiveBrushRadiusStatic().coerceIn(RADIUS_MIN, RADIUS_MAX).toFloat()
        strength = OBLNativeActivity.getActiveBrushStrengthStatic().coerceIn(0f, 1f)
        radiusUnit = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_RADIUS_UNIT).toInt()
        direction = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_DIRECTION).toInt()
        normalRadius = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_NORMAL_RADIUS)
        hardness = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_HARDNESS)
        autoSmooth = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_AUTOSMOOTH)
        isAccumulate = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_ACCUMULATE) >= 0.5f
        isFrontFaces = OBLNativeActivity.getActiveBrushExtraStatic(FIELD_FRONT_FACES) >= 0.5f
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Card(
            modifier = Modifier
                .width(NOMAD_CARD_W_DP.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = NomadCardBg),
            border = BorderStroke(1.dp, NomadCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .heightIn(max = NOMAD_CONTENT_MAX_H.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(NomadAccentStart)
                        )
                        Text(
                            text = label,
                            color = NomadTextMain,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NomadTrackBg)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = NomadTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NomadCardBorder)
                )

                // --- RADIUS & UNIT ---
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NomadSculptSlider(
                        label = "Radius",
                        value = radius,
                        range = RADIUS_MIN.toFloat()..RADIUS_MAX.toFloat(),
                        unit = "px",
                        isInt = true,
                        hasPressureIcon = true,
                        isPressureActive = radiusPressure,
                        onPressureToggle = { radiusPressure = !radiusPressure },
                        onValueChange = { r ->
                            radius = r
                            OBLNativeActivity.setActiveBrushRadiusStatic(r.round().toInt())
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NomadTrackBg)
                            .padding(2.dp)
                    ) {
                        NomadSegmentedBtn(
                            text = "View",
                            isSelected = radiusUnit == 0,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                radiusUnit = 0
                                OBLNativeActivity.setActiveBrushExtraStatic(FIELD_RADIUS_UNIT, 0f)
                            }
                        )
                        NomadSegmentedBtn(
                            text = "Scene",
                            isSelected = radiusUnit == 1,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                radiusUnit = 1
                                OBLNativeActivity.setActiveBrushExtraStatic(FIELD_RADIUS_UNIT, 1f)
                            }
                        )
                    }
                }

                // --- STRENGTH ---
                NomadSculptSlider(
                    label = "Strength",
                    value = strength,
                    range = 0f..1f,
                    hasPressureIcon = true,
                    isPressureActive = strengthPressure,
                    onPressureToggle = { strengthPressure = !strengthPressure },
                    onValueChange = { s ->
                        strength = s.coerceIn(0f, 1f)
                        OBLNativeActivity.setActiveBrushStrengthStatic(strength)
                    }
                )

                // --- HARDNESS ---
                NomadSculptSlider(
                    label = "Hardness",
                    value = hardness,
                    range = 0f..1f,
                    onValueChange = { h ->
                        hardness = h.coerceIn(0f, 1f)
                        OBLNativeActivity.setActiveBrushExtraStatic(FIELD_HARDNESS, hardness)
                    }
                )

                // --- NORMAL RADIUS (real: 0..2 en Blender) ---
                NomadSculptSlider(
                    label = "Normal Radius",
                    value = normalRadius,
                    range = 0f..2f,
                    onValueChange = { n ->
                        normalRadius = n.coerceIn(0f, 2f)
                        OBLNativeActivity.setActiveBrushExtraStatic(FIELD_NORMAL_RADIUS, normalRadius)
                    }
                )

                // --- AUTO-SMOOTH ---
                NomadSculptSlider(
                    label = "Auto-Smooth",
                    value = autoSmooth,
                    range = 0f..1f,
                    onValueChange = { a ->
                        autoSmooth = a.coerceIn(0f, 1f)
                        OBLNativeActivity.setActiveBrushExtraStatic(FIELD_AUTOSMOOTH, autoSmooth)
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NomadCardBorder)
                )

                // --- SWITCHES ---
                NomadSwitch(
                    title = "Front Faces Only",
                    subtitle = "Affect visible mesh",
                    checked = isFrontFaces,
                    onCheckedChange = { it ->
                        isFrontFaces = it
                        OBLNativeActivity.setActiveBrushExtraStatic(FIELD_FRONT_FACES, if (it) 1f else 0f)
                    }
                )

                NomadSwitch(
                    title = "Accumulate",
                    subtitle = "Accumulate strokes",
                    checked = isAccumulate,
                    onCheckedChange = { it ->
                        isAccumulate = it
                        OBLNativeActivity.setActiveBrushExtraStatic(FIELD_ACCUMULATE, if (it) 1f else 0f)
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NomadCardBorder)
                )

                // --- DIRECTION ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NomadTrackBg)
                        .padding(3.dp)
                ) {
                    NomadSegmentedBtn(
                        text = "Add (+)",
                        isSelected = direction == 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            direction = 0
                            OBLNativeActivity.setActiveBrushExtraStatic(FIELD_DIRECTION, 0f)
                        }
                    )
                    NomadSegmentedBtn(
                        text = "Subtract (-)",
                        isSelected = direction == 1,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            direction = 1
                            OBLNativeActivity.setActiveBrushExtraStatic(FIELD_DIRECTION, 1f)
                        }
                    )
                }
            }
        }
    }
}