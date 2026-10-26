package com.cheesecomer.cartmate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * プレビュー用。ModalBottomSheet は別のウィンドウに表示されるためプレビューに描かれないので、
 * 画面（[background]）の上に幕とシートの見た目を重ねて、シートの中身（[content]）を表示する
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetPreviewFrame(
    background: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        background()
        Box(Modifier.fillMaxSize().background(BottomSheetDefaults.ScrimColor))
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            shape = BottomSheetDefaults.ExpandedShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}
