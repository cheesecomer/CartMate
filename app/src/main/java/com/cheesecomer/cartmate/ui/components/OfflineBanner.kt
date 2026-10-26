package com.cheesecomer.cartmate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.ui.theme.CartMateColors
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing

/** オフラインの間、上部のバーの直下に表示するバナー（docs/design/sync.md「オンライン判定と表示」） */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CartMateColors.WarningBannerBackground)
            .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_cloud_off),
            contentDescription = null,
            tint = CartMateColors.WarningBannerText,
            modifier = Modifier.size(20.dp),
        )
        Text(
            stringResource(R.string.offline_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = CartMateColors.WarningBannerText,
            modifier = Modifier.padding(start = CartMateSpacing.small),
        )
    }
}
