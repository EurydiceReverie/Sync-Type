package com.lanremotetype.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lanremotetype.ui.theme.*
import com.lanremotetype.util.SoundHelper

@Composable
fun LiquidCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)

    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable {
                SoundHelper.playClick()
                onClick()
            }
    } else {
        modifier.clip(shape)
    }

    val backgroundBrush = if (containerColor != null) {
        Brush.verticalGradient(
            colors = listOf(
                containerColor,
                containerColor.copy(alpha = 0.8f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                LiquidSurfaceHighlight.copy(alpha = 0.6f),
                LiquidSurface.copy(alpha = 0.4f)
            )
        )
    }

    Box(
        modifier = cardModifier
            .background(brush = backgroundBrush)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        LiquidBorderBright,
                        LiquidBorder.copy(alpha = 0.3f)
                    )
                ),
                shape = shape
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(0.5f, 0f),
                        radius = 400f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: LiquidButtonColors = LiquidButtonDefaults.primaryColors(),
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val alpha = if (enabled) 1f else 0.5f

    Box(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled) {
                SoundHelper.playClick()
                SoundHelper.vibrateLight()
                onClick()
            }
            .background(
                brush = Brush.horizontalGradient(
                    colors = colors.gradientColors.map { it.copy(alpha = alpha) }
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = colors.borderColors.map { it.copy(alpha = alpha * 0.5f) }
                ),
                shape = shape
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

data class LiquidButtonColors(
    val gradientColors: List<Color>,
    val borderColors: List<Color>
)

object LiquidButtonDefaults {
    fun primaryColors() = LiquidButtonColors(
        gradientColors = listOf(LiquidPrimaryDark, LiquidPrimary),
        borderColors = listOf(LiquidPrimary.copy(alpha = 0.5f), LiquidTeal.copy(alpha = 0.3f))
    )

    fun secondaryColors() = LiquidButtonColors(
        gradientColors = listOf(LiquidSecondary.copy(alpha = 0.7f), LiquidSecondary),
        borderColors = listOf(LiquidSecondary.copy(alpha = 0.4f), LiquidPink.copy(alpha = 0.3f))
    )

    fun dangerColors() = LiquidButtonColors(
        gradientColors = listOf(LiquidRed.copy(alpha = 0.7f), LiquidRed),
        borderColors = listOf(LiquidRed.copy(alpha = 0.4f), LiquidOrange.copy(alpha = 0.3f))
    )

    fun successColors() = LiquidButtonColors(
        gradientColors = listOf(LiquidGreen.copy(alpha = 0.7f), LiquidGreen),
        borderColors = listOf(LiquidGreen.copy(alpha = 0.4f), LiquidTeal.copy(alpha = 0.3f))
    )

    fun neutralColors() = LiquidButtonColors(
        gradientColors = listOf(LiquidSurfaceHighlight, LiquidSurfaceLight),
        borderColors = listOf(LiquidBorder, LiquidBorder.copy(alpha = 0.3f))
    )
}

@Composable
fun LiquidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxLines: Int = 1,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        LiquidSurfaceHighlight.copy(alpha = 0.5f),
                        LiquidSurface.copy(alpha = 0.3f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        LiquidBorder.copy(alpha = 0.6f),
                        LiquidBorder.copy(alpha = 0.2f)
                    )
                ),
                shape = shape
            )
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = LiquidOnSurfaceSecondary) },
            placeholder = placeholder?.let { { Text(it, color = LiquidOnSurfaceSecondary.copy(alpha = 0.5f)) } },
            maxLines = maxLines,
            colors = TextFieldDefaults.colors(
                focusedTextColor = LiquidOnSurface,
                unfocusedTextColor = LiquidOnSurface,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedLabelColor = LiquidPrimary,
                unfocusedLabelColor = LiquidOnSurfaceSecondary,
                cursorColor = LiquidPrimary
            ),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = trailingIcon
        )
    }
}

@Composable
fun LiquidChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val animatedAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        animationSpec = tween(200)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            LiquidPrimaryDark.copy(alpha = animatedAlpha),
                            LiquidPrimary.copy(alpha = animatedAlpha)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            LiquidSurfaceHighlight.copy(alpha = 0.4f * animatedAlpha),
                            LiquidSurface.copy(alpha = 0.2f * animatedAlpha)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = if (selected) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            LiquidPrimary.copy(alpha = 0.6f),
                            LiquidTeal.copy(alpha = 0.4f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            LiquidBorder.copy(alpha = 0.4f),
                            LiquidBorder.copy(alpha = 0.15f)
                        )
                    )
                },
                shape = shape
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else LiquidOnSurfaceSecondary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun LiquidGlowCircle(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = color,
                shape = RoundedCornerShape(50)
            )
    )
    Box(
        modifier = modifier
            .size(size * 2)
            .background(
                color = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(50)
            )
    )
}

@Composable
fun LiquidSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = LiquidOnSurface,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun LiquidSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LiquidOnSurfaceSecondary
    )
}
