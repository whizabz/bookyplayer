@file:OptIn(UnstableApi::class)

package xyz.saltedchips.bookyplayer.player

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.saltedchips.bookyplayer.MainActivity
import xyz.saltedchips.bookyplayer.R
import xyz.saltedchips.bookyplayer.player.AutoLibrary.Companion.KEY_REPEAT
import xyz.saltedchips.bookyplayer.player.AutoLibrary.Companion.KEY_SKIP_BACK
import xyz.saltedchips.bookyplayer.player.AutoLibrary.Companion.KEY_SKIP_FORWARD
import xyz.saltedchips.bookyplayer.ui.widget.captureNowPlaying
import xyz.saltedchips.bookyplayer.ui.widget.refreshNowPlayingWidgets

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private var player: SkipAwarePlayer? = null
    private var exoPlayer: ExoPlayer? = null
    private lateinit var library: AutoLibrary
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastWidgetUpdateAt = 0L

    private val persistWhilePlaying = object : Runnable {
        override fun run() {
            persistProgress()
            handler.postDelayed(this, PERSIST_INTERVAL_MS)
        }
    }

    private val skipPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val skipPlayer = player ?: return@OnSharedPreferenceChangeListener
        when (key) {
            KEY_SKIP_BACK -> skipPlayer.backIncrementMs = library.skipBackMs()
            KEY_SKIP_FORWARD -> skipPlayer.forwardIncrementMs = library.skipForwardMs()
            KEY_REPEAT -> {
                val repeating = library.repeatEnabled()
                val mode = if (repeating) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                if (exoPlayer?.repeatMode != mode) {
                    exoPlayer?.repeatMode = mode
                }
            }
        }
        if (key == KEY_SKIP_BACK || key == KEY_SKIP_FORWARD || key == KEY_REPEAT) {
            refreshMediaButtons()
        }
    }

    private val persistListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val important = events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
            )
            if (important) scheduleWidgetUpdate(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            persistProgress()
            handler.removeCallbacks(persistWhilePlaying)
            if (isPlaying) {
                handler.postDelayed(persistWhilePlaying, PERSIST_INTERVAL_MS)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) persistProgress()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            library.setRepeatEnabled(repeatMode != Player.REPEAT_MODE_OFF)
            refreshMediaButtons()
        }
    }

    override fun onCreate() {
        super.onCreate()
        library = AutoLibrary(this)
        val prefs = getSharedPreferences(AutoLibrary.PREFS, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(skipPrefsListener)
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(library.skipBackMs())
            .setSeekForwardIncrementMs(library.skipForwardMs())
            .build()
        exo.trackSelectionParameters = exo.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
        exo.addListener(persistListener)
        exoPlayer = exo
        val skipPlayer = SkipAwarePlayer(exo).apply {
            backIncrementMs = library.skipBackMs()
            forwardIncrementMs = library.skipForwardMs()
        }
        player = skipPlayer
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, skipPlayer, LibraryCallback())
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(mediaButtonPreferences())
            .build()
        val notifications = DefaultMediaNotificationProvider.Builder(this).build()
        notifications.setSmallIcon(R.drawable.ms_book_2)
        setMediaNotificationProvider(notifications)
        scheduleWidgetUpdate(skipPlayer)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        if (!TrustedMediaClients.allows(controllerInfo.packageName, packageName)) return null
        return session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (WidgetCommandAuth.matches(this, intent)) {
            when (intent?.action) {
                ACTION_WIDGET_PLAY_PAUSE -> handleWidgetPlayPause()
                ACTION_WIDGET_SEEK_BACK -> player?.seekBack()
                ACTION_WIDGET_SEEK_FORWARD -> player?.seekForward()
            }
        }
        return result
    }

    override fun onDestroy() {
        handler.removeCallbacks(persistWhilePlaying)
        handler.removeCallbacks(widgetUpdateRunnable)
        persistProgress()
        player?.let { captureNowPlaying(it, this) }
        val app = applicationContext
        handler.post {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                refreshNowPlayingWidgets(app)
            }
        }
        scope.cancel()
        getSharedPreferences(AutoLibrary.PREFS, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(skipPrefsListener)
        session?.run {
            exoPlayer?.removeListener(persistListener)
            player.release()
            release()
        }
        session = null
        player = null
        exoPlayer = null
        super.onDestroy()
    }

    private fun handleWidgetPlayPause() {
        val exo = player ?: return
        if (exo.mediaItemCount == 0) {
            val resume = library.playbackResumption() ?: return
            exo.setMediaItems(resume.mediaItems, resume.startIndex, resume.startPositionMs)
            exo.prepare()
            exo.play()
            player?.let {
                captureNowPlaying(it, this)
                lastWidgetUpdateAt = 0L
                pushWidgetUpdate()
            }
            return
        }
        if (exo.isPlaying) exo.pause() else exo.play()
        player?.let {
            captureNowPlaying(it, this)
            lastWidgetUpdateAt = 0L
            pushWidgetUpdate()
        }
    }

    private fun persistProgress() {
        val exo = player ?: return
        library.persistFromPlayer(exo)
    }

    private fun scheduleWidgetUpdate(player: Player) {
        captureNowPlaying(player, this)
        val now = System.currentTimeMillis()
        if (now - lastWidgetUpdateAt < WIDGET_THROTTLE_MS) {
            handler.removeCallbacks(widgetUpdateRunnable)
            handler.postDelayed(widgetUpdateRunnable, WIDGET_THROTTLE_MS)
            return
        }
        lastWidgetUpdateAt = now
        pushWidgetUpdate()
    }

    private val widgetUpdateRunnable = Runnable {
        player?.let { captureNowPlaying(it, this) }
        lastWidgetUpdateAt = System.currentTimeMillis()
        pushWidgetUpdate()
    }

    private fun pushWidgetUpdate() {
        scope.launch(Dispatchers.Default) {
            refreshNowPlayingWidgets(applicationContext)
        }
    }

    private fun refreshMediaButtons() {
        session?.setMediaButtonPreferences(mediaButtonPreferences())
    }

    private fun mediaButtonPreferences(): ImmutableList<CommandButton> {
        val repeating = library.repeatEnabled()
        return ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setDisplayName("Previous chapter")
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setDisplayName("Next chapter")
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            CommandButton.Builder(skipBackIcon(library.skipBackSeconds()))
                .setDisplayName("Seek back")
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setSlots(CommandButton.SLOT_BACK_SECONDARY)
                .build(),
            CommandButton.Builder(skipForwardIcon(library.skipForwardSeconds()))
                .setDisplayName("Seek forward")
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
            CommandButton.Builder(
                if (repeating) CommandButton.ICON_REPEAT_ALL else CommandButton.ICON_REPEAT_OFF,
            )
                .setDisplayName("Repeat")
                .setCustomIconResId(R.drawable.ms_repeat)
                .setSessionCommand(SessionCommand(COMMAND_REPEAT, android.os.Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (!TrustedMediaClients.allows(controller.packageName, packageName)) {
                return MediaSession.ConnectionResult.reject()
            }
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_REPEAT, android.os.Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setMediaButtonPreferences(mediaButtonPreferences())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_REPEAT) {
                val enabled = !library.repeatEnabled()
                library.setRepeatEnabled(enabled)
                exoPlayer?.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                refreshMediaButtons()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = AutoLibrary.contentStyleExtras
            val resultParams = MediaLibraryService.LibraryParams.Builder()
                .setExtras(extras)
                .build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(library.libraryRoot(), resultParams),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = library.children(parentId)
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params),
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = library.item(mediaId)
                ?: return Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE),
                )
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = resolve(mediaItems) ?: return Futures.immediateFuture(mutableListOf())
            applyPlaybackPrefs()
            return Futures.immediateFuture(resolved.mediaItems.toMutableList())
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val resolved = resolve(mediaItems)
                ?: return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L),
                )
            applyPlaybackPrefs()
            return if (mediaItems.size > 1) {
                Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        resolved.mediaItems,
                        startIndex,
                        startPositionMs,
                    ),
                )
            } else {
                Futures.immediateFuture(resolved)
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val resumed = library.playbackResumption()
                ?: return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L),
                )
            applyPlaybackPrefs()
            return Futures.immediateFuture(resumed)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            val event = intent.mediaButtonEvent() ?: return super.onMediaButtonEvent(session, controllerInfo, intent)
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) {
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }
            val skipPlayer = player ?: return super.onMediaButtonEvent(session, controllerInfo, intent)
            return when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                -> {
                    skipPlayer.seekForward()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                -> {
                    skipPlayer.seekBack()
                    true
                }
                else -> super.onMediaButtonEvent(session, controllerInfo, intent)
            }
        }

        private fun resolve(
            mediaItems: List<MediaItem>,
        ): MediaSession.MediaItemsWithStartPosition? {
            val mediaId = mediaItems.firstOrNull()?.mediaId?.takeIf { it.isNotBlank() } ?: return null
            return library.resolvePlay(mediaId)
        }

        private fun applyPlaybackPrefs() {
            val exo = exoPlayer ?: return
            exo.setPlaybackSpeed(library.speed())
            exo.repeatMode = if (library.repeatEnabled()) {
                Player.REPEAT_MODE_ALL
            } else {
                Player.REPEAT_MODE_OFF
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "xyz.saltedchips.bookyplayer.action.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_SEEK_BACK = "xyz.saltedchips.bookyplayer.action.WIDGET_SEEK_BACK"
        const val ACTION_WIDGET_SEEK_FORWARD = "xyz.saltedchips.bookyplayer.action.WIDGET_SEEK_FORWARD"
        private const val COMMAND_REPEAT = "xyz.saltedchips.bookyplayer.REPEAT"
        private const val PERSIST_INTERVAL_MS = 5_000L
        private const val WIDGET_THROTTLE_MS = 400L
    }
}

private fun skipBackIcon(seconds: Int): Int = when (seconds) {
    5 -> CommandButton.ICON_SKIP_BACK_5
    10 -> CommandButton.ICON_SKIP_BACK_10
    15 -> CommandButton.ICON_SKIP_BACK_15
    30 -> CommandButton.ICON_SKIP_BACK_30
    else -> CommandButton.ICON_SKIP_BACK
}

private fun skipForwardIcon(seconds: Int): Int = when (seconds) {
    5 -> CommandButton.ICON_SKIP_FORWARD_5
    10 -> CommandButton.ICON_SKIP_FORWARD_10
    15 -> CommandButton.ICON_SKIP_FORWARD_15
    30 -> CommandButton.ICON_SKIP_FORWARD_30
    else -> CommandButton.ICON_SKIP_FORWARD
}

@Suppress("DEPRECATION")
private fun Intent.mediaButtonEvent(): KeyEvent? {
    return extras?.getParcelable(Intent.EXTRA_KEY_EVENT) as? KeyEvent
}

private class SkipAwarePlayer(
    player: ExoPlayer,
) : ForwardingPlayer(player) {
    var backIncrementMs: Long = 10_000L
    var forwardIncrementMs: Long = 10_000L

    override fun getSeekBackIncrement(): Long = backIncrementMs

    override fun getSeekForwardIncrement(): Long = forwardIncrementMs

    override fun seekBack() {
        skipBookPosition(-backIncrementMs)
    }

    override fun seekForward() {
        skipBookPosition(forwardIncrementMs)
    }
}
