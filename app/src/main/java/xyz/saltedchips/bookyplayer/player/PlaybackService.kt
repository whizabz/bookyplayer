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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
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
import xyz.saltedchips.bookyplayer.library.CoverLoader
import xyz.saltedchips.bookyplayer.player.AutoLibrary.Companion.KEY_REPEAT
import xyz.saltedchips.bookyplayer.player.AutoLibrary.Companion.KEY_SKIP_BACK
import xyz.saltedchips.bookyplayer.player.AutoLibrary.Companion.KEY_SKIP_FORWARD
import xyz.saltedchips.bookyplayer.player.AutoLibrary.Companion.KEY_SPEED
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
            KEY_SPEED -> exoPlayer?.setPlaybackSpeed(library.speed())
            KEY_REPEAT -> {
                val repeating = library.repeatEnabled()
                val mode = if (repeating) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                if (exoPlayer?.repeatMode != mode) {
                    exoPlayer?.repeatMode = mode
                }
            }
        }
        if (key == KEY_SKIP_BACK || key == KEY_SKIP_FORWARD || key == KEY_REPEAT || key == KEY_SPEED) {
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
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
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

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            library.setSpeed(playbackParameters.speed)
            refreshMediaButtons()
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
            .setBitmapLoader(CacheBitmapLoader(CoverBitmapLoader(this)))
            .build()
        val notifications = DefaultMediaNotificationProvider.Builder(this).build()
        notifications.setSmallIcon(R.drawable.ms_book_2)
        setMediaNotificationProvider(notifications)
        scheduleWidgetUpdate(skipPlayer)
        handler.post { prepareLastBookIfIdle() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
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

    private fun prepareLastBookIfIdle() {
        val exo = player ?: return
        if (exo.mediaItemCount > 0) return
        val resume = library.playbackResumption(touchLastPlayed = false) ?: return
        if (resume.mediaItems.isEmpty()) return
        applyPlaybackPrefs()
        exo.setMediaItems(resume.mediaItems, resume.startIndex, resume.startPositionMs)
        exo.prepare()
        captureNowPlaying(exo, this)
        lastWidgetUpdateAt = 0L
        pushWidgetUpdate()
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
        val speed = player?.playbackParameters?.speed ?: library.speed()
        return ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_REWIND)
                .setDisplayName("Seek back")
                .setCustomIconResId(R.drawable.ms_replay)
                .setSessionCommand(SessionCommand(COMMAND_SEEK_BACK, android.os.Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
                .setDisplayName("Seek forward")
                .setCustomIconResId(R.drawable.ms_forward)
                .setSessionCommand(SessionCommand(COMMAND_SEEK_FORWARD, android.os.Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(formatNotificationSpeed(speed))
                .setCustomIconResId(notificationSpeedIcon(speed))
                .setSessionCommand(SessionCommand(COMMAND_SPEED, android.os.Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
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

    private fun playerCommandsFor(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): Player.Commands {
        val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
        if (isShadeController(session, controller)) {
            commands
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        return commands.build()
    }

    private fun isShadeController(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): Boolean {
        if (session.isMediaNotificationController(controller)) return true
        return controller.packageName == "com.android.systemui" ||
            controller.packageName == "com.google.android.systemui"
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (!isTrustedController(session, controller)) {
                return MediaSession.ConnectionResult.reject()
            }
            CoverLoader.grantArtworkTo(this@PlaybackService, controller.packageName)
            handler.post { prepareLastBookIfIdle() }
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_REPEAT, android.os.Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SPEED, android.os.Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SEEK_BACK, android.os.Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SEEK_FORWARD, android.os.Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommandsFor(session, controller))
                .setMediaButtonPreferences(mediaButtonPreferences())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_SEEK_BACK) {
                player?.seekBack()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == COMMAND_SEEK_FORWARD) {
                player?.seekForward()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == COMMAND_REPEAT) {
                val enabled = !library.repeatEnabled()
                library.setRepeatEnabled(enabled)
                exoPlayer?.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                refreshMediaButtons()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == COMMAND_SPEED) {
                val next = nextNotificationSpeed(player?.playbackParameters?.speed ?: library.speed())
                library.setSpeed(next)
                exoPlayer?.setPlaybackSpeed(next)
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
            handler.post { prepareLastBookIfIdle() }
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
    }

    private fun isTrustedController(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): Boolean {
        if (session.isMediaNotificationController(controller)) return true
        if (session.isAutomotiveController(controller)) return true
        if (session.isAutoCompanionController(controller)) return true
        return TrustedMediaClients.allows(controller.packageName, packageName)
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "xyz.saltedchips.bookyplayer.action.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_SEEK_BACK = "xyz.saltedchips.bookyplayer.action.WIDGET_SEEK_BACK"
        const val ACTION_WIDGET_SEEK_FORWARD = "xyz.saltedchips.bookyplayer.action.WIDGET_SEEK_FORWARD"
        private const val COMMAND_REPEAT = "xyz.saltedchips.bookyplayer.REPEAT"
        private const val COMMAND_SPEED = "xyz.saltedchips.bookyplayer.SPEED"
        private const val COMMAND_SEEK_BACK = "xyz.saltedchips.bookyplayer.SEEK_BACK"
        private const val COMMAND_SEEK_FORWARD = "xyz.saltedchips.bookyplayer.SEEK_FORWARD"
        private const val PERSIST_INTERVAL_MS = 5_000L
        private const val WIDGET_THROTTLE_MS = 400L
    }
}

private fun nextNotificationSpeed(current: Float): Float {
    val steps = floatArrayOf(0.8f, 1f, 1.2f, 1.5f, 2f)
    return steps.firstOrNull { it > current + 0.04f } ?: steps.first()
}

private fun formatNotificationSpeed(speed: Float): String {
    val trimmed = speed.toString().trimEnd('0').trimEnd('.')
    return "${trimmed}x"
}

private fun notificationSpeedIcon(speed: Float): Int {
    return when {
        speed < 0.9f -> R.drawable.notification_speed_0_8
        speed < 1.1f -> R.drawable.notification_speed_1
        speed < 1.35f -> R.drawable.notification_speed_1_2
        speed < 1.75f -> R.drawable.notification_speed_1_5
        else -> R.drawable.notification_speed_2
    }
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

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> false
            else -> super.isCommandAvailable(command)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            .remove(Player.COMMAND_SEEK_TO_NEXT)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()
    }
}
