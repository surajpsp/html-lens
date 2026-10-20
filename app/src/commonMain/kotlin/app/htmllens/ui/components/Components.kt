package app.htmllens.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.htmllens.platform.EngineStatus
import app.htmllens.ui.theme.Lens

enum class BtnStyle { PRIMARY, SECONDARY, GHOST, DANGER, SOFT }

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: BtnStyle = BtnStyle.SECONDARY,
    enabled: Boolean = true,
    compact: Boolean = false,
    trailing: ImageVector? = null,
) {
    val c = Lens.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val (bg, fg, border) = when (style) {
        BtnStyle.PRIMARY -> Triple(if (hovered) c.ink.copy(alpha = 0.86f) else c.ink, c.onInk, Color.Transparent)
        BtnStyle.SECONDARY -> Triple(if (hovered) c.surface2 else Color.Transparent, c.text, c.borderStrong)
        BtnStyle.GHOST -> Triple(if (hovered) c.surface2 else Color.Transparent, c.text, Color.Transparent)
        BtnStyle.DANGER -> Triple(if (hovered) c.danger.copy(alpha = 0.9f) else c.danger, if (c.isDark) c.bg else Color.White, Color.Transparent)
        BtnStyle.SOFT -> Triple(if (hovered) c.primarySoft.copy(alpha = 0.75f) else c.primarySoft, c.primary, Color.Transparent)
    }
    val animatedBg by animateColorAsState(bg)
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 32.dp else 42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) animatedBg else c.surface2)
            .border(1.dp, if (enabled) border else c.border, RoundedCornerShape(6.dp))
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (compact) 11.dp else 18.dp, vertical = if (compact) 5.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) fg else c.textFaint
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        }
        Text(
            text, color = tint, maxLines = 1,
            style = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
                .copy(fontWeight = if (style == BtnStyle.PRIMARY) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailing, null, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun IconBtn(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    size: Dp = 34.dp,
    badge: String? = null,
    badgeColor: Color? = null,
) {
    val c = Lens.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        active -> c.primarySoft
                        hovered && enabled -> c.surface2
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, tooltip,
                tint = when {
                    !enabled -> c.textFaint
                    tint != null -> tint
                    active -> c.primary
                    else -> c.textMuted
                },
                modifier = Modifier.size(size * 0.56f),
            )
            if (badge != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 1.dp)
                        .clip(CircleShape).background(badgeColor ?: c.danger).padding(horizontal = 4.dp),
                ) {
                    Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = color.copy(alpha = 0.13f),
    icon: ImageVector? = null,
) {
    Row(
        modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, count: Int? = null) {
    val c = Lens.colors
    Row(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) c.ink else Color.Transparent)
            .border(1.dp, if (selected) c.ink else c.borderStrong, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) c.onInk else c.textMuted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onInk else c.textMuted)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = if (selected) c.onInk.copy(alpha = 0.7f) else c.textFaint)
        }
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    background: Color? = null,
    border: Color? = null,
    content: @Composable () -> Unit,
) {
    val c = Lens.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background ?: if (hovered && onClick != null) c.surface2 else c.surface)
            .border(1.dp, border ?: if (hovered && onClick != null) c.borderStrong else c.border, RoundedCornerShape(10.dp))
            .hoverable(interaction)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = Lens.colors.text, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontFamily = Lens.mono), color = Lens.colors.accent, modifier = modifier, letterSpacing = 1.sp)
}

@Composable
fun Divider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = Lens.colors.border)

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
    leading: ImageVector = Icons.Rounded.Search,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val c = Lens.colors
    Row(
        modifier.height(38.dp).clip(RoundedCornerShape(6.dp)).background(c.surface)
            .border(1.dp, c.borderStrong, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(leading, null, tint = c.textFaint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = c.textFaint, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }, onDone = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty() && trailingContent == null) {
            Icon(Icons.Rounded.Close, "Clear", tint = c.textFaint, modifier = Modifier.size(16.dp).clip(CircleShape).clickable { onValueChange("") })
        }
        trailingContent?.invoke()
    }
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    mono: Boolean = false,
    minHeight: Dp = 40.dp,
    onSubmit: (() -> Unit)? = null,
) {
    val c = Lens.colors
    val style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = Lens.mono, fontSize = 12.5.sp, color = c.text)
    else MaterialTheme.typography.bodyMedium.copy(color = c.text)
    Box(
        modifier.heightIn(min = minHeight).clip(RoundedCornerShape(6.dp)).background(c.surface)
            .border(1.dp, c.borderStrong, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
    ) {
        if (value.isEmpty()) Text(placeholder, color = c.textFaint, style = style)
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine, textStyle = style,
            cursorBrush = SolidColor(c.primary), modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Lens.colors
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.textFaint, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.widthIn(max = 420.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun IconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, gradient: Boolean = false) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.22f))
            .background(if (gradient) SolidColor(color) else SolidColor(color.copy(alpha = 0.12f))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (gradient) Color.White else color, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier, mono: Boolean = false) {
    val c = Lens.colors
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.width(120.dp))
        Text(
            value, style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono) else MaterialTheme.typography.bodySmall,
            color = c.text, modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EngineStatusView(status: EngineStatus, modifier: Modifier = Modifier, onRetry: () -> Unit = {}) {
    val c = Lens.colors
    Box(modifier.fillMaxSize().background(c.surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 380.dp).padding(24.dp)) {
            when (status) {
                is EngineStatus.Preparing -> {
                    if (status.progress == null) CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                    else {
                        Text("${(status.progress * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, color = c.text)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { status.progress }, color = c.primary, trackColor = c.surface3, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(status.step, style = MaterialTheme.typography.titleSmall, color = c.text)
                    Spacer(Modifier.height(4.dp))
                    Text("HTML Lens renders pages with a private copy of Chromium that runs entirely on your computer.",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                is EngineStatus.Failed -> {
                    Text("Rendering engine unavailable", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Text(status.message, style = MaterialTheme.typography.bodySmall, color = c.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Btn("Try again", onRetry, style = BtnStyle.PRIMARY)
                }
                EngineStatus.Ready -> Unit
            }
        }
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Lens.colors
    val track by animateColorAsState(if (checked) c.primary else c.surface3)
    Box(
        modifier.size(width = 38.dp, height = 22.dp).clip(RoundedCornerShape(50)).background(track).clickable { onChange(!checked) }.padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun SettingRow(title: String, subtitle: String?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    val c = Lens.colors
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
        trailing()
    }
}

@Composable
fun Segmented(
    options: List<Pair<String, ImageVector?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null,
    compact: Boolean = false,
) {
    val c = Lens.colors
    Row(
        modifier.clip(RoundedCornerShape(7.dp)).border(1.dp, c.borderStrong, RoundedCornerShape(7.dp)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, (label, icon) ->
            val active = i == selected
            val accent = colors?.getOrNull(i) ?: c.primary
            val bg by animateColorAsState(if (active) (colors?.getOrNull(i)?.copy(alpha = 0.16f) ?: c.surface3) else Color.Transparent)
            Row(
                Modifier.clip(RoundedCornerShape(5.dp)).background(bg)
                    .clickable { onSelect(i) }
                    .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 4.dp else 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = if (active) accent else c.textFaint, modifier = Modifier.size(15.dp))
                    if (label.isNotEmpty()) Spacer(Modifier.width(5.dp))
                }
                if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal), color = if (active) (colors?.getOrNull(i) ?: c.text) else c.textMuted)
            }
        }
    }
}

@Composable
fun ProgressOverlay(message: String) {
    val c = Lens.colors
    Box(Modifier.fillMaxSize().background(c.scrim), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(c.surface).border(1.dp, c.borderStrong, RoundedCornerShape(8.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = c.primary, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(message, style = MaterialTheme.typography.titleSmall, color = c.text)
        }
    }
}
