package xyz.saltedchips.bookyplayer.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.saltedchips.bookyplayer.player.AutoLibrary

fun Player.showsAsPlaying(): Boolean {
    if (playbackState == Player.STATE_ENDED) return false
    return playWhenReady
}

fun captureNowPlaying(player: Player, context: Context) {
    val item = player.currentMediaItem
    val meta = item?.mediaMetadata
    val bookId = item?.mediaId?.let(AutoLibrary::bookIdFromMediaId)
    NowPlayingSnapshotStore.write(
        context = context,
        isPlaying = player.showsAsPlaying(),
        bookId = bookId,
        title = meta?.displayTitle?.toString()?.ifBlank { null }
            ?: meta?.title?.toString().orEmpty(),
        author = meta?.artist?.toString().orEmpty(),
    )
}

suspend fun refreshNowPlayingWidgets(context: Context) {
    val app = context.applicationContext
    val snapshot = NowPlayingSnapshotStore.load(app)
    withContext(Dispatchers.Default) {
        val glanceManager = GlanceAppWidgetManager(app)
        val appWidgetManager = AppWidgetManager.getInstance(app)
        listOf(
            SquareNowPlayingWidgetReceiver::class.java to SquareNowPlayingWidget(),
            ExtraWideNowPlayingWidgetReceiver::class.java to ExtraWideNowPlayingWidget(),
            MediumNowPlayingWidgetReceiver::class.java to MediumNowPlayingWidget(),
            WideNowPlayingWidgetReceiver::class.java to WideNowPlayingWidget(),
        ).forEach { (receiver, widget) ->
            appWidgetManager.getAppWidgetIds(ComponentName(app, receiver)).forEach { appWidgetId ->
                val glanceId = try {
                    glanceManager.getGlanceIdBy(appWidgetId)
                } catch (_: Exception) {
                    return@forEach
                }
                updateAppWidgetState(app, glanceId) { prefs ->
                    prefs[WidgetGlanceKeys.playing] = snapshot.isPlaying
                    prefs[WidgetGlanceKeys.title] = snapshot.title
                    prefs[WidgetGlanceKeys.author] = snapshot.author
                    snapshot.book?.id?.let { prefs[WidgetGlanceKeys.bookId] = it }
                }
                widget.update(app, glanceId)
            }
        }
        NowPlayingWidget().updateAll(app)
        SquareNowPlayingWidget().updateAll(app)
        ExtraWideNowPlayingWidget().updateAll(app)
        MediumNowPlayingWidget().updateAll(app)
        WideNowPlayingWidget().updateAll(app)
    }
}
