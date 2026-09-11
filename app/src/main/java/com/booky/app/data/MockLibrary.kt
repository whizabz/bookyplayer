package com.booky.app.data

import androidx.annotation.DrawableRes
import com.booky.app.R

data class Audiobook(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String,
    val chapterCount: Int,
    val fileCount: Int,
    val durationMs: Long,
    val listenedMs: Long,
    val currentChapter: Int,
    val currentChapterTitle: String,
    val fileName: String,
    @param:DrawableRes val coverRes: Int = 0,
    val coverUri: String? = null,
    val artworkFileUri: String? = null,
    val mediaUris: List<String> = emptyList(),
    val chapterTitles: List<String> = emptyList(),
    val chapterDurationsMs: List<Long> = emptyList(),
    val chapterStartMs: List<Long> = emptyList(),
    val addedAtMs: Long = 0L,
    val lastPlayedMs: Long = 0L,
)

private fun hours(h: Int, m: Int) = ((h * 3600L) + (m * 60L)) * 1000L

object MockLibrary {
    val pride = Audiobook(
        id = "pride",
        title = "Pride and Prejudice",
        author = "Jane Austen",
        narrator = "Rosamund Pike",
        chapterCount = 61,
        fileCount = 12,
        durationMs = hours(11, 35),
        listenedMs = (hours(11, 35) * 0.67).toLong(),
        currentChapter = 41,
        currentChapterTitle = "Chapter 41",
        fileName = "Pride and Prejudice.m4b",
        coverRes = R.drawable.cover_pride,
    )

    val frankenstein = Audiobook(
        id = "frankenstein",
        title = "Frankenstein",
        author = "Mary Shelley",
        narrator = "Dan Stevens",
        chapterCount = 28,
        fileCount = 8,
        durationMs = hours(8, 42),
        listenedMs = (hours(8, 42) * 0.12).toLong(),
        currentChapter = 3,
        currentChapterTitle = "Chapter 3",
        fileName = "Frankenstein.m4b",
        coverRes = R.drawable.cover_frankenstein,
    )

    val dracula = Audiobook(
        id = "dracula",
        title = "Dracula",
        author = "Bram Stoker",
        narrator = "Alan Cumming",
        chapterCount = 27,
        fileCount = 10,
        durationMs = hours(15, 28),
        listenedMs = 0L,
        currentChapter = 1,
        currentChapterTitle = "Chapter 1",
        fileName = "Dracula.m4b",
        coverRes = R.drawable.cover_dracula,
    )

    val warOfTheWorlds = Audiobook(
        id = "war_worlds",
        title = "The War of the Worlds",
        author = "H. G. Wells",
        narrator = "David Tennant",
        chapterCount = 27,
        fileCount = 6,
        durationMs = hours(6, 51),
        listenedMs = (hours(6, 51) * 0.88).toLong(),
        currentChapter = 24,
        currentChapterTitle = "Chapter 24",
        fileName = "The War of the Worlds.m4b",
        coverRes = R.drawable.cover_war_worlds,
    )

    val alice = Audiobook(
        id = "alice",
        title = "Alice's Adventures in Wonderland",
        author = "Lewis Carroll",
        narrator = "Miriam Margolyes",
        chapterCount = 12,
        fileCount = 4,
        durationMs = hours(3, 12),
        listenedMs = hours(3, 12),
        currentChapter = 12,
        currentChapterTitle = "Chapter 12",
        fileName = "Alice in Wonderland.m4b",
        coverRes = R.drawable.cover_alice,
    )

    val sherlock = Audiobook(
        id = "sherlock",
        title = "The Adventures of Sherlock Holmes",
        author = "Arthur Conan Doyle",
        narrator = "Stephen Fry",
        chapterCount = 12,
        fileCount = 12,
        durationMs = hours(10, 5),
        listenedMs = (hours(10, 5) * 0.41).toLong(),
        currentChapter = 5,
        currentChapterTitle = "The Five Orange Pips",
        fileName = "The Adventures of Sherlock Holmes.m4b",
        coverRes = R.drawable.cover_sherlock,
    )

    val moby = Audiobook(
        id = "moby",
        title = "Moby-Dick",
        author = "Herman Melville",
        narrator = "William Hootkins",
        chapterCount = 135,
        fileCount = 24,
        durationMs = hours(24, 10),
        listenedMs = (hours(24, 10) * 0.08).toLong(),
        currentChapter = 11,
        currentChapterTitle = "Chapter 11",
        fileName = "Moby-Dick.m4b",
        coverRes = R.drawable.cover_moby,
    )

    val janeEyre = Audiobook(
        id = "jane_eyre",
        title = "Jane Eyre",
        author = "Charlotte Brontë",
        narrator = "Thandiwe Newton",
        chapterCount = 38,
        fileCount = 16,
        durationMs = hours(19, 4),
        listenedMs = (hours(19, 4) * 0.55).toLong(),
        currentChapter = 21,
        currentChapterTitle = "Chapter 21",
        fileName = "Jane Eyre.m4b",
        coverRes = R.drawable.cover_jane_eyre,
    )

    val books: List<Audiobook> = listOf(
        pride,
        frankenstein,
        dracula,
        warOfTheWorlds,
        alice,
        sherlock,
        moby,
        janeEyre,
    )
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

fun formatMinutes(ms: Long): String {
    val minutes = ((ms + 30_000L) / 60_000L).coerceAtLeast(if (ms > 0L) 1L else 0L)
    return if (minutes == 1L) "1 min" else "$minutes mins"
}

fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
