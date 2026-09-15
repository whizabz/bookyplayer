package xyz.saltedchips.bookyplayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.saltedchips.bookyplayer.BuildConfig
import xyz.saltedchips.bookyplayer.R
import xyz.saltedchips.bookyplayer.settings.AppearanceMode
import xyz.saltedchips.bookyplayer.settings.ColorTheme
import xyz.saltedchips.bookyplayer.settings.ContrastPreference
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    appearance: AppearanceMode,
    onAppearanceChange: (AppearanceMode) -> Unit,
    colorTheme: ColorTheme = ColorTheme.System,
    onColorThemeChange: (ColorTheme) -> Unit = {},
    contrastPreference: ContrastPreference = ContrastPreference.System,
    onContrastPreferenceChange: (ContrastPreference) -> Unit = {},
    skipBackSeconds: Int = 10,
    skipForwardSeconds: Int = 10,
    onSkipBackSecondsChange: (Int) -> Unit = {},
    onSkipForwardSecondsChange: (Int) -> Unit = {},
    smartResumeEnabled: Boolean = false,
    smartResumeSeconds: Int = 5,
    onSmartResumeEnabledChange: (Boolean) -> Unit = {},
    onSmartResumeSecondsChange: (Int) -> Unit = {},
    folderPath: String? = null,
    onChooseFolder: () -> Unit = {},
    playbackNotificationsEnabled: Boolean? = null,
    onPlaybackNotificationsClick: () -> Unit = {},
    onOpenPlaceholderCover: () -> Unit = {},
    onBack: () -> Unit = {},
    bottomContentPadding: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text("Settings", maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(BookyIcons.back, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (playbackNotificationsEnabled == false) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(bottom = 16.dp)) {
                        ListItem(
                            headlineContent = { Text("Stay in control while you listen") },
                            supportingContent = {
                                Text(
                                    "Booky Player shows a playback notification so you can pause, skip, " +
                                        "and see the current book from the lock screen and notification shade " +
                                        "when you leave the app. It is not used for ads or other alerts.",
                                )
                            },
                            leadingContent = {
                                Icon(
                                    BookyIcons.warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        Button(
                            onClick = onPlaybackNotificationsClick,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Text("Tap to allow")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Audiobook folder") },
                    supportingContent = {
                        Text(
                            folderPath ?: "Choose a folder for your audiobooks",
                        )
                    },
                    leadingContent = { Icon(BookyIcons.folder, contentDescription = null) },
                    trailingContent = { Icon(BookyIcons.chevronRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onChooseFolder),
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(bottom = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("Appearance") },
                        supportingContent = {
                            Text("System follows the device theme")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    val modes = AppearanceMode.entries
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            ButtonGroupDefaults.ConnectedSpaceBetween,
                        ),
                    ) {
                        modes.forEachIndexed { index, mode ->
                            ToggleButton(
                                checked = appearance == mode,
                                onCheckedChange = { checked ->
                                    if (checked) onAppearanceChange(mode)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) {
                                Text(mode.name, maxLines = 1)
                            }
                        }
                    }
                    ListItem(
                        headlineContent = { Text("Theme") },
                        supportingContent = {
                            Text("System uses device colors. Book follows cover art")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    val themes = ColorTheme.entries
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            ButtonGroupDefaults.ConnectedSpaceBetween,
                        ),
                    ) {
                        themes.forEachIndexed { index, theme ->
                            ToggleButton(
                                checked = colorTheme == theme,
                                onCheckedChange = { checked ->
                                    if (checked) onColorThemeChange(theme)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    themes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) {
                                Text(theme.name, maxLines = 1)
                            }
                        }
                    }
                    ListItem(
                        headlineContent = { Text("Contrast") },
                        supportingContent = {
                            Text("System follows the device colour contrast")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    val contrasts = ContrastPreference.entries
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            ButtonGroupDefaults.ConnectedSpaceBetween,
                        ),
                    ) {
                        contrasts.forEachIndexed { index, option ->
                            ToggleButton(
                                checked = contrastPreference == option,
                                onCheckedChange = { checked ->
                                    if (checked) onContrastPreferenceChange(option)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    contrasts.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) {
                                Text(option.name, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Placeholder covers") },
                    supportingContent = {
                        Text("Look for books without artwork")
                    },
                    trailingContent = { Icon(BookyIcons.chevronRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenPlaceholderCover),
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(bottom = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("Seek back") },
                        supportingContent = { Text("Playback controls jump backward") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    SeekPresetButtons(
                        selected = skipBackSeconds,
                        onSelect = onSkipBackSecondsChange,
                    )
                    ListItem(
                        headlineContent = { Text("Seek ahead") },
                        supportingContent = { Text("Playback controls jump forward") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    SeekPresetButtons(
                        selected = skipForwardSeconds,
                        onSelect = onSkipForwardSecondsChange,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(bottom = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("Smart resume") },
                        supportingContent = {
                            Text("If paused more than 30 seconds, rewind a little before playing")
                        },
                        trailingContent = {
                            Switch(
                                checked = smartResumeEnabled,
                                onCheckedChange = onSmartResumeEnabledChange,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    val rewindOptions = listOf(3, 5, 10)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            ButtonGroupDefaults.ConnectedSpaceBetween,
                        ),
                    ) {
                        rewindOptions.forEachIndexed { index, seconds ->
                            ToggleButton(
                                checked = smartResumeSeconds == seconds,
                                onCheckedChange = { checked ->
                                    if (checked) onSmartResumeSecondsChange(seconds)
                                },
                                enabled = smartResumeEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    rewindOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) {
                                Text("${seconds}s", maxLines = 1)
                            }
                        }
                    }
                }
            }

            Text(
                "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
    }
}

private val SeekPresets = listOf(5, 10, 15, 20, 30, 60)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SeekPresetButtons(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(
            ButtonGroupDefaults.ConnectedSpaceBetween,
        ),
    ) {
        SeekPresets.forEachIndexed { index, seconds ->
            ToggleButton(
                checked = selected == seconds,
                onCheckedChange = { checked ->
                    if (checked) onSelect(seconds)
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    SeekPresets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text("${seconds}s", maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
