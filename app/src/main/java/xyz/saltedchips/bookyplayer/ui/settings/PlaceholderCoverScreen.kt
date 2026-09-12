package xyz.saltedchips.bookyplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.saltedchips.bookyplayer.settings.PlaceholderCoverStyle
import xyz.saltedchips.bookyplayer.settings.PlaceholderFont
import xyz.saltedchips.bookyplayer.theme.placeholderShapeOptions
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons
import xyz.saltedchips.bookyplayer.ui.components.EditorialCover
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaceholderCoverScreen(
    style: PlaceholderCoverStyle,
    previewTitle: String,
    onStyleChange: (PlaceholderCoverStyle) -> Unit,
    onBack: () -> Unit,
    bottomContentPadding: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shapes = placeholderShapeOptions()
    val fonts = PlaceholderFont.entries
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomContentPadding),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(48.dp))
            EditorialCover(
                title = previewTitle,
                seed = previewTitle,
                style = style,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text("Shape", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shapes, key = { it.id }) { option ->
                    val selected = style.shapeId == option.id
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                onClick = { onStyleChange(style.copy(shapeId = option.id)) },
                                onClickLabel = option.label,
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) colors.primary else colors.outlineVariant,
                                shape = RoundedCornerShape(16.dp),
                            ),
                    ) {
                        EditorialCover(
                            title = previewTitle,
                            seed = previewTitle,
                            style = style.copy(shapeId = option.id),
                            showTitle = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Typeface", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                fonts.forEachIndexed { index, font ->
                    ToggleButton(
                        checked = style.font == font,
                        onCheckedChange = { checked ->
                            if (checked) onStyleChange(style.copy(font = font))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            fonts.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) {
                        Text(font.label, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            AxisSlider(
                label = "Weight",
                value = style.weight,
                valueRange = 100f..900f,
                valueLabel = style.weight.roundToInt().toString(),
                onChange = { onStyleChange(style.copy(weight = it)) },
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 4.dp, top = 2.dp),
        ) {
            Icon(BookyIcons.back, contentDescription = "Back")
        }
    }
}

@Composable
private fun AxisSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
        )
    }
}
