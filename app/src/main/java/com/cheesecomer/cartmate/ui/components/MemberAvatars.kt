package com.cheesecomer.cartmate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheesecomer.cartmate.domain.model.Member

/**
 * 共有のリストのメンバーのアバター（docs/design/ui.md「リスト一覧」）。2人まで少し重ねて表示し、3人目以降は「+N」にする。
 * 読み上げは名前の一覧にする
 */
@Composable
fun MemberAvatars(members: List<Member>, size: Dp, modifier: Modifier = Modifier) {
    if (members.isEmpty()) return
    val shown = members.take(MAX_SHOWN)
    val rest = members.size - shown.size
    val description = members.joinToString("、") { it.displayName }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 少し重ねる
        horizontalArrangement = Arrangement.spacedBy(-OVERLAP),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        shown.forEach { member ->
            Avatar(
                name = member.displayName,
                photoUrl = member.photoUrl,
                size = size,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
            )
        }
        if (rest > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
            ) {
                Text(
                    "+$rest",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4f).sp,
                )
            }
        }
    }
}

private const val MAX_SHOWN = 2
private val OVERLAP = 6.dp
