package com.cheesecomer.cartmate.ui.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing

// 商品名の管理・商品・購入履歴・商品をまとめるの画面で共通の部品（docs/design/screens/item_*）

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** 欄の見出し。カードの外に小さく薄く置く（中身はカードで区切る） */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = CartMateSpacing.large + CartMateSpacing.xSmall, end = CartMateSpacing.large, top = CartMateSpacing.xLarge, bottom = CartMateSpacing.small),
    )
}

/** 商品の画面と購入履歴の、枠線のある白いカード */
@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = CARD_SHAPE,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth().padding(horizontal = CartMateSpacing.large),
    ) {
        Column(content = content)
    }
}

/** カードの中の行の区切り。左右に余白を空ける */
@Composable
internal fun CardDivider() {
    HorizontalDivider(Modifier.padding(horizontal = CartMateSpacing.large), color = MaterialTheme.colorScheme.surfaceContainer)
}

/**
 * LazyColumn の行を並べて、1枚の白いカードに見せる（商品名の管理と商品をまとめるの一覧）。
 * 最初の行の上と最後の行の下だけを丸める
 */
@Composable
internal fun Modifier.listCardSegment(first: Boolean, last: Boolean): Modifier {
    val radius = LIST_CARD_RADIUS
    return padding(horizontal = CartMateSpacing.large)
        .clip(RoundedCornerShape(topStart = if (first) radius else 0.dp, topEnd = if (first) radius else 0.dp, bottomStart = if (last) radius else 0.dp, bottomEnd = if (last) radius else 0.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
}

/** 丸い検索欄。文字があれば、右端のボタンで消せる */
@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.small),
    )
}

@Composable
internal fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(CartMateSpacing.xLarge),
    )
}

@Composable
internal fun Chevron() {
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val CARD_SHAPE = RoundedCornerShape(20.dp)
private val LIST_CARD_RADIUS = 28.dp
