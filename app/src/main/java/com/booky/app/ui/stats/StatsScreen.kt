package com.booky.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.booky.app.data.MockStats
import com.booky.app.data.TimeSavedKind
import com.booky.app.ui.components.BookyIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text("Stats", maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(MockStats.sinceLabel, style = MaterialTheme.typography.bodyLarge)
            Text(
                MockStats.listeningHeadline,
                style = MaterialTheme.typography.displaySmall,
                color = colors.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                MockStats.flavorText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
            ) {
                Text(
                    "LISTENING ACTIVITY",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above,
                    ),
                    tooltip = {
                        PlainTooltip {
                            Text("Each square is a day. Darker means you listened more.")
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                        Icon(
                            BookyIcons.info,
                            contentDescription = "About listening activity",
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            ListeningHeatmap()

            Text(
                "TIME SAVED BY",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
            )
            MockStats.timeSaved.forEach { row ->
                ListItem(
                    headlineContent = { Text(row.label) },
                    leadingContent = {
                        Icon(iconFor(row.icon), contentDescription = null, tint = colors.onSurfaceVariant)
                    },
                    trailingContent = {
                        Text(row.value, color = colors.primary, style = MaterialTheme.typography.bodyMedium)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = {
                    Text("TOTAL", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                },
                trailingContent = {
                    Text(
                        MockStats.totalSaved,
                        color = colors.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun iconFor(kind: TimeSavedKind): ImageVector = when (kind) {
    TimeSavedKind.Skipping -> BookyIcons.skipping
    TimeSavedKind.Speed -> BookyIcons.speed
    TimeSavedKind.TrimSilence -> BookyIcons.trimSilence
    TimeSavedKind.AutoSkipping -> BookyIcons.autoSkipping
}

@Composable
private fun ListeningHeatmap() {
    val colors = MaterialTheme.colorScheme
    val cell = 12.dp
    val gap = 3.dp
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MockStats.months.forEach { month ->
                Text(month, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Row {
            Column(
                modifier = Modifier.width(32.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                MockStats.weekdayLabels.forEach { label ->
                    Box(
                        modifier = Modifier.height(cell),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (label.isNotEmpty()) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                MockStats.activity.forEach { weekRow ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        weekRow.forEach { intensity ->
                            Box(
                                modifier = Modifier
                                    .size(cell)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(heatmapColor(intensity, colors.primary, colors.surfaceContainerHighest)),
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            (0..4).forEach { intensity ->
                Box(
                    modifier = Modifier
                        .padding(end = 3.dp)
                        .size(cell)
                        .clip(RoundedCornerShape(2.dp))
                        .background(heatmapColor(intensity, colors.primary, colors.surfaceContainerHighest)),
                )
            }
            Text("More", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun heatmapColor(intensity: Int, primary: Color, empty: Color): Color {
    return when (intensity) {
        0 -> empty
        1 -> primary.copy(alpha = 0.25f)
        2 -> primary.copy(alpha = 0.5f)
        3 -> primary.copy(alpha = 0.75f)
        else -> primary
    }
}
