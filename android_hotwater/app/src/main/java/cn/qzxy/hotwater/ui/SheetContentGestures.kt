package cn.qzxy.hotwater.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/** Inner lists keep their scroll; only the handle/outside area can move the sheet itself. */
@Composable
internal fun Modifier.sheetContentGestures(): Modifier {
    val boundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(0f, available.y)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(0f, available.y)
        }
    }
    // Recognize vertical gestures even over a non-scrolling part of the content. Consume
    // unused scroll after children, so reaching either edge never drags the parent sheet.
    return nestedScroll(boundary).scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
}
