package xyz.saltedchips.bookyplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import xyz.saltedchips.bookyplayer.R

object BookyIcons {
    val settings: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_settings)
    val close: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_close)
    val back: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_arrow_back)
    val play: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_play_arrow)
    val pause: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_pause)
    val timer: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_timer)
    val skipBack: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_replay)
    val skipForward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_forward)
    val chapters: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_list)
    val more: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_more_horiz)
    val folder: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_create_new_folder)
    val sort: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_sort)
    val chevronRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_chevron_right)
    val skipNext: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_skip_next)
    val skipPrevious: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_skip_previous)
    val restartAlt: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_restart_alt)
    val check: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_check)
    val doneAll: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_done_all)
    val book2: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_book_2)
    val delete: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_delete)
    val edit: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_edit)
    val speed: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_speed)
    val repeat: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ms_repeat)
}
