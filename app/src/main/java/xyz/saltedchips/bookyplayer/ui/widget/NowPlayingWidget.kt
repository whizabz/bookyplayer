package xyz.saltedchips.bookyplayer.ui.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders as Material3ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import xyz.saltedchips.bookyplayer.MainActivity
import xyz.saltedchips.bookyplayer.R
import xyz.saltedchips.bookyplayer.player.PlaybackService

open class NowPlayingWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SQUARE, PLAYER, ULTRA),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = widgetColors()) {
                NowPlayingContent(widgetSnapshot())
            }
        }
    }

    companion object {
        val SQUARE = DpSize(100.dp, 100.dp)
        val PLAYER = DpSize(250.dp, 250.dp)
        val ULTRA = DpSize(250.dp, 40.dp)
    }
}

class SquareNowPlayingWidget : NowPlayingWidget()

class WideNowPlayingWidget : NowPlayingWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
}

class ExtraWideNowPlayingWidget : NowPlayingWidget()

class SquareNowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SquareNowPlayingWidget()
}

class WideNowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WideNowPlayingWidget()
}

class ExtraWideNowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ExtraWideNowPlayingWidget()
}

@Composable
private fun widgetColors(): ColorProviders {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme.colors
    } else {
        Material3ColorProviders(
            light = lightColorScheme(),
            dark = darkColorScheme(),
        )
    }
}

@Composable
private fun widgetSnapshot(): NowPlayingSnapshot {
    val glance = currentState<Preferences>()
    val stored = NowPlayingSnapshotStore.load(LocalContext.current)
    val title = glance[WidgetGlanceKeys.title]?.ifBlank { null } ?: stored.title
    val author = glance[WidgetGlanceKeys.author]?.ifBlank { null } ?: stored.author
    return stored.copy(
        title = title,
        author = author,
        isPlaying = glance[WidgetGlanceKeys.playing] ?: stored.isPlaying,
    )
}

@Composable
private fun NowPlayingContent(snapshot: NowPlayingSnapshot) {
    val size = LocalSize.current
    when {
        size == NowPlayingWidget.SQUARE -> SquareNowPlaying(snapshot)
        size == NowPlayingWidget.ULTRA || size.height < 90.dp -> {
            StripNowPlaying(snapshot, coverSize = 56.dp)
        }
        else -> StackedNowPlaying(snapshot)
    }
}

@Composable
private fun SquareNowPlaying(snapshot: NowPlayingSnapshot) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground),
    ) {
        CoverImage(
            snapshot,
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(openApp()),
        )
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            PlayButton(isPlaying = snapshot.isPlaying, size = 40.dp)
        }
    }
}

@Composable
private fun StackedNowPlaying(snapshot: NowPlayingSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .clickable(openApp()),
        ) {
            CoverImage(snapshot, modifier = GlanceModifier.fillMaxSize())
        }
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable(openApp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = snapshot.title.ifBlank { "Booky Player" },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            if (snapshot.author.isNotBlank()) {
                Text(
                    text = snapshot.author,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        TransportGroup(
            isPlaying = snapshot.isPlaying,
            playSize = 52.dp,
            skipSize = 44.dp,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun StripNowPlaying(snapshot: NowPlayingSnapshot, coverSize: Dp) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(coverSize)
                .cornerRadius(12.dp)
                .clickable(openApp()),
        ) {
            CoverImage(snapshot, modifier = GlanceModifier.fillMaxSize())
        }
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(start = 10.dp, end = 8.dp)
                .clickable(openApp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = snapshot.title.ifBlank { "Booky Player" },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                ),
            )
            if (snapshot.author.isNotBlank()) {
                Text(
                    text = snapshot.author,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
        TransportGroup(
            isPlaying = snapshot.isPlaying,
            playSize = 40.dp,
            skipSize = 36.dp,
        )
    }
}

@Composable
private fun TransportGroup(
    isPlaying: Boolean,
    playSize: Dp,
    skipSize: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SkipButton(
            icon = R.drawable.ms_replay,
            description = "Seek back",
            action = PlaybackService.ACTION_WIDGET_SEEK_BACK,
            size = skipSize,
        )
        Spacer(GlanceModifier.width(2.dp))
        PlayButton(isPlaying = isPlaying, size = playSize)
        Spacer(GlanceModifier.width(2.dp))
        SkipButton(
            icon = R.drawable.ms_forward,
            description = "Seek forward",
            action = PlaybackService.ACTION_WIDGET_SEEK_FORWARD,
            size = skipSize,
        )
    }
}

@Composable
private fun SkipButton(
    icon: Int,
    description: String,
    action: String,
    size: Dp,
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(widgetServiceAction(action)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            modifier = GlanceModifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, size: Dp) {
    val icon = if (isPlaying) R.drawable.ms_pause else R.drawable.ms_play_arrow
    val description = if (isPlaying) "Pause" else "Play"
    val radius = if (isPlaying) size / 2 else 16.dp
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(radius)
            .background(GlanceTheme.colors.primary)
            .clickable(widgetServiceAction(PlaybackService.ACTION_WIDGET_PLAY_PAUSE)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            modifier = GlanceModifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun CoverImage(snapshot: NowPlayingSnapshot, modifier: GlanceModifier) {
    val cover = snapshot.cover
    if (cover != null) {
        Image(
            provider = ImageProvider(cover),
            contentDescription = snapshot.title,
            contentScale = ContentScale.Crop,
            modifier = modifier.cornerRadius(16.dp),
        )
    } else {
        Box(
            modifier = modifier
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = snapshot.title.firstOrNull()?.uppercase() ?: "B",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun widgetServiceAction(action: String): Action {
    val context = LocalContext.current
    return actionStartService(
        Intent(context, PlaybackService::class.java).setAction(action),
        isForegroundService = true,
    )
}

@Composable
private fun openApp() = actionStartActivity(
    Intent(LocalContext.current, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
)
