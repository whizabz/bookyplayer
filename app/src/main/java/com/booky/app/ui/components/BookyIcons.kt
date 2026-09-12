package com.booky.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.booky.app.R

object BookyIcons {
    val library: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_books_movies_and_music)
    val libraryFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_books_movies_and_music_fill)
    val settings: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_settings)
    val close: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_close)
    val back: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_arrow_back)
    val play: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_play_arrow)
    val pause: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_pause)
    val timer: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_timer)
    val cast: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_cast)
    val skipBack: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_replay)
    val skipForward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_forward)
    val bookmark: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_bookmark)
    val chapters: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_list)
    val more: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_more_horiz)
    val select: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_check_circle)
    val importFiles: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_upload)
    val download: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_cloud_download)
    val servers: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_hard_drive)
    val folder: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_create_new_folder)
    val viewOptions: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_tune)
    val sort: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_sort)
    val chevronRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_chevron_right)
    val chevronDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_keyboard_arrow_down)
    val info: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_info)
    val skipping: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_skip_next)
    val skipNext: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_skip_next)
    val skipPrevious: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_skip_previous)
    val restartAlt: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_restart_alt)
    val check: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_check)
    val doneAll: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_done_all)
    val book2: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_book_2)
    val delete: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_delete)
    val edit: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_edit)
    val speed: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_speed)
    val trimSilence: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_content_cut)
    val autoSkipping: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_autoplay)
    val repeat: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_repeat)
}
