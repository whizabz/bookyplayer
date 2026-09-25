package xyz.saltedchips.bookyplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp as lerpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.lerp as lerpTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.lerp as lerpFloat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import xyz.saltedchips.bookyplayer.data.formatClock
import xyz.saltedchips.bookyplayer.data.formatMinutes
import xyz.saltedchips.bookyplayer.data.chapterTotal
import xyz.saltedchips.bookyplayer.player.ChapterMarksSnapshot
import xyz.saltedchips.bookyplayer.player.PlayerUiState
import xyz.saltedchips.bookyplayer.player.SleepTimer
import xyz.saltedchips.bookyplayer.ui.components.BookCover
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons
import xyz.saltedchips.bookyplayer.ui.components.SheetHeader
import xyz.saltedchips.bookyplayer.ui.components.SheetHeaderToContentPadding
import xyz.saltedchips.bookyplayer.ui.components.MarkPreviousChaptersDialog
import xyz.saltedchips.bookyplayer.ui.components.SwipeableChapterRow
import xyz.saltedchips.bookyplayer.ui.components.chapterListenProgress
import xyz.saltedchips.bookyplayer.ui.components.showBulkChapterSnackbar
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class PlayerAnchor { Chapters, Expanded, Mini, Hidden }

private val MiniPlayerHeight = 64.dp
private val MiniPlayerInset = 12.dp
private val MiniPlayerWidth = 348.dp
private val MiniPlaySize = 40.dp
private val MiniSkipSize = 40.dp
private val MiniTransportGap = 6.dp

private class ExpandedSlotLock {
    var play by mutableStateOf<Offset?>(null)
    var skip by mutableStateOf<Offset?>(null)
    var skipForward by mutableStateOf<Offset?>(null)
    var title by mutableStateOf<Offset?>(null)
    var titleSize by mutableStateOf<IntSize?>(null)
}
private val MiniCoverSize = 40.dp
private val MiniTitleWidth = 120.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    player: PlayerUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekChapter: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSleepTimer: (SleepTimer?) -> Unit,
    onRestartChapter: () -> Unit,
    onMarkChapterPlayed: (Boolean) -> Unit,
    onMarkBookPlayed: () -> Unit,
    onSetChaptersPlayed: (Collection<Int>, Boolean) -> Unit,
    onSnapshotMarks: () -> ChapterMarksSnapshot,
    onRestoreMarks: (ChapterMarksSnapshot) -> Unit,
    onToggleRepeat: () -> Unit,
    onCloseBook: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val spatialSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val miniContainer =
        if (colors.background.luminance() < 0.5f) {
            colors.surfaceContainerLowest
        } else {
            colors.surfaceContainerHighest
        }
    val scope = rememberCoroutineScope()
    val dismissX = remember { Animatable(0f) }
    val dragState = remember {
        AnchoredDraggableState(
            initialValue = if (expanded) PlayerAnchor.Expanded else PlayerAnchor.Hidden,
            DraggableAnchors {
                PlayerAnchor.Chapters at -10_000f
                PlayerAnchor.Expanded at 0f
                PlayerAnchor.Mini at 10_000f
                PlayerAnchor.Hidden at 20_000f
            },
        )
    }
    var hasEntered by remember { mutableStateOf(expanded) }
    LaunchedEffect(player.book?.id) {
        dismissX.snapTo(0f)
    }
    val settleTo: (PlayerAnchor) -> Unit = { target ->
        scope.launch { dragState.animateTo(target, spatialSpec) }
    }
    LaunchedEffect(expanded) {
        if (!hasEntered) return@LaunchedEffect
        val target = when {
            !expanded -> PlayerAnchor.Mini
            dragState.currentValue == PlayerAnchor.Chapters -> PlayerAnchor.Chapters
            else -> PlayerAnchor.Expanded
        }
        if (dragState.currentValue != target) {
            dragState.animateTo(target, spatialSpec)
        }
    }
    LaunchedEffect(dragState.settledValue) {
        when (dragState.settledValue) {
            PlayerAnchor.Hidden -> Unit
            PlayerAnchor.Chapters,
            PlayerAnchor.Expanded,
            -> if (!expanded) onExpandedChange(true)
            PlayerAnchor.Mini -> if (expanded) onExpandedChange(false)
        }
    }
    BackHandler(enabled = expanded) {
        if (dragState.currentValue == PlayerAnchor.Chapters) {
            settleTo(PlayerAnchor.Expanded)
        } else {
            settleTo(PlayerAnchor.Mini)
        }
    }

    val slotLock = remember(player.book?.id) { ExpandedSlotLock() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val navPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val screenOffsetPx = with(density) { FloatingToolbarDefaults.ScreenOffset.toPx() }
        val miniHeightPx = with(density) { MiniPlayerHeight.toPx() }
        val miniWidthPx = with(density) { MiniPlayerWidth.toPx() }
        val fullWidthPx = constraints.maxWidth.toFloat()
        val fullHeightPx = constraints.maxHeight.toFloat()
        val rangePx = (fullHeightPx - miniHeightPx - navPx - screenOffsetPx).coerceAtLeast(1f)
        val hiddenPx = rangePx + miniHeightPx + navPx + screenOffsetPx
        val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
        val chapterSheetPx = (fullHeightPx - statusPx - with(density) { 12.dp.toPx() })
            .coerceAtLeast(1f)
        LaunchedEffect(rangePx, hiddenPx, chapterSheetPx) {
            if (!hasEntered && !expanded) {
                dragState.updateAnchors(
                    DraggableAnchors {
                        PlayerAnchor.Chapters at -chapterSheetPx
                        PlayerAnchor.Expanded at 0f
                        PlayerAnchor.Mini at rangePx
                        PlayerAnchor.Hidden at hiddenPx
                    },
                )
                dragState.snapTo(PlayerAnchor.Hidden)
                dragState.animateTo(PlayerAnchor.Mini, spatialSpec)
                hasEntered = true
            } else if (!hasEntered) {
                dragState.snapTo(PlayerAnchor.Expanded)
                hasEntered = true
            }
            dragState.updateAnchors(
                DraggableAnchors {
                    PlayerAnchor.Chapters at -chapterSheetPx
                    PlayerAnchor.Expanded at 0f
                    PlayerAnchor.Mini at rangePx
                },
            )
        }
        val offsetPx = runCatching { dragState.requireOffset() }.getOrDefault(
            when {
                expanded -> 0f
                hasEntered -> rangePx
                else -> hiddenPx
            },
        )
        val rawProgress = offsetPx / rangePx
        val collapseProgress = rawProgress.coerceIn(0f, 1f)
        val chapterProgress = (-offsetPx / chapterSheetPx).coerceIn(0f, 1f)
        val miniSettled = collapseProgress > 0.92f &&
            dragState.settledValue == PlayerAnchor.Mini &&
            !dragState.isAnimationRunning
        val dismissThresholdPx = with(density) { 80.dp.toPx() }
        val dismissOffscreenPx = fullWidthPx + with(density) { 48.dp.toPx() }
        val dismissDrag = rememberDraggableState { delta ->
            scope.launch { dismissX.snapTo(dismissX.value + delta) }
        }
        val chromeFade = effectsOutgoing(rawProgress)
        val scrimFade = effectsScrim(rawProgress)
        val colorProgress = collapseProgress
        val surfaceWidth = lerp(
            with(density) { fullWidthPx.toDp() },
            MiniPlayerWidth,
            collapseProgress,
        )
        val surfaceHeight = lerp(
            with(density) { fullHeightPx.toDp() },
            MiniPlayerHeight,
            collapseProgress,
        )
        val edgePadding = lerp(0.dp, FloatingToolbarDefaults.ScreenOffset, collapseProgress)
            .coerceAtLeast(0.dp)
        val bottomPadding = lerp(
            0.dp,
            with(density) { navPx.toDp() } + FloatingToolbarDefaults.ScreenOffset,
            collapseProgress,
        ).coerceAtLeast(0.dp)
        val corner = lerp(0.dp, MiniPlayerHeight / 2f, collapseProgress).coerceAtLeast(0.dp)
        val miniShape = RoundedCornerShape(corner)
        val scrim = BottomSheetDefaults.ScrimColor
        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = dragState,
            animationSpec = spatialSpec,
        )
        val slotsReady = slotLock.play != null && slotLock.skip != null &&
            slotLock.skipForward != null && slotLock.title != null
        val probeWidth = fullWidthPx.roundToInt()
        val probeHeight = fullHeightPx.roundToInt()
        if (!slotsReady && probeWidth > 0 && probeHeight > 0) {
            Box(
                Modifier.layout { measurable, _ ->
                    val placeable = measurable.measure(
                        Constraints.fixed(probeWidth, probeHeight),
                    )
                    layout(0, 0) {
                        placeable.place(-probeWidth, 0)
                    }
                },
            ) {
                NowPlayingContent(
                    player = player,
                    collapseProgress = 0f,
                    chromeFade = 0f,
                    fullWidthPx = fullWidthPx,
                    fullHeightPx = fullHeightPx,
                    miniWidthPx = miniWidthPx,
                    miniHeightPx = miniHeightPx,
                    surfaceLeftPx = 0f,
                    surfaceTopPx = 0f,
                    miniSurfaceLeftPx = 0f,
                    miniSurfaceTopPx = 0f,
                    transportSettled = true,
                    slotLock = slotLock,
                    measurementOnly = true,
                    onExpand = {},
                    onTogglePlay = {},
                    onSkip = {},
                    onSeek = {},
                    onSeekChapter = {},
                    onSetSpeed = {},
                    onSetSleepTimer = {},
                    onRestartChapter = {},
                    onMarkChapterPlayed = {},
                    onMarkBookPlayed = {},
                    onSetChaptersPlayed = { _, _ -> },
                    onSnapshotMarks = onSnapshotMarks,
                    onRestoreMarks = {},
                    onToggleRepeat = {},
                    onCloseBook = {},
                    onOpenChapters = {},
                    chaptersOpen = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (scrimFade > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimFade }
                    .background(scrim)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        settleTo(PlayerAnchor.Mini)
                    },
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(
                    start = edgePadding,
                    end = edgePadding,
                    bottom = bottomPadding,
                )
                .offset {
                    IntOffset(
                        dismissX.value.roundToInt(),
                        (offsetPx - rangePx).coerceAtLeast(0f).roundToInt(),
                    )
                }
                .width(surfaceWidth)
                .height(surfaceHeight)
                .dropShadow(miniShape) {
                    radius = 28.dp.toPx()
                    color = Color.Black
                    alpha = 0.16f * collapseProgress
                    offset = Offset(0f, 6.dp.toPx())
                }
                .clip(miniShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(hazeState) {
                            blurRadius = (36f * collapseProgress).dp
                            tints = listOf(
                                HazeTint(miniContainer.copy(alpha = 0.26f * collapseProgress)),
                            )
                            noiseFactor = 0.03f * collapseProgress
                        }
                    } else {
                        Modifier
                    },
                )
                .draggable(
                    state = dismissDrag,
                    orientation = Orientation.Horizontal,
                    enabled = miniSettled,
                    onDragStopped = { velocity ->
                        scope.launch {
                            val x = dismissX.value
                            val shouldClose = abs(x) > dismissThresholdPx || abs(velocity) > 1400f
                            if (shouldClose) {
                                val dir = when {
                                    abs(velocity) > 400f -> if (velocity >= 0f) 1f else -1f
                                    else -> if (x >= 0f) 1f else -1f
                                }
                                dismissX.animateTo(dir * dismissOffscreenPx, spatialSpec)
                                onCloseBook()
                            } else {
                                dismissX.animateTo(0f, spatialSpec)
                            }
                        }
                    },
                )
                .anchoredDraggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    enabled = chapterProgress < 0.98f,
                    flingBehavior = flingBehavior,
                ),
            shape = miniShape,
            color = lerpColor(
                colors.surface,
                miniContainer.copy(alpha = 0.18f),
                colorProgress,
            ),
            contentColor = colors.onSurface,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            border = BorderStroke(
                width = 0.5.dp,
                color = colors.secondaryContainer.copy(alpha = 0.45f * collapseProgress),
            ),
        ) {
            NowPlayingContent(
                player = player,
                collapseProgress = collapseProgress,
                chromeFade = chromeFade,
                fullWidthPx = fullWidthPx,
                fullHeightPx = fullHeightPx,
                miniWidthPx = miniWidthPx,
                miniHeightPx = miniHeightPx,
                surfaceLeftPx = (fullWidthPx - with(density) { (edgePadding * 2).toPx() } -
                    with(density) { surfaceWidth.toPx() }) / 2f +
                    with(density) { edgePadding.toPx() },
                surfaceTopPx = fullHeightPx - with(density) { bottomPadding.toPx() } -
                    with(density) { surfaceHeight.toPx() },
                miniSurfaceLeftPx = (fullWidthPx - screenOffsetPx * 2f - miniWidthPx) / 2f +
                    screenOffsetPx,
                miniSurfaceTopPx = fullHeightPx - navPx - screenOffsetPx - miniHeightPx,
                transportSettled = !dragState.isAnimationRunning &&
                    (dragState.settledValue == PlayerAnchor.Expanded ||
                        dragState.settledValue == PlayerAnchor.Chapters) &&
                    collapseProgress < 0.02f,
                slotLock = slotLock,
                onExpand = { settleTo(PlayerAnchor.Expanded) },
                onTogglePlay = onTogglePlay,
                onSkip = onSkip,
                onSeek = onSeek,
                onSeekChapter = onSeekChapter,
                onSetSpeed = onSetSpeed,
                onSetSleepTimer = onSetSleepTimer,
                onRestartChapter = onRestartChapter,
                onMarkChapterPlayed = onMarkChapterPlayed,
                onMarkBookPlayed = onMarkBookPlayed,
                onSetChaptersPlayed = onSetChaptersPlayed,
                onSnapshotMarks = onSnapshotMarks,
                onRestoreMarks = onRestoreMarks,
                onToggleRepeat = onToggleRepeat,
                onCloseBook = onCloseBook,
                onOpenChapters = { settleTo(PlayerAnchor.Chapters) },
                chaptersOpen = chapterProgress > 0.5f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (chapterProgress > 0.01f && collapseProgress < 0.2f) {
            val book = player.book
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = chapterProgress }
                        .background(scrim)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            settleTo(PlayerAnchor.Expanded)
                        },
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(with(density) { chapterSheetPx.toDp() })
                        .offset {
                            IntOffset(
                                0,
                                ((1f - chapterProgress) * chapterSheetPx).roundToInt(),
                            )
                        }
                        .anchoredDraggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            enabled = chapterProgress > 0.02f,
                            flingBehavior = flingBehavior,
                        ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = colors.surface,
                    contentColor = colors.onSurface,
                    shadowElevation = 0.dp,
                    tonalElevation = 2.dp,
                ) {
                    if (book != null) {
                        ChapterListPane(
                            titles = book.chapterTitles.ifEmpty { listOf(book.currentChapterTitle) },
                            durationsMs = book.chapterDurationsMs,
                            bookPositionMs = player.bookPositionMs,
                            currentIndex = player.currentChapterIndex,
                            isPlaying = player.isPlaying,
                            positionsMs = player.chapterPositionsMs,
                            completedChapters = player.completedChapters,
                            onSelect = { index ->
                                onSeekChapter(index)
                                settleTo(PlayerAnchor.Expanded)
                            },
                            onSetChaptersPlayed = onSetChaptersPlayed,
                            onSnapshotMarks = onSnapshotMarks,
                            onRestoreMarks = onRestoreMarks,
                            onDismiss = { settleTo(PlayerAnchor.Expanded) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingContent(
    player: PlayerUiState,
    collapseProgress: Float,
    chromeFade: Float,
    fullWidthPx: Float,
    fullHeightPx: Float,
    miniWidthPx: Float,
    miniHeightPx: Float,
    surfaceLeftPx: Float,
    surfaceTopPx: Float,
    miniSurfaceLeftPx: Float,
    miniSurfaceTopPx: Float,
    transportSettled: Boolean,
    slotLock: ExpandedSlotLock,
    measurementOnly: Boolean = false,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekChapter: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSleepTimer: (SleepTimer?) -> Unit,
    onRestartChapter: () -> Unit,
    onMarkChapterPlayed: (Boolean) -> Unit,
    onMarkBookPlayed: () -> Unit,
    onSetChaptersPlayed: (Collection<Int>, Boolean) -> Unit,
    onSnapshotMarks: () -> ChapterMarksSnapshot,
    onRestoreMarks: (ChapterMarksSnapshot) -> Unit,
    onToggleRepeat: () -> Unit,
    onCloseBook: () -> Unit,
    onOpenChapters: () -> Unit,
    chaptersOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val book = player.book ?: return
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val speedLabel = formatSpeed(player.speed)
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var pendingToolbarMark by remember { mutableStateOf(false) }
    var pendingMarkChapterConfirm by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showRemaining by rememberSaveable { mutableStateOf(false) }
    var parentCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var playSlot by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var skipSlot by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var skipForwardSlot by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var titleSlot by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val chapterDuration = player.chapterDurationMs.toFloat().coerceAtLeast(1f)
    val chapterPosition = player.chapterPositionMs.toFloat().coerceIn(0f, chapterDuration)
    val fadeOut = chromeFade
    val playExpanded = IconButtonDefaults.extraLargeContainerSize()
    val skipExpanded = IconButtonDefaults.largeContainerSize()
    val playSize = lerpSize(playExpanded, DpSize(MiniPlaySize, MiniPlaySize), collapseProgress)
    val skipSize = lerpSize(skipExpanded, DpSize(MiniSkipSize, MiniSkipSize), collapseProgress)
    val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
    val padExpPx = with(density) { 20.dp.toPx() }
    val playW = with(density) { playExpanded.width.toPx() }
    val playH = with(density) { playExpanded.height.toPx() }
    val skipW = with(density) { skipExpanded.width.toPx() }
    val skipH = with(density) { skipExpanded.height.toPx() }
    val playCollapsedW = with(density) { MiniPlaySize.toPx() }
    val playCollapsedH = with(density) { MiniPlaySize.toPx() }
    val skipCollapsedW = with(density) { MiniSkipSize.toPx() }
    val skipCollapsedH = with(density) { MiniSkipSize.toPx() }
    val coverCollapsedPx = with(density) { MiniCoverSize.toPx() }
    val coverExpandedSize = (fullWidthPx - padExpPx * 2f).coerceAtLeast(coverCollapsedPx)
    val coverExpandedOffset = Offset(
        padExpPx,
        statusPx + with(density) { 48.dp.toPx() },
    )
    val measuredPlay = slotOffset(parentCoords, playSlot)
    val measuredSkip = slotOffset(parentCoords, skipSlot)
    val measuredSkipForward = slotOffset(parentCoords, skipForwardSlot)
    val measuredTitle = slotOffset(parentCoords, titleSlot)
    val measuredTitleSize = titleSlot?.takeIf { it.isAttached }?.size
    SideEffect {
        if (measurementOnly) {
            measuredPlay?.let { slotLock.play = it }
            measuredSkip?.let { slotLock.skip = it }
            measuredSkipForward?.let { slotLock.skipForward = it }
            measuredTitle?.let { slotLock.title = it }
            measuredTitleSize?.let { slotLock.titleSize = it }
        }
    }
    val titleExpandedOffset = slotLock.title
        ?: Offset(
            padExpPx,
            coverExpandedOffset.y + coverExpandedSize + with(density) { 16.dp.toPx() },
        )
    val playExpandedOffset = slotLock.play
        ?: Offset(
            (fullWidthPx - playW) / 2f,
            fullHeightPx * 0.72f,
        )
    val skipExpandedOffset = slotLock.skip
        ?: Offset(
            playExpandedOffset.x - with(density) { 2.dp.toPx() } - skipW,
            playExpandedOffset.y + (playH - skipH) / 2f,
        )
    val skipForwardExpandedOffset = slotLock.skipForward
        ?: Offset(
            playExpandedOffset.x + playW + with(density) { 2.dp.toPx() },
            playExpandedOffset.y + (playH - skipH) / 2f,
        )
    val coverCollapsedOffset = Offset(
        with(density) { MiniPlayerInset.toPx() },
        (miniHeightPx - coverCollapsedPx) / 2f,
    )
    val skipForwardCollapsedLocal = Offset(
        miniWidthPx - with(density) { MiniPlayerInset.toPx() } - skipCollapsedW,
        (miniHeightPx - skipCollapsedH) / 2f,
    )
    val playCollapsedLocal = Offset(
        skipForwardCollapsedLocal.x - with(density) { MiniTransportGap.toPx() } - playCollapsedW,
        (miniHeightPx - playCollapsedH) / 2f,
    )
    val skipCollapsedLocal = Offset(
        playCollapsedLocal.x - with(density) { MiniTransportGap.toPx() } - skipCollapsedW,
        (miniHeightPx - skipCollapsedH) / 2f,
    )
    val playOffset = lerpOffset(playExpandedOffset, playCollapsedLocal, collapseProgress)
    val skipOffset = lerpOffset(skipExpandedOffset, skipCollapsedLocal, collapseProgress)
    val skipForwardOffset = lerpOffset(
        skipForwardExpandedOffset,
        skipForwardCollapsedLocal,
        collapseProgress,
    )
    val titleCollapsedOffset = Offset(
        coverCollapsedOffset.x + coverCollapsedPx + with(density) { 12.dp.toPx() },
        with(density) { MiniPlayerInset.toPx() },
    )
    val coverSize = lerp(
        with(density) { coverExpandedSize.toDp() },
        MiniCoverSize,
        collapseProgress,
    )
    val coverOffset = lerpOffset(coverExpandedOffset, coverCollapsedOffset, collapseProgress)
    val titleOffset = lerpOffset(titleExpandedOffset, titleCollapsedOffset, collapseProgress)
    val expandedTitleWidth = slotLock.titleSize?.let { with(density) { it.width.toDp() } }
        ?: with(density) { (fullWidthPx - padExpPx * 2f).toDp() }
    val coverCorner = lerp(16.dp, MiniCoverSize / 2f, collapseProgress)

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { parentCoords = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .graphicsLayer { alpha = 1f - fadeOut },
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Spacer(Modifier.height(16.dp))
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .onGloballyPositioned { titleSlot = it },
                ) {
                    MorphingNowPlayingTitle(
                        title = book.title,
                        subtitle = book.currentChapterTitle,
                        collapseProgress = 0f,
                        expandedWidth = expandedTitleWidth,
                        typography = typography,
                        colors = colors,
                        modifier = Modifier.graphicsLayer { alpha = 0f },
                    )
                }
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
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 5_000,
                        initialDelayMillis = 0,
                    ),
            )

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                ChapterSlider(
                    value = chapterPosition,
                    valueRange = 0f..chapterDuration,
                    onValueChange = { onSeek(it.toLong()) },
                    enabled = !measurementOnly,
                    playing = player.isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                )
            }
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
                    .weight(0.65f)
                    .heightIn(min = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ButtonGroup(
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    transportItem(
                        menuLabel = "Back ${player.skipBackSeconds} seconds",
                        onClick = { onSkip(-(player.skipBackSeconds * 1_000L)) },
                        enabled = false,
                        animatePress = false,
                        modifier = Modifier
                            .onGloballyPositioned { skipSlot = it }
                            .graphicsLayer { alpha = 0f },
                        icon = {
                            Icon(
                                BookyIcons.skipBack,
                                contentDescription = "Back ${player.skipBackSeconds} seconds",
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
                                enabled = false,
                                shapes = IconButtonDefaults.toggleableShapes(
                                    shape = IconButtonDefaults.extraLargeRoundShape,
                                    pressedShape = IconButtonDefaults.extraLargeRoundShape,
                                    checkedShape = IconButtonDefaults.extraLargeRoundShape,
                                ),
                                colors = IconButtonDefaults.filledIconToggleButtonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary,
                                    checkedContainerColor = colors.primary,
                                    checkedContentColor = colors.onPrimary,
                                ),
                                modifier = Modifier
                                    .size(IconButtonDefaults.extraLargeContainerSize())
                                    .onGloballyPositioned { playSlot = it }
                                    .graphicsLayer { alpha = 0f },
                                interactionSource = interactionSource,
                            ) {
                                Icon(
                                    imageVector = if (player.isPlaying) {
                                        BookyIcons.pause
                                    } else {
                                        BookyIcons.play
                                    },
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
                        menuLabel = "Forward ${player.skipForwardSeconds} seconds",
                        onClick = { onSkip(player.skipForwardSeconds * 1_000L) },
                        enabled = false,
                        animatePress = false,
                        modifier = Modifier
                            .onGloballyPositioned { skipForwardSlot = it }
                            .graphicsLayer { alpha = 0f },
                        icon = {
                            Icon(
                                BookyIcons.skipForward,
                                contentDescription = "Forward ${player.skipForwardSeconds} seconds",
                                modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                            )
                        },
                    )
                }
            }

            Spacer(
                Modifier
                    .weight(1.35f)
                    .heightIn(min = 24.dp),
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
                    active = chaptersOpen,
                    onClick = onOpenChapters,
                    icon = { Icon(BookyIcons.chapters, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Repeat",
                    active = player.repeatEnabled,
                    onClick = onToggleRepeat,
                    icon = { Icon(BookyIcons.repeat, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Mark chapter as played",
                    onClick = { pendingMarkChapterConfirm = true },
                    icon = { Icon(BookyIcons.check, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Restart chapter",
                    overflowOnly = true,
                    onClick = onRestartChapter,
                    icon = { Icon(BookyIcons.restartAlt, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Mark book as played",
                    onClick = onMarkBookPlayed,
                    icon = { Icon(BookyIcons.doneAll, contentDescription = null) },
                )
                toolbarItem(
                    menuLabel = "Close book",
                    onClick = onCloseBook,
                    icon = { Icon(BookyIcons.book2, contentDescription = null) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        val miniFillT = ((collapseProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
        if (miniFillT > 0f) {
            val fillFraction = (chapterPosition / chapterDuration).coerceIn(0f, 1f)
            val fillColor = colors.primary.copy(alpha = 0.28f * miniFillT)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clickable(onClick = onExpand),
            ) {
                drawRect(
                    color = fillColor,
                    size = Size(size.width * fillFraction + 2f, size.height),
                )
            }
        }
        BookCover(
            book = book,
            modifier = Modifier
                .offset { coverOffset.round() }
                .size(coverSize)
                .then(
                    if (collapseProgress > 0.55f) {
                        Modifier.clickable(onClick = onExpand)
                    } else {
                        Modifier
                    },
                ),
            square = false,
            shape = RoundedCornerShape(coverCorner),
        )
        MorphingNowPlayingTitle(
            title = book.title,
            subtitle = book.currentChapterTitle,
            collapseProgress = collapseProgress,
            expandedWidth = expandedTitleWidth,
            typography = typography,
            colors = colors,
            modifier = Modifier
                .offset { titleOffset.round() }
                .then(
                    if (collapseProgress > 0.55f) {
                        Modifier.clickable(onClick = onExpand)
                    } else {
                        Modifier
                    },
                ),
        )
        if (!measurementOnly) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                FilledTonalIconButton(
                    onClick = { onSkip(-(player.skipBackSeconds * 1_000L)) },
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.largeRoundShape,
                        pressedShape = IconButtonDefaults.largePressedShape,
                    ),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    modifier = Modifier
                        .offset { skipOffset.round() }
                        .requiredSize(skipSize),
                ) {
                    Icon(
                        BookyIcons.skipBack,
                        contentDescription = "Back ${player.skipBackSeconds} seconds",
                        modifier = Modifier.size(
                            lerp(
                                IconButtonDefaults.largeIconSize,
                                24.dp,
                                collapseProgress,
                            ),
                        ),
                    )
                }
                FilledIconToggleButton(
                    checked = player.isPlaying,
                    onCheckedChange = { onTogglePlay() },
                    shapes = IconButtonDefaults.toggleableShapes(
                        shape = IconButtonDefaults.extraLargeRoundShape,
                        pressedShape = IconButtonDefaults.extraLargeRoundShape,
                        checkedShape = IconButtonDefaults.extraLargeRoundShape,
                    ),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                        checkedContainerColor = colors.primary,
                        checkedContentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .offset { playOffset.round() }
                        .requiredSize(playSize),
                ) {
                    Icon(
                        imageVector = if (player.isPlaying) BookyIcons.pause else BookyIcons.play,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(
                            lerp(
                                IconButtonDefaults.extraLargeIconSize,
                                24.dp,
                                collapseProgress,
                            ),
                        ),
                    )
                }
                FilledTonalIconButton(
                    onClick = { onSkip(player.skipForwardSeconds * 1_000L) },
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.largeRoundShape,
                        pressedShape = IconButtonDefaults.largePressedShape,
                    ),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    modifier = Modifier
                        .offset { skipForwardOffset.round() }
                        .requiredSize(skipSize),
                ) {
                    Icon(
                        BookyIcons.skipForward,
                        contentDescription = "Forward ${player.skipForwardSeconds} seconds",
                        modifier = Modifier.size(
                            lerp(
                                IconButtonDefaults.largeIconSize,
                                24.dp,
                                collapseProgress,
                            ),
                        ),
                    )
                }
            }
        }

        if (!measurementOnly) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )
        if (pendingMarkChapterConfirm) {
            AlertDialog(
                onDismissRequest = { pendingMarkChapterConfirm = false },
                title = { Text("Mark chapter as played?") },
                text = {
                    Text(
                        "Mark \"${book.currentChapterTitle}\" as played and skip to the next chapter.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingMarkChapterConfirm = false
                            val index = player.currentChapterIndex
                            val previousIncomplete =
                                (0 until index).any { it !in player.completedChapters }
                            if (index > 0 && previousIncomplete) {
                                pendingToolbarMark = true
                            } else {
                                onMarkChapterPlayed(false)
                            }
                        },
                    ) { Text("Mark as played") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingMarkChapterConfirm = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
        if (pendingToolbarMark) {
            val index = player.currentChapterIndex
            val previousCount = (0 until index).count { it !in player.completedChapters }
            MarkPreviousChaptersDialog(
                previousCount = previousCount,
                onMarkPrevious = {
                    pendingToolbarMark = false
                    val snapshot = onSnapshotMarks()
                    onMarkChapterPlayed(true)
                    snackbarScope.showBulkChapterSnackbar(
                        host = snackbarHostState,
                        count = previousCount + 1,
                        played = true,
                        snapshot = snapshot,
                        restore = onRestoreMarks,
                    )
                },
                onOnlyThese = {
                    pendingToolbarMark = false
                    onMarkChapterPlayed(false)
                },
                onDismiss = { pendingToolbarMark = false },
            )
        }
        if (showSleep) {
            SleepSheet(
                timer = player.sleepTimer,
                remainingChapters = (book.chapterTotal() - player.currentChapterIndex)
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
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.transportItem(
    menuLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animatePress: Boolean = true,
    icon: @Composable () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            FilledTonalIconButton(
                onClick = onClick,
                enabled = enabled,
                shapes = IconButtonDefaults.shapes(
                    shape = IconButtonDefaults.largeRoundShape,
                    pressedShape = IconButtonDefaults.largePressedShape,
                ),
                modifier = Modifier
                    .size(IconButtonDefaults.largeContainerSize())
                    .then(
                        if (animatePress) {
                            Modifier.animateWidth(interactionSource)
                        } else {
                            Modifier
                        },
                    )
                    .then(modifier),
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
    overflowOnly: Boolean = false,
    icon: @Composable () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            if (overflowOnly) {
                Spacer(Modifier.width(10_000.dp))
                return@customItem
            }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChapterSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SliderDefaults.colors()
    val interactionSource = remember { MutableInteractionSource() }
    val thumbGap = 6.dp
    val thumbSize = DpSize(4.dp, 52.dp)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.graphicsLayer { clip = false },
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    enabled = true,
                    thumbSize = thumbSize,
                )
            },
            track = { sliderState ->
                val fraction = sliderState.coercedValueAsFraction.coerceIn(0f, 1f)
                val density = LocalDensity.current
                val thumbClearance = thumbGap + thumbSize.width / 2
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val gapPx = with(density) { thumbClearance.toPx() }
                    val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val inset = (gapPx / width).coerceIn(0f, 0.5f)
                    LinearWavyProgressIndicator(
                        progress = { (fraction - inset).coerceAtLeast(0f) },
                        modifier = Modifier.fillMaxSize(),
                        gapSize = thumbClearance * 2,
                        stopSize = 4.dp,
                        amplitude = { progress ->
                            when {
                                !playing -> 0f
                                progress <= 0.1f || progress >= 0.95f -> 0f
                                else -> 0.4f
                            }
                        },
                        waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 3f,
                    )
                }
            },
            valueRange = valueRange,
        )
    }
}

private fun effectsOutgoing(raw: Float): Float {
    val t = raw.coerceIn(0f, 1f)
    return ((t - 0.06f) / 0.34f).coerceIn(0f, 1f)
}

private fun effectsScrim(raw: Float): Float {
    val t = raw.coerceIn(0f, 1f)
    return 1f - (t / 0.8f).coerceIn(0f, 1f)
}

@Composable
private fun MorphingNowPlayingTitle(
    title: String,
    subtitle: String,
    collapseProgress: Float,
    expandedWidth: Dp,
    typography: Typography,
    colors: ColorScheme,
    modifier: Modifier = Modifier,
) {
    val width = lerp(expandedWidth, MiniTitleWidth, collapseProgress)
    val morphTitleStyle = lerpSizeOnly(typography.titleLarge, typography.bodyLarge, collapseProgress)
    val morphSubtitleStyle = lerpSizeOnly(typography.titleMedium, typography.bodySmall, collapseProgress)
        .copy(color = lerpColor(colors.onSurface, colors.onSurfaceVariant, collapseProgress))
    val horizontalBias = lerpFloat(0f, -1f, collapseProgress)
    val titleMaxHeight = lerp(72.dp, 22.dp, collapseProgress)
    Box(modifier.width(width)) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = titleMaxHeight)
                    .clipToBounds(),
            ) {
                Text(
                    text = title,
                    style = morphTitleStyle,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = width)
                        .align(BiasAlignment(horizontalBias, -1f)),
                )
            }
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = subtitle,
                    style = morphSubtitleStyle,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = width)
                        .align(BiasAlignment(horizontalBias, -1f)),
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun TextStyle.withoutFontPadding() = copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun lerpSizeOnly(expanded: TextStyle, collapsed: TextStyle, progress: Float): TextStyle {
    return lerpTextStyle(expanded, collapsed, progress).copy(
        fontFamily = expanded.fontFamily,
        fontWeight = expanded.fontWeight,
        fontStyle = expanded.fontStyle,
        fontSynthesis = expanded.fontSynthesis,
        letterSpacing = expanded.letterSpacing,
        fontFeatureSettings = expanded.fontFeatureSettings,
    ).withoutFontPadding()
}

private fun slotOffset(parent: LayoutCoordinates?, slot: LayoutCoordinates?): Offset? {
    if (parent == null || slot == null || !parent.isAttached || !slot.isAttached) return null
    return parent.localPositionOf(slot, Offset.Zero)
}

private fun lerpSize(start: DpSize, stop: DpSize, fraction: Float): DpSize =
    DpSize(lerp(start.width, stop.width, fraction), lerp(start.height, stop.height, fraction))

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
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = "Playback speed",
                action = {
                    TextButton(
                        onClick = { onSetSpeed(1f) },
                        enabled = speed != 1f,
                    ) {
                        Text("Reset")
                    }
                },
            )
            Text(
                text = formatSpeed(speed),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = SheetHeaderToContentPadding,
                    bottom = 20.dp,
                ),
            )
            Slider(
                value = speed.coerceIn(SpeedMin, SpeedMax),
                onValueChange = onSetSpeed,
                valueRange = SpeedMin..SpeedMax,
                steps = SpeedSteps,
                modifier = Modifier.padding(horizontal = 24.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
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

private fun sleepChapterOptionLabel(count: Int, remaining: Int): String = when {
    remaining <= 1 || count >= remaining -> "End of book"
    count <= 1 -> "End of this chapter"
    else -> "In $count chapters"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val maxChapters = remainingChapters.coerceAtLeast(1)
    var chapterCount by remember {
        mutableIntStateOf(
            (timer as? SleepTimer.EndOfChapters)?.chapters?.coerceIn(1, maxChapters) ?: 1,
        )
    }
    if (chapterCount > maxChapters) chapterCount = maxChapters
    val itemCount = SleepPresetsMinutes.size + 2
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            SheetHeader(
                title = "Sleep timer",
                action = {
                    TextButton(
                        onClick = { onSetTimer(null) },
                        enabled = timer != null,
                    ) {
                        Text("Cancel timer")
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = SheetHeaderToContentPadding,
                        bottom = 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SleepPresetsMinutes.forEachIndexed { index, minutes ->
                    val option = SleepTimer.Minutes(minutes)
                    SleepOptionRow(
                        title = if (minutes == 60) "1 hour" else "$minutes minutes",
                        selected = timer == option,
                        onClick = {
                            if (timer == option) onSetTimer(null) else onSetTimer(option)
                        },
                        itemShapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = itemCount,
                        ),
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
                    itemShapes = ListItemDefaults.segmentedShapes(
                        index = SleepPresetsMinutes.size,
                        count = itemCount,
                    ),
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
                    title = sleepChapterOptionLabel(chapterCount, maxChapters),
                    selected = timer is SleepTimer.EndOfChapters,
                    onClick = {
                        val option = SleepTimer.EndOfChapters(chapterCount)
                        if (timer == option) onSetTimer(null) else onSetTimer(option)
                    },
                    itemShapes = ListItemDefaults.segmentedShapes(
                        index = SleepPresetsMinutes.size + 1,
                        count = itemCount,
                    ),
                    trailing = if (maxChapters > 1) {
                        {
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
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SleepOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    itemShapes: ListItemShapes,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "sleep-row-container",
    )
    Surface(
        shape = itemShapes.shape,
        color = container,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShapes.shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SleepStepper(
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        ButtonGroup(
            overflowIndicator = {},
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(40.dp),
        ) {
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                FilledTonalIconButton(
                    onClick = onMinus,
                    enabled = minusEnabled,
                    shapes = IconButtonDefaults.shapes(
                        shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                        pressedShape = ButtonGroupDefaults.connectedLeadingButtonPressShape,
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .animateWidth(interactionSource),
                    interactionSource = interactionSource,
                ) {
                    Text("−")
                }
            },
            menuContent = { menuState ->
                DropdownMenuItem(
                    text = { Text("Decrease") },
                    enabled = minusEnabled,
                    onClick = {
                        onMinus()
                        menuState.dismiss()
                    },
                )
            },
        )
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                FilledTonalIconButton(
                    onClick = onPlus,
                    enabled = plusEnabled,
                    shapes = IconButtonDefaults.shapes(
                        shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                        pressedShape = ButtonGroupDefaults.connectedTrailingButtonPressShape,
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .animateWidth(interactionSource),
                    interactionSource = interactionSource,
                ) {
                    Text("+")
                }
            },
            menuContent = { menuState ->
                DropdownMenuItem(
                    text = { Text("Increase") },
                    enabled = plusEnabled,
                    onClick = {
                        onPlus()
                        menuState.dismiss()
                    },
                )
            },
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChapterListPane(
    titles: List<String>,
    durationsMs: List<Long>,
    bookPositionMs: Long,
    currentIndex: Int,
    isPlaying: Boolean,
    positionsMs: Map<Int, Long>,
    completedChapters: Set<Int>,
    onSelect: (Int) -> Unit,
    onSetChaptersPlayed: (Collection<Int>, Boolean) -> Unit,
    onSnapshotMarks: () -> ChapterMarksSnapshot,
    onRestoreMarks: (ChapterMarksSnapshot) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var revealedIndex by remember { mutableStateOf<Int?>(null) }
    var pendingPlayed by remember { mutableStateOf<Set<Int>?>(null) }
    LaunchedEffect(currentIndex) {
        if (titles.isNotEmpty()) {
            listState.scrollToItem(currentIndex.coerceIn(0, titles.lastIndex))
        }
    }

    fun applyPlayed(indices: Set<Int>, bulk: Boolean) {
        val snapshot = onSnapshotMarks()
        onSetChaptersPlayed(indices, true)
        if (bulk) {
            scope.showBulkChapterSnackbar(
                host = snackbarHostState,
                count = indices.size,
                played = true,
                snapshot = snapshot,
                restore = onRestoreMarks,
            )
        }
    }

    fun applyUnplayed(index: Int) {
        onSetChaptersPlayed(setOf(index), false)
    }

    fun requestMarkPlayed(index: Int) {
        val previousIncomplete = (0 until index).any { it !in completedChapters }
        if (index > 0 && previousIncomplete) {
            pendingPlayed = setOf(index)
        } else {
            applyPlayed(setOf(index), bulk = false)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }
            SheetHeader(title = "Chapters")
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = SheetHeaderToContentPadding,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(titles) { index, title ->
                    val selected = index == currentIndex
                    val duration = durationsMs.getOrNull(index) ?: 0L
                    val complete = index in completedChapters
                    val progress = if (complete) {
                        1f
                    } else {
                        chapterListenProgress(
                            index = index,
                            durationMs = duration,
                            bookPositionMs = bookPositionMs,
                            chapterDurationsMs = durationsMs,
                            savedPositionsMs = positionsMs,
                        )
                    }
                    SwipeableChapterRow(
                        index = index,
                        title = title,
                        durationMs = duration,
                        progress = progress,
                        complete = complete,
                        selected = selected,
                        itemShapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = titles.size,
                        ),
                        revealed = revealedIndex == index,
                        swipeEnabled = true,
                        playing = selected && isPlaying,
                        onClick = { onSelect(index) },
                        onRevealedChange = { open ->
                            revealedIndex = if (open) {
                                index
                            } else if (revealedIndex == index) {
                                null
                            } else {
                                revealedIndex
                            }
                        },
                        onMarkPlayed = { requestMarkPlayed(index) },
                        onMarkUnplayed = { applyUnplayed(index) },
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
        pendingPlayed?.let { indices ->
            val lowest = indices.minOrNull() ?: 0
            val previousCount = (0 until lowest).count { it !in completedChapters }
            MarkPreviousChaptersDialog(
                previousCount = previousCount,
                onMarkPrevious = {
                    pendingPlayed = null
                    applyPlayed((0 until lowest).toSet() + indices, bulk = true)
                },
                onOnlyThese = {
                    pendingPlayed = null
                    applyPlayed(indices, bulk = false)
                },
                onDismiss = { pendingPlayed = null },
            )
        }
    }
}
