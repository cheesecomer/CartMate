package com.cheesecomer.cartmate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing

/**
 * 2つ以上から1つを選ぶ選択肢のカード（公開範囲の選択）。選んでいるものは primary の枠と primaryContainer の背景で示す。
 * [enabled] が false なら、選んでいる値を表示するだけで変えられない
 */
@Composable
fun SelectableOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.medium
    val colors = MaterialTheme.colorScheme
    val contentColor = if (selected) colors.primary else colors.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerLow)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = CartMateSpacing.medium, vertical = CartMateSpacing.medium),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(CartMateSpacing.small))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) colors.primary else colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = CartMateSpacing.xSmall),
            )
        }
    }
}
