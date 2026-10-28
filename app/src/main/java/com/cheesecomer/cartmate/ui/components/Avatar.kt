package com.cheesecomer.cartmate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cheesecomer.cartmate.domain.family.AvatarInitial

/**
 * メンバーのアバター（docs/requirements.md §3.5「アバター」）。Google アカウントの写真を表示し、
 * 写真がない場合と読み込みに失敗した場合は、名前の頭文字を primaryContainer の円に表示する。
 * 白っぽい写真や頭文字の円が背景に溶けないよう、写真の有無にかかわらず 1dp の縁を付ける。
 * 読み上げは呼び出し側で名前を付ける
 */
@Composable
fun Avatar(name: String, photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            // 縁は中身（写真）の上に描かれる
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // 写真の下に頭文字を置いておき、写真がなければそのまま見えるようにする
        AvatarInitial.of(name)?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                // 円の大きさに合わせる（32dp で 14sp）
                fontSize = (size.value * 0.44f).sp,
            )
        }
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 上部バーなどで使うアバターの大きさ */
val AvatarSmall = 32.dp
