package com.tim.futureai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer as MaterialModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.tim.futureai.ui.liquid.LocalGlassPrefs
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.theme.ContinuousCornerShape
import kotlin.math.roundToInt

/** Gather's primary action: quiet fill, luminous rim, and a short pressed response. */
@Composable
fun GatherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ContinuousCornerShape(18.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val spec = LocalLiquidSpec.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalGlassPrefs.current.reduceMotion
    val scale by animateFloatAsState(if (pressed && !reduce) 0.975f else 1f, spring(dampingRatio = 0.82f, stiffness = 520f), label = "gather-primary-press")
    Box(
        modifier.defaultMinSize(minHeight = 46.dp).scale(scale).clip(shape)
            .background(Brush.verticalGradient(listOf(spec.accentBright, spec.accent.copy(alpha = if (enabled) 1f else 0.55f))))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (pressed) 0.16f else 0.075f), Color.Transparent)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.52f), spec.accent.copy(alpha = 0.34f))), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content) }
}

/** Secondary action with a translucent edge instead of the stock text-button ripple. */
@Composable
fun GatherTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ContinuousCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val spec = LocalLiquidSpec.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.defaultMinSize(minHeight = 40.dp).clip(shape)
            .background(if (pressed) spec.surfaceTint.copy(alpha = 0.85f) else Color.Transparent)
            .border(if (pressed) 1.dp else 0.5.dp, spec.surfaceEdge.copy(alpha = if (pressed) 0.9f else 0.34f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(contentPadding), contentAlignment = Alignment.Center,
    ) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content) }
}

/** Continuous-corner, theme-aware input with a quiet inset-glass fill. */
@Composable
fun GatherOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = ContinuousCornerShape(17.dp),
    colors: TextFieldColors = gatherFieldColors(),
) {
    MaterialOutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        enabled = enabled, readOnly = readOnly, textStyle = textStyle,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        prefix = prefix, suffix = suffix, supportingText = supportingText,
        isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        interactionSource = interactionSource, shape = shape, colors = colors,
    )
}

@Composable
private fun gatherFieldColors(): TextFieldColors {
    val spec = LocalLiquidSpec.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = spec.text, unfocusedTextColor = spec.text,
        disabledTextColor = spec.muted, errorTextColor = spec.text,
        focusedContainerColor = spec.surfaceTint.copy(alpha = 0.52f),
        unfocusedContainerColor = spec.surfaceTint.copy(alpha = 0.32f),
        disabledContainerColor = spec.surfaceTint.copy(alpha = 0.18f),
        errorContainerColor = spec.surfaceTint.copy(alpha = 0.42f),
        cursorColor = spec.accentBright, errorCursorColor = spec.danger,
        focusedBorderColor = spec.accentBright.copy(alpha = 0.92f),
        unfocusedBorderColor = spec.surfaceEdge.copy(alpha = 0.86f),
        disabledBorderColor = spec.surfaceEdge.copy(alpha = 0.46f),
        errorBorderColor = spec.danger,
        focusedLabelColor = spec.accentBright, unfocusedLabelColor = spec.muted,
        disabledLabelColor = spec.muted.copy(alpha = 0.65f), errorLabelColor = spec.danger,
        focusedPlaceholderColor = spec.muted.copy(alpha = 0.82f), unfocusedPlaceholderColor = spec.muted.copy(alpha = 0.72f),
    )
}

@Composable
fun GatherDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = ContinuousCornerShape(20.dp),
    containerColor: Color = LocalLiquidSpec.current.surfaceTint,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 14.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    MaterialDropdownMenu(
        expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier,
        offset = offset, properties = properties, shape = shape, containerColor = containerColor,
        tonalElevation = tonalElevation, shadowElevation = shadowElevation,
        border = BorderStroke(0.9.dp, LocalLiquidSpec.current.surfaceEdge.copy(alpha = 0.82f)),
        content = content,
    )
}

@Composable
fun GatherDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null,
) {
    val spec = LocalLiquidSpec.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val foreground = if (enabled) spec.text else spec.muted.copy(alpha = 0.54f)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides foreground) {
        Row(
            modifier.fillMaxWidth().heightIn(min = 48.dp).clip(ContinuousCornerShape(12.dp))
                .background(if (pressed) spec.surfaceEdge.copy(alpha = 0.22f) else Color.Transparent)
                .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            Box(Modifier.weight(1f)) { text() }
            trailingIcon?.invoke()
        }
    }
}

@Composable
fun GatherFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedContainerColor: Color = LocalLiquidSpec.current.accent.copy(alpha = 0.16f),
    selectedLabelColor: Color = LocalLiquidSpec.current.accentBright,
) {
    val spec = LocalLiquidSpec.current
    val shape = ContinuousCornerShape(50.dp)
    Box(
        modifier.clip(shape)
            .background(if (selected) selectedContainerColor else spec.surfaceTint.copy(alpha = 0.22f))
            .border(1.dp, if (selected) selectedLabelColor.copy(alpha = 0.64f) else spec.surfaceEdge.copy(alpha = 0.52f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides if (selected) selectedLabelColor else spec.muted) {
            label()
        }
    }
}

@Composable
fun GatherModalNavigationDrawer(
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    gesturesEnabled: Boolean = true,
    scrimColor: Color = Color.Black.copy(alpha = 0.60f),
    content: @Composable () -> Unit,
) {
    MaterialModalNavigationDrawer(
        drawerContent = drawerContent, modifier = modifier, drawerState = drawerState,
        gesturesEnabled = gesturesEnabled, scrimColor = scrimColor, content = content,
    )
}

@Composable
fun GatherModalDrawerSheet(
    modifier: Modifier = Modifier,
    drawerContainerColor: Color = LocalLiquidSpec.current.surfaceTint,
    drawerContentColor: Color = LocalLiquidSpec.current.text,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val spec = LocalLiquidSpec.current
    val shape = ContinuousCornerShape(27.dp)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides drawerContentColor) {
        androidx.compose.foundation.layout.Column(
            modifier.fillMaxHeight().clip(shape)
                .background(Brush.horizontalGradient(listOf(drawerContainerColor, spec.surfaceTint)))
                .border(1.dp, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.34f), spec.surfaceEdge.copy(alpha = 0.64f))), shape)
                .statusBarsPadding().navigationBarsPadding(),
            content = content,
        )
    }
}

@Composable
fun GatherCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalLiquidSpec.current.accentBright,
    strokeWidth: Dp = 3.dp,
) {
    val transition = rememberInfiniteTransition(label = "gather-progress")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing)), label = "gather-progress-rotation",
    )
    Canvas(modifier) {
        val stroke = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
        val diameter = (size.minDimension - strokeWidth.toPx()).coerceAtLeast(0f)
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
        drawArc(color.copy(alpha = 0.14f), 0f, 360f, false, topLeft, arcSize, style = stroke)
        rotate(rotation, pivot = center) {
            drawArc(color.copy(alpha = 0.28f), 0f, 220f, false, topLeft, arcSize, style = stroke)
            drawArc(color, 220f, 96f, false, topLeft, arcSize, style = stroke)
        }
    }
}

@Composable
fun GatherLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = LocalLiquidSpec.current.accentBright,
    trackColor: Color = LocalLiquidSpec.current.surfaceEdge.copy(alpha = 0.42f),
) {
    val fraction by animateFloatAsState(progress().coerceIn(0f, 1f), tween(260, easing = LinearEasing), label = "gather-linear-progress")
    val accent = LocalLiquidSpec.current.accent
    Canvas(modifier.heightIn(min = 3.dp)) {
        val barHeight = 3.dp.toPx().coerceAtMost(size.height)
        val top = (size.height - barHeight) / 2f
        val corner = barHeight / 2f
        drawRoundRect(trackColor, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, barHeight), androidx.compose.ui.geometry.CornerRadius(corner))
        if (fraction > 0f) drawRoundRect(
            Brush.horizontalGradient(listOf(accent, color)), Offset(0f, top),
            androidx.compose.ui.geometry.Size(size.width * fraction, barHeight), androidx.compose.ui.geometry.CornerRadius(corner),
        )
    }
}

@Composable
fun GatherIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val spec = LocalLiquidSpec.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.78f, stiffness = 560f), label = "gather-icon-press")
    Box(modifier.size(44.dp).clip(ContinuousCornerShape(15.dp))
        .scale(scale).background(spec.surfaceTint.copy(alpha = if (pressed) 0.58f else 0.24f))
        .border(0.7.dp, spec.surfaceEdge.copy(alpha = 0.52f), ContinuousCornerShape(15.dp))
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/** Compact glass switch with an animated luminous thumb and full switch semantics. */
@Composable
fun GatherSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val spec = LocalLiquidSpec.current
    val reduce = LocalGlassPrefs.current.reduceMotion
    val progress by animateFloatAsState(if (checked) 1f else 0f, if (reduce) androidx.compose.animation.core.snap() else spring(dampingRatio = 0.86f, stiffness = 420f), label = "gather-switch")
    val shape = ContinuousCornerShape(50.dp)
    Box(modifier.size(width = 54.dp, height = 32.dp)
        .toggleable(value = checked, enabled = enabled && onCheckedChange != null, role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() }, indication = null, onValueChange = { onCheckedChange?.invoke(it) })
        .semantics { stateDescription = if (checked) "已开启" else "已关闭" }
        .clip(shape)
        .background(if (checked) Brush.horizontalGradient(listOf(spec.accent, spec.accentBright)) else Brush.horizontalGradient(listOf(spec.surfaceTint, spec.ambientTop)))
        .border(1.dp, if (checked) spec.accentBright.copy(alpha = 0.8f) else spec.surfaceEdge.copy(alpha = 0.8f), shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.matchParentSize()) { drawLine(Color.White.copy(alpha = if (checked) 0.32f else 0.12f), Offset(15.dp.toPx(), 1.dp.toPx()), Offset(size.width - 15.dp.toPx(), 1.dp.toPx()), strokeWidth = 1.dp.toPx()) }
        Box(Modifier.padding(start = 3.dp).offsetX(progress * 22f).size(26.dp).clip(ContinuousCornerShape(50.dp))
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E7F0))))
            .border(0.7.dp, Color.White.copy(alpha = 0.9f), ContinuousCornerShape(50.dp)))
    }
}

@Composable
fun GatherCheckBox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val spec = LocalLiquidSpec.current
    val shape = ContinuousCornerShape(7.dp)
    Box(modifier.size(24.dp).toggleable(value = checked, enabled = enabled && onCheckedChange != null, role = Role.Checkbox,
        interactionSource = remember { MutableInteractionSource() }, indication = null, onValueChange = { onCheckedChange?.invoke(it) })
        .clip(shape).background(if (checked) spec.accent else spec.surfaceTint.copy(alpha = 0.4f))
        .border(1.dp, if (checked) spec.accentBright else spec.surfaceEdge, shape), contentAlignment = Alignment.Center) {
        if (checked) Text("✓", color = Color.White, fontSize = 15.sp, lineHeight = 16.sp)
    }
}

@Composable
fun GatherSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0) {
    val spec = LocalLiquidSpec.current
    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range <= 0f) 0f else ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val shape = ContinuousCornerShape(50.dp)
    Box(modifier.fillMaxWidth().heightIn(min = 34.dp).semantics {
        progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, valueRange, steps)
        if (enabled) setProgress { target -> onValueChange(target.coerceIn(valueRange)); true }
    }.pointerInput(enabled, valueRange, steps) {
        if (enabled) detectTapGestures { point ->
            val raw = (point.x / size.width).coerceIn(0f, 1f)
            val snapped = if (steps > 0) (raw * (steps + 1)).roundToInt().toFloat() / (steps + 1) else raw
            onValueChange(valueRange.start + snapped * range)
        }
    }.pointerInput(enabled, valueRange, steps) {
        if (enabled) detectDragGestures(onDragStart = { p -> onValueChange(valueRange.start + (p.x / size.width).coerceIn(0f, 1f) * range) }) { change, _ ->
            val raw = (change.position.x / size.width).coerceIn(0f, 1f)
            val snapped = if (steps > 0) (raw * (steps + 1)).roundToInt().toFloat() / (steps + 1) else raw
            onValueChange(valueRange.start + snapped * range)
        }
    }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(shape).background(spec.surfaceEdge.copy(alpha = 0.42f)))
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(shape).background(Brush.horizontalGradient(listOf(spec.accent, spec.accentBright))))
        Canvas(Modifier.matchParentSize()) {
            val x = size.width * fraction
            drawCircle(Color.White.copy(alpha = 0.13f), 10.dp.toPx(), Offset(x, size.height / 2))
            drawCircle(Color.White, 6.dp.toPx(), Offset(x, size.height / 2))
            drawCircle(spec.accentBright, 3.dp.toPx(), Offset(x, size.height / 2))
        }
    }
}

@Composable
fun GatherAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = ContinuousCornerShape(30.dp),
    containerColor: Color = Color.Unspecified,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val spec = LocalLiquidSpec.current
    val surface = if (containerColor == Color.Unspecified) spec.ambientMiddle else containerColor
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(modifier.fillMaxWidth().clip(shape)
            .background(surface.copy(alpha = 0.98f), shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)), shape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), spec.surfaceEdge.copy(alpha = 0.4f))), shape)
            .padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (icon != null) Box(Modifier.align(Alignment.CenterHorizontally)) { icon() }
            if (title != null) Box { title() }
            if (text != null) Box { text() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (dismissButton != null) Box { dismissButton() }
                Box { confirmButton() }
            }
        }
    }
}

private fun Modifier.offsetX(x: Float): Modifier = this.then(Modifier.padding(start = x.dp))
