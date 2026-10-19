package xyz.saltedchips.bookyplayer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.saltedchips.bookyplayer.settings.LibraryLayout

@Composable
fun LibraryLayoutPicker(
    selected: LibraryLayout,
    onSelect: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryLayout.entries.forEach { option ->
            val isSelected = option == selected
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) scheme.surfaceContainerHighest else scheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        color = if (isSelected) scheme.primary else scheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelect(option) },
                            role = Role.RadioButton,
                        ),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        SkeletonFilterChips()
                        Spacer(Modifier.height(8.dp))
                        when (option) {
                            LibraryLayout.Rows -> SkeletonRows()
                            LibraryLayout.Hero -> SkeletonHero()
                            LibraryLayout.Grid -> SkeletonGrid()
                        }
                    }
                }
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun skeletonTone() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

@Composable
private fun skeletonAccent() = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    radius: Dp,
    color: Color,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(color),
    )
}

@Composable
private fun SkeletonFilterChips() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        SkeletonBlock(Modifier.width(14.dp).height(8.dp), 3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        SkeletonBlock(Modifier.width(22.dp).height(8.dp), 3.dp, skeletonTone())
        SkeletonBlock(Modifier.width(18.dp).height(8.dp), 3.dp, skeletonTone())
    }
}

@Composable
private fun SkeletonTextLines(
    firstWidth: Dp,
    secondWidth: Dp,
    height: Dp = 4.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SkeletonBlock(Modifier.width(firstWidth).height(height), 2.dp, skeletonTone())
        SkeletonBlock(Modifier.width(secondWidth).height(height), 2.dp, skeletonTone().copy(alpha = 0.09f))
    }
}

@Composable
private fun SkeletonRows() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(4) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            ) {
                if (index != 3) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (index == 0) 0.66f else 0.3f)
                            .height(26.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    )
                }
                Row(
                    Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SkeletonBlock(Modifier.size(20.dp), 6.dp, skeletonAccent())
                    SkeletonTextLines(30.dp, 20.dp)
                }
            }
        }
    }
}

@Composable
private fun SkeletonHero() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            SkeletonBlock(Modifier.size(34.dp), 8.dp, skeletonAccent())
            Column(
                Modifier.height(34.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                SkeletonTextLines(26.dp, 18.dp)
                SkeletonBlock(
                    Modifier.width(32.dp).height(8.dp),
                    4.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
            }
        }
        repeat(3) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SkeletonBlock(Modifier.size(16.dp), 5.dp, skeletonAccent())
                SkeletonTextLines(32.dp, 20.dp, height = 3.dp)
            }
        }
    }
}

@Composable
private fun SkeletonGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SkeletonBlock(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            9.dp,
                            skeletonAccent(),
                        )
                        SkeletonBlock(Modifier.width(24.dp).height(3.dp), 2.dp, skeletonTone())
                    }
                }
            }
        }
    }
}
