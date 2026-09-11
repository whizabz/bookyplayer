package com.booky.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.booky.app.data.formatClock
import com.booky.app.data.formatMinutes
import com.booky.app.player.PlayerUiState
import com.booky.app.player.SleepTimer
import com.booky.app.ui.components.BookCover
import com.booky.app.ui.components.BookyIcons
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    player: PlayerUiState,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekChapter: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSleepTimer: (SleepTimer?) -> Unit,
    onRestartChapter: () -> Unit,
    onMarkChapterPlayed: () -> Unit,
    onMarkBookPlayed: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCloseBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val dismissToMiniPlayer: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxSize(),
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        shape = BottomSheetDefaults.ExpandedShape,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        NowPlayingContent(
            player = player,
            onTogglePlay = onTogglePlay,
            onSkip = onSkip,
            onSeek = onSeek,
            onSeekChapter = onSeekChapter,
            onSetSpeed = onSetSpeed,
            onSetSleepTimer = onSetSleepTimer,
            onRestartChapter = onRestartChapter,
            onMarkChapterPlayed = onMarkChapterPlayed,
            onMarkBookPlayed = onMarkBookPlayed,
            onToggleRepeat = onToggleRepeat,
            onCloseBook = onCloseBook,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingContent(
    player: PlayerUiState,
    onTogglePlay: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekChapter: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSleepTimer: (SleepTimer?) -> Unit,
    onRestartChapter: () -> Unit,
    onMarkChapterPlayed: () -> Unit,
    onMarkBookPlayed: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCloseBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = player.book ?: return
    val colors = MaterialTheme.colorScheme
    val speedLabel = formatSpeed(player.speed)
    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showRemaining by rememberSaveable { mutableStateOf(false) }
    val chapterDuration = player.chapterDurationMs.toFloat().coerceAtLeast(1f)
    val chapterPosition = player.chapterPositionMs.toFloat().coerceIn(0f, chapterDuration)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 2.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }
            BookCover(
                book = book,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "${book.title} by ${book.author}",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            val chapterCount = book.chapterTitles.size.coerceAtLeast(1)
            val chapterIndex = player.currentChapterIndex.coerceIn(0, chapterCount - 1)
            val chapterButtonSize = IconButtonDefaults.smallContainerSize(
                IconButtonDefaults.IconButtonWidthOption.Wide,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { onSeekChapter(chapterIndex - 1) },
                    enabled = chapterIndex > 0,
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.smallRoundShape,
                        pressedShape = IconButtonDefaults.smallPressedShape,
                    ),
                    modifier = Modifier.size(chapterButtonSize),
                ) {
                    Icon(BookyIcons.skipPrevious, contentDescription = "Previous chapter")
                }
                Text(
                    text = book.currentChapterTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 5_000,
                            initialDelayMillis = 0,
                        ),
                )
                FilledTonalIconButton(
                    onClick = { onSeekChapter(chapterIndex + 1) },
                    enabled = chapterIndex < chapterCount - 1,
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.smallRoundShape,
                        pressedShape = IconButtonDefaults.smallPressedShape,
                    ),
                    modifier = Modifier.size(chapterButtonSize),
                ) {
                    Icon(BookyIcons.skipNext, contentDescription = "Next chapter")
                }
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = chapterPosition,
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..chapterDuration,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.surfaceContainerHighest,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatClock(player.chapterPositionMs),
                    style = MaterialTheme.typography.labelMedium,
                )
                val remainingMediaMs =
                    (player.chapterDurationMs - player.chapterPositionMs).coerceAtLeast(0L)
                val remainingWallMs =
                    (remainingMediaMs / player.speed.coerceAtLeast(0.01f)).toLong()
                Text(
                    text = if (showRemaining) {
                        "-${formatClock(remainingWallMs)}"
                    } else {
                        formatClock(player.chapterDurationMs)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.clickable { showRemaining = !showRemaining },
                )
            }

            Spacer(
                Modifier
                    .weight(1f)
                    .heightIn(min = 20.dp),
            )
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                transportItem(
                    menuLabel = "Back 10 seconds",
                    onClick = { onSkip(-10_000) },
                    icon = {
                        Icon(
                            BookyIcons.skipBack,
                            contentDescription = "Back 10 seconds",
                            modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                        )
                    },
                )
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        FilledIconToggleButton(
                            checked = player.isPlaying,
                            onCheckedChange = { onTogglePlay() },
                            shapes = IconButtonDefaults.toggleableShapes(
                                shape = IconButtonDefaults.extraLargeSquareShape,
                                pressedShape = IconButtonDefaults.extraLargePressedShape,
                                checkedShape = IconButtonDefaults.extraLargeRoundShape,
                            ),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                                checkedContainerColor = colors.primary,
                                checkedContentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .animateWidth(interactionSource)
                                .size(IconButtonDefaults.extraLargeContainerSize()),
                            interactionSource = interactionSource,
                        ) {
                            Icon(
                                imageVector = if (player.isPlaying) BookyIcons.pause else BookyIcons.play,
                                contentDescription = if (player.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(IconButtonDefaults.extraLargeIconSize),
                            )
                        }
                    },
                    menuContent = { menuState ->
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = if (player.isPlaying) {
                                        BookyIcons.pause
                                    } else {
                                        BookyIcons.play
                                    },
                                    contentDescription = null,
                                )
                            },
                            text = { Text(if (player.isPlaying) "Pause" else "Play") },
                            onClick = {
                                onTogglePlay()
                                menuState.dismiss()
                            },
                        )
                    },
                )
                transportItem(
                    menuLabel = "Forward 10 seconds",
                    onClick = { onSkip(10_000) },
                    icon = {
                        Icon(
                            BookyIcons.skipForward,
                            contentDescription = "Forward 10 seconds",
                            modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                        )
                    },
                )
            }

            Spacer(
                Modifier
                    .weight(1f)
                    .heightIn(min = 20.dp),
            )
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(
                        menuState = menuState,
                        modifier = Modifier.size(
                            IconButtonDefaults.smallContainerSize(
                                IconButtonDefaults.IconButtonWidthOption.Wide,
                            ),
                        ),
                        shape = IconButtonDefaults.smallRoundShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                toolbarItem(
                    menuLabel = "Speed",
                    extraLabel = if (player.speed != 1f) speedLabel else null,
                    active = player.speed != 1f || showSpeed,
                    onClick = { showSpeed = true },
                    icon = { Icon(BookyIcons.speed, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Sleep timer",
                    extraLabel = player.sleepTimer?.chipLabel,
                    active = player.sleepTimer != null || showSleep,
                    onClick = { showSleep = true },
                    icon = { Icon(BookyIcons.timer, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Chapters",
                    active = showChapters,
                    onClick = { showChapters = true },
                    icon = { Icon(BookyIcons.chapters, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Cast",
                    onClick = {},
                    icon = { Icon(BookyIcons.cast, contentDescription = "Cast") },
                )
                toolbarItem(
                    menuLabel = "Restart chapter",
                    onClick = onRestartChapter,
                    icon = { Icon(BookyIcons.restartAlt, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Mark chapter as played",
                    onClick = onMarkChapterPlayed,
                    icon = { Icon(BookyIcons.check, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Mark book as played",
                    onClick = onMarkBookPlayed,
                    icon = { Icon(BookyIcons.doneAll, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Repeat",
                    active = player.repeatEnabled,
                    onClick = onToggleRepeat,
                    icon = { Icon(BookyIcons.autoSkipping, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Close book",
                    onClick = onCloseBook,
                    icon = { Icon(BookyIcons.book2, contentDescription = null) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (showSleep) {
            SleepSheet(
                timer = player.sleepTimer,
                remainingChapters = (book.chapterTitles.size - player.currentChapterIndex)
                    .coerceAtLeast(1),
                onSetTimer = onSetSleepTimer,
                onDismiss = { showSleep = false },
            )
        }
        if (showSpeed) {
            SpeedSheet(
                speed = player.speed,
                onSetSpeed = onSetSpeed,
                onDismiss = { showSpeed = false },
            )
        }
        if (showChapters) {
            ChapterListSheet(
                titles = book.chapterTitles.ifEmpty { listOf(book.currentChapterTitle) },
                durationsMs = book.chapterDurationsMs,
                currentIndex = player.currentChapterIndex,
                positionsMs = player.chapterPositionsMs,
                onSelect = onSeekChapter,
                onDismiss = { showChapters = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.transportItem(
    menuLabel: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            FilledTonalIconButton(
                onClick = onClick,
                shapes = IconButtonDefaults.shapes(
                    shape = IconButtonDefaults.largeRoundShape,
                    pressedShape = IconButtonDefaults.largePressedShape,
                ),
                modifier = Modifier
                    .animateWidth(interactionSource)
                    .size(IconButtonDefaults.largeContainerSize()),
                interactionSource = interactionSource,
                content = icon,
            )
        },
        menuContent = { menuState ->
            DropdownMenuItem(
                leadingIcon = icon,
                text = { Text(menuLabel) },
                onClick = {
                    onClick()
                    menuState.dismiss()
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.toolbarItem(
    menuLabel: String,
    onClick: () -> Unit,
    extraLabel: String? = null,
    active: Boolean = false,
    icon: @Composable () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            PlayerToolbarButton(
                active = active,
                extraLabel = extraLabel,
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = Modifier.animateWidth(interactionSource),
                icon = icon,
            )
        },
        menuContent = { menuState ->
            DropdownMenuItem(
                leadingIcon = icon,
                text = {
                    Text(
                        if (extraLabel != null) "$menuLabel · $extraLabel" else menuLabel,
                    )
                },
                onClick = {
                    onClick()
                    menuState.dismiss()
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerToolbarButton(
    active: Boolean,
    extraLabel: String?,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    if (extraLabel == null) {
        val container = IconButtonDefaults.smallContainerSize(
            IconButtonDefaults.IconButtonWidthOption.Wide,
        )
        val shapes = IconButtonDefaults.shapes(
            shape = if (active) {
                IconButtonDefaults.smallSquareShape
            } else {
                IconButtonDefaults.smallRoundShape
            },
            pressedShape = IconButtonDefaults.smallPressedShape,
        )
        val buttonModifier = modifier.size(container)
        if (active) {
            FilledIconButton(
                onClick = onClick,
                shapes = shapes,
                modifier = buttonModifier,
                interactionSource = interactionSource,
                content = icon,
            )
        } else {
            FilledTonalIconButton(
                onClick = onClick,
                shapes = shapes,
                modifier = buttonModifier,
                interactionSource = interactionSource,
                content = icon,
            )
        }
        return
    }

    val height = ButtonDefaults.ExtraSmallContainerHeight
    val shapes = ButtonDefaults.shapes(
        shape = if (active) ButtonDefaults.squareShape else ButtonDefaults.shapesFor(height).shape,
        pressedShape = ButtonDefaults.extraSmallPressedShape,
    )
    val buttonModifier = modifier.height(height)
    val padding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true)
    val content: @Composable RowScope.() -> Unit = {
        icon()
        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
        Text(extraLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
    if (active) {
        Button(
            onClick = onClick,
            shapes = shapes,
            modifier = buttonModifier,
            contentPadding = padding,
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            shapes = shapes,
            modifier = buttonModifier,
            contentPadding = padding,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

private const val SpeedMin = 0.1f
private const val SpeedMax = 3f
/** Discrete 0.1× steps between the endpoints (0.2× … 2.9×). */
private const val SpeedSteps = 28

private fun formatSpeed(speed: Float): String =
    "${speed.toString().trimEnd('0').trimEnd('.')}×"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(
    speed: Float,
    onSetSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Playback speed",
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(
                    onClick = { onSetSpeed(1f) },
                    enabled = speed != 1f,
                ) {
                    Text("Reset")
                }
            }
            Text(
                text = formatSpeed(speed),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Slider(
                value = speed.coerceIn(SpeedMin, SpeedMax),
                onValueChange = onSetSpeed,
                valueRange = SpeedMin..SpeedMax,
                steps = SpeedSteps,
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        // M3 discrete sliders should not paint a tick on every 0.1× step.
                        // Keep end stop indicators; mark only the 0.5× divisions.
                        drawTick = { offset, color ->
                            val width = size.width
                            if (width > 0f) {
                                val value = SpeedMin + (offset.x / width) * (SpeedMax - SpeedMin)
                                val tenths = (value * 10f).roundToInt()
                                if (tenths % 5 == 0 && tenths != (SpeedMin * 10f).toInt() &&
                                    tenths != (SpeedMax * 10f).toInt()
                                ) {
                                    drawCircle(
                                        color = color,
                                        radius = SliderDefaults.TickSize.toPx() / 2f,
                                        center = offset,
                                    )
                                }
                            }
                        },
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatSpeed(SpeedMin),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "1×",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatSpeed(SpeedMax),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val SleepPresetsMinutes = listOf(15, 30, 60)
private const val SleepCustomStepMinutes = 5
private const val SleepCustomMinMinutes = 5
private const val SleepCustomMaxMinutes = 180

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(
    timer: SleepTimer?,
    remainingChapters: Int,
    onSetTimer: (SleepTimer?) -> Unit,
    onDismiss: () -> Unit,
) {
    var customMinutes by remember {
        mutableIntStateOf(
            (timer as? SleepTimer.Minutes)
                ?.minutes
                ?.takeIf { it !in SleepPresetsMinutes }
                ?: 10,
        )
    }
    var chapterCount by remember {
        mutableIntStateOf((timer as? SleepTimer.EndOfChapters)?.chapters ?: 1)
    }
    val maxChapters = remainingChapters.coerceAtLeast(1)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = "Sleep timer",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SleepPresetsMinutes.forEachIndexed { index, minutes ->
            val option = SleepTimer.Minutes(minutes)
            SleepOptionRow(
                title = if (minutes == 60) "1 hour" else "$minutes minutes",
                selected = timer == option,
                onClick = {
                    if (timer == option) onSetTimer(null) else onSetTimer(option)
                },
                showDivider = index > 0,
            )
        }
        SleepOptionRow(
            title = "$customMinutes minutes",
            selected = timer is SleepTimer.Minutes && timer.minutes == customMinutes &&
                customMinutes !in SleepPresetsMinutes,
            onClick = {
                val option = SleepTimer.Minutes(customMinutes)
                if (timer == option) onSetTimer(null) else onSetTimer(option)
            },
            showDivider = true,
            trailing = {
                SleepStepper(
                    minusEnabled = customMinutes > SleepCustomMinMinutes,
                    plusEnabled = customMinutes < SleepCustomMaxMinutes,
                    onMinus = {
                        customMinutes = (customMinutes - SleepCustomStepMinutes)
                            .coerceAtLeast(SleepCustomMinMinutes)
                        onSetTimer(SleepTimer.Minutes(customMinutes))
                    },
                    onPlus = {
                        customMinutes = (customMinutes + SleepCustomStepMinutes)
                            .coerceAtMost(SleepCustomMaxMinutes)
                        onSetTimer(SleepTimer.Minutes(customMinutes))
                    },
                )
            },
        )
        SleepOptionRow(
            title = if (chapterCount == 1) "End of chapter" else "In $chapterCount chapters",
            selected = timer is SleepTimer.EndOfChapters,
            onClick = {
                val option = SleepTimer.EndOfChapters(chapterCount)
                if (timer == option) onSetTimer(null) else onSetTimer(option)
            },
            showDivider = true,
            trailing = {
                SleepStepper(
                    minusEnabled = chapterCount > 1,
                    plusEnabled = chapterCount < maxChapters,
                    onMinus = {
                        chapterCount = (chapterCount - 1).coerceAtLeast(1)
                        onSetTimer(SleepTimer.EndOfChapters(chapterCount))
                    },
                    onPlus = {
                        chapterCount = (chapterCount + 1).coerceAtMost(maxChapters)
                        onSetTimer(SleepTimer.EndOfChapters(chapterCount))
                    },
                )
            },
        )
        SleepOptionRow(
            title = "End of book",
            selected = timer is SleepTimer.EndOfBook,
            onClick = {
                if (timer is SleepTimer.EndOfBook) onSetTimer(null) else onSetTimer(SleepTimer.EndOfBook)
            },
            showDivider = true,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SleepOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colors.outlineVariant.copy(alpha = 0.6f),
            )
        }
        ListItem(
            headlineContent = { Text(title) },
            trailingContent = trailing,
            colors = ListItemDefaults.colors(
                containerColor = if (selected) colors.surfaceContainerHigh else Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun SleepStepper(
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row {
        FilledTonalIconButton(
            onClick = onMinus,
            enabled = minusEnabled,
            modifier = Modifier.size(40.dp),
        ) {
            Text("−")
        }
        FilledTonalIconButton(
            onClick = onPlus,
            enabled = plusEnabled,
            modifier = Modifier.size(40.dp),
        ) {
            Text("+")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListSheet(
    titles: List<String>,
    durationsMs: List<Long>,
    currentIndex: Int,
    positionsMs: Map<Int, Long>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(currentIndex) {
        if (titles.isNotEmpty()) {
            listState.scrollToItem(currentIndex.coerceIn(0, titles.lastIndex))
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState) {
            itemsIndexed(titles) { index, title ->
                val selected = index == currentIndex
                val duration = durationsMs.getOrNull(index) ?: 0L
                val position = positionsMs[index] ?: 0L
                val progress = if (duration > 0L) {
                    (position.toFloat() / duration).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val showProgress = progress > 0.02f && progress < 0.98f
                Column {
                    if (index > 0 && !selected && currentIndex != index - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 20.dp),
                            color = colors.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) colors.surfaceContainerHigh else Color.Transparent)
                            .clickable {
                                onSelect(index)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(28.dp),
                            color = if (selected) colors.primary else colors.onSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (showProgress) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    strokeCap = StrokeCap.Butt,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {},
                                )
                            }
                        }
                        if (duration > 0L) {
                            Text(
                                text = formatMinutes(duration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
