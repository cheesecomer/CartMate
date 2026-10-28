package com.cheesecomer.cartmate.ui.products

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.input.formatQuantity
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import java.time.format.DateTimeFormatter

/** 「10/18」 */
internal val HISTORY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

/** 商品ごとの購入履歴（docs/design/ui.md「購入履歴」、docs/design/screens/item_history） */
@Composable
fun PurchaseHistoryScreen(
    onBack: () -> Unit,
    /** 最後の購入を削除して0件になった。商品名の管理まで戻る */
    onEmptied: () -> Unit = onBack,
    viewModel: PurchaseHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (state.emptied) {
        val message = stringResource(R.string.product_detail_gone, state.productName)
        LaunchedEffect(Unit) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onEmptied()
        }
    }
    var deleting by remember { mutableStateOf<PurchaseHistoryViewModel.Row?>(null) }
    deleting?.let { row ->
        PurchaseDeleteDialog(
            itemName = row.purchase.inputName,
            onConfirm = {
                deleting = null
                viewModel.delete(row.purchase.itemId)
            },
            onDismiss = { deleting = null },
        )
    }
    ProductScaffold(title = stringResource(R.string.purchase_history_title, state.productName), onBack = onBack) {
        if (state.loading) return@ProductScaffold
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CartMateSpacing.medium)) {
            item { InfoCard() }
            item {
                // 件数のバッジは、見出しの文字と下をそろえる
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth().padding(end = CartMateSpacing.large),
                ) {
                    SectionLabel(stringResource(R.string.purchase_history_list), Modifier.weight(1f))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(bottom = CartMateSpacing.small)) {
                        Text(
                            stringResource(R.string.purchase_history_count, state.rows.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = CartMateSpacing.medium, vertical = CartMateSpacing.xSmall),
                        )
                    }
                }
            }
            if (state.rows.isEmpty()) {
                item { Empty(stringResource(R.string.purchase_history_empty)) }
            }
            items(state.rows, key = { it.purchase.itemId.value }) { row ->
                PurchaseCard(row, viewModel::setExcluded, onDelete = { deleting = row }.takeIf { row.deletable })
            }
            item { Spacer(Modifier.height(CartMateSpacing.xLarge)) }
        }
    }
}

/** 「周期予測の対象について」。スイッチの意味を、一覧の上で伝える */
@Composable
private fun InfoCard() {
    SectionCard(Modifier.padding(top = CartMateSpacing.small)) {
        Row(Modifier.padding(CartMateSpacing.large), horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.medium)) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(CartMateSpacing.xSmall)) {
                Text(stringResource(R.string.purchase_history_info_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.purchase_history_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 購入の記録の1件。「10/27  牛乳  1個」と「スーパー・山田 花子」。予測に使わない記録は薄くし、「予測除外」と、
 * 外した人と日を添える。カードのタップかスイッチで切り替える。自分がチェックした記録だけ、削除のアイコンを出す
 */
@Composable
private fun PurchaseCard(
    row: PurchaseHistoryViewModel.Row,
    onExcluded: (ItemId, Boolean) -> Unit,
    /** 自分がチェックした購入だけ渡す */
    onDelete: (() -> Unit)?,
) {
    val purchase = row.purchase
    val amount = listOfNotNull(purchase.quantity?.let(::formatQuantity), purchase.unit).joinToString("")
    val excluded = purchase.excludedFromPrediction
    val dimmed = if (excluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    SectionCard(
        Modifier.toggleable(
            value = !excluded,
            role = Role.Switch,
            onValueChange = { use -> onExcluded(purchase.itemId, !use) },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = CartMateSpacing.large, end = CartMateSpacing.medium, top = CartMateSpacing.medium, bottom = CartMateSpacing.medium),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CartMateSpacing.xSmall)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.small)) {
                    Text(
                        listOf(HISTORY_DATE.format(row.date), purchase.inputName, amount).filter { it.isNotEmpty() }.joinToString(" "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = dimmed,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (excluded) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Text(
                                stringResource(R.string.purchase_history_excluded_chip),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = CartMateSpacing.small, vertical = CartMateSpacing.xSmall),
                            )
                        }
                    }
                }
                Text(
                    listOf(purchase.listNameSnapshot, row.checkerName ?: stringResource(R.string.former_member)).joinToString(" ・ "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (excluded) {
                    // 家族の誰がいつ外したかを残す
                    Text(
                        stringResource(
                            R.string.purchase_history_excluded,
                            row.excludedByName ?: stringResource(R.string.former_member),
                            row.excludedDate?.let(HISTORY_DATE::format).orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            onDelete?.let {
                val description = stringResource(R.string.purchase_history_delete, purchase.inputName)
                IconButton(onClick = it, modifier = Modifier.semantics { contentDescription = description }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(
                checked = !excluded,
                onCheckedChange = null,
                thumbContent = {
                    Icon(
                        painterResource(if (excluded) R.drawable.ic_close else R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/** 購入の削除の確認。家族の画面からも消え、元に戻せないことを伝える */
@Composable
private fun PurchaseDeleteDialog(itemName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchase_delete_title, itemName)) },
        text = { Text(stringResource(R.string.purchase_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
