package com.booky.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.booky.app.ui.navigation.TopLevelDestination

@Composable
fun BookyBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
    ) {
        destinations.forEach { item ->
            val selected = current == item.destination
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}

private data class NavDestination(
    val destination: TopLevelDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val destinations: List<NavDestination>
    @Composable get() = listOf(
        NavDestination(
            TopLevelDestination.Library,
            "Library",
            BookyIcons.libraryFilled,
            BookyIcons.library,
        ),
        NavDestination(
            TopLevelDestination.Stats,
            "Stats",
            BookyIcons.statsFilled,
            BookyIcons.stats,
        ),
        NavDestination(
            TopLevelDestination.Settings,
            "Settings",
            BookyIcons.settingsFilled,
            BookyIcons.settings,
        ),
    )
