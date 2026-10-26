package com.cheesecomer.cartmate.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.list.ListProgress
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.ui.components.DraggingElevation
import com.cheesecomer.cartmate.ui.components.cardFrame
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing

/**
 * リスト一覧のカード（docs/design/ui.md「リスト一覧」）。リスト名、備考、件数、進捗バーを表示する。
 * プライベートのリストには鍵アイコンを表示する。家族のメンバーはどのリストでも同じなので、カードにはアバターを出さない
 */
@Composable
fun ListCard(
    list: ListSummary,
    onClick: () -> Unit,
    dragging: Boolean,
    actions: List<CustomAccessibilityAction>,
    // ロングタップでドラッグを始める部分
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val progress = list.progress
    Column(
        modifier = Modifier
            .cardFrame(elevation = if (dragging) DraggingElevation else 0.dp)
            .then(modifier)
            .background(colors.surfaceContainerLowest)
            .clickable(onClickLabel = stringResource(R.string.lists_open), onClick = onClick)
            // TalkBack 用の操作（並び替え）。clickable と同じ要素に付けないと読み上げの対象にならない
            .semantics { customActions = actions }
            .heightIn(min = 160.dp)
            .padding(CartMateSpacing.large),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    list.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (list.visibility == Visibility.PRIVATE) {
                    Icon(
                        painterResource(R.drawable.ic_lock),
                        contentDescription = stringResource(R.string.private_label),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = CartMateSpacing.xSmall).size(16.dp),
                    )
                }
            }
            if (list.memo != null) {
                Text(
                    list.memo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = CartMateSpacing.xSmall),
                )
            }
        }
        Column(Modifier.padding(top = CartMateSpacing.large)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    progress.openCount.toString(),
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (progress.status == ListProgress.Status.REMAINING) colors.primary else colors.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        when (progress.status) {
                            ListProgress.Status.REMAINING -> R.string.lists_count_remaining
                            ListProgress.Status.NOT_REGISTERED -> R.string.lists_count_not_registered
                            ListProgress.Status.DONE -> R.string.lists_count_done
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = CartMateSpacing.xSmall, bottom = 2.dp),
                )
            }
            Spacer(Modifier.height(CartMateSpacing.small))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = colors.primary,
                trackColor = colors.surfaceContainer,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
