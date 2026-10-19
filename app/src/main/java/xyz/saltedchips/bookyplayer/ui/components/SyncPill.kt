package xyz.saltedchips.bookyplayer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A compact floating "Sync to audio" pill (about 32dp tall) with tight inner padding. */
@Composable
fun SyncPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BookyIcons.sync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer4()
            Text("Sync to audio", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Spacer4() {
    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
}
