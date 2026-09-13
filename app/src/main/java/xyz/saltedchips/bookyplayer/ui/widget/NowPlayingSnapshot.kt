package xyz.saltedchips.bookyplayer.ui.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.library.CoverLoader
import xyz.saltedchips.bookyplayer.player.AutoLibrary

data class NowPlayingSnapshot(
    val book: Audiobook?,
    val title: String,
    val author: String,
    val isPlaying: Boolean,
    val cover: Bitmap?,
)

object WidgetGlanceKeys {
    val playing = booleanPreferencesKey("widget_is_playing")
    val title = stringPreferencesKey("widget_title")
    val author = stringPreferencesKey("widget_author")
    val bookId = stringPreferencesKey("widget_book_id")
}

object NowPlayingSnapshotStore {
    private const val KEY_PLAYING = "widget_is_playing"
    private const val KEY_TITLE = "widget_title"
    private const val KEY_AUTHOR = "widget_author"

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(AutoLibrary.PREFS, Context.MODE_PRIVATE)
    }

    fun write(
        context: Context,
        isPlaying: Boolean,
        bookId: String?,
        title: String,
        author: String,
    ) {
        val editor = prefs(context).edit()
            .putBoolean(KEY_PLAYING, isPlaying)
            .putString(KEY_TITLE, title)
            .putString(KEY_AUTHOR, author)
        if (!bookId.isNullOrBlank()) {
            editor.putString(AutoLibrary.KEY_BOOK_ID, bookId)
        }
        editor.commit()
    }

    fun load(context: Context): NowPlayingSnapshot {
        val prefs = prefs(context)
        val library = AutoLibrary(context)
        val bookId = prefs.getString(AutoLibrary.KEY_BOOK_ID, null)
        val book = bookId?.let(library::bookById)
        val title = prefs.getString(KEY_TITLE, null)?.ifBlank { null }
            ?: book?.title.orEmpty()
        val author = prefs.getString(KEY_AUTHOR, null)?.ifBlank { null }
            ?: book?.author.orEmpty()
        val cover = book?.let { CoverLoader.load(context, it, sampleSize = 4) }
        return NowPlayingSnapshot(
            book = book,
            title = title,
            author = author,
            isPlaying = prefs.getBoolean(KEY_PLAYING, false),
            cover = cover,
        )
    }
}
