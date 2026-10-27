package com.cheesecomer.cartmate.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import kotlinx.serialization.Serializable

@Serializable
data object ProductMergeRoute

/**
 * 商品の統合画面（docs/requirements.md §3.5「商品の統合」）。商品を選ぶと、「別の商品に統合」「統合を解除」と、
 * 辞書でまとめている商品なら「別の商品として扱う」（外していれば「辞書でまとめる」）を選べる
 */
@Composable
fun ProductMergeScreen(onBack: () -> Unit, viewModel: ProductMergeViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    ProductMergeContent(
        rows = rows,
        onBack = onBack,
        mergeTargets = viewModel::mergeTargets,
        onMerge = viewModel::merge,
        onUnmerge = viewModel::unmerge,
        onDictionaryOptOut = viewModel::setDictionaryOptOut,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductMergeContent(
    rows: List<ProductMerge.Row>,
    onBack: () -> Unit,
    mergeTargets: (ProductId) -> List<ProductMerge.Row>,
    onMerge: (from: ProductId, target: ProductId) -> Unit,
    onUnmerge: (ProductId) -> Unit,
    onDictionaryOptOut: (ProductId, Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf<ProductMerge.Row?>(null) }
    var choosingTarget by remember { mutableStateOf<ProductMerge.Row?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_merge_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        if (rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(CartMateSpacing.xLarge), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.product_merge_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.product_merge_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.small),
                )
            }
            items(rows, key = { it.product.id.value }) { row ->
                ListItem(
                    headlineContent = { Text(row.product.displayName) },
                    supportingContent = reasonText(row)?.let { { Text(it) } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .clickable(role = Role.Button) { selected = row }
                        .heightIn(min = 56.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }

    selected?.let { row ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(row.product.displayName) },
            text = {
                Column {
                    reasonText(row)?.let { Text(it, modifier = Modifier.padding(bottom = CartMateSpacing.medium)) }
                    DialogAction(stringResource(R.string.product_merge_into_other)) {
                        selected = null
                        choosingTarget = row
                    }
                    if (row.canUnmerge) {
                        DialogAction(stringResource(R.string.product_merge_unmerge)) {
                            selected = null
                            onUnmerge(row.product.id)
                        }
                    }
                    if (row.canOptOut) {
                        DialogAction(stringResource(R.string.product_merge_opt_out)) {
                            selected = null
                            onDictionaryOptOut(row.product.id, true)
                        }
                    }
                    if (row.canOptIn) {
                        DialogAction(stringResource(R.string.product_merge_opt_in)) {
                            selected = null
                            onDictionaryOptOut(row.product.id, false)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    choosingTarget?.let { row ->
        val targets = remember(row) { mergeTargets(row.product.id) }
        AlertDialog(
            onDismissRequest = { choosingTarget = null },
            title = { Text(stringResource(R.string.product_merge_choose_target, row.product.displayName)) },
            text = {
                LazyColumn {
                    items(targets, key = { it.product.id.value }) { target ->
                        DialogAction(target.product.displayName) {
                            choosingTarget = null
                            onMerge(row.product.id, target.product.id)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** 名寄せの理由。「『牛乳』に統合しています」「辞書で『卵』としてまとめています」「辞書でまとめていません」 */
@Composable
private fun reasonText(row: ProductMerge.Row): String? {
    val mergedIntoName = row.mergedIntoName
    val dictionaryName = row.dictionaryName
    return when {
        mergedIntoName != null -> stringResource(R.string.product_merge_reason_manual, mergedIntoName)
        dictionaryName != null -> stringResource(R.string.product_merge_reason_dictionary, dictionaryName)
        row.canOptIn -> stringResource(R.string.product_merge_reason_opted_out)
        else -> null
    }
}

@Composable
private fun DialogAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = CartMateSpacing.medium),
    )
}
