package com.cheesecomer.cartmate.ui.join

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.ui.components.Avatar
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import com.cheesecomer.cartmate.ui.theme.CartMateTheme

@Composable
fun JoinConfirmScreen(onClose: () -> Unit, viewModel: JoinConfirmViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    JoinConfirmContent(state, onClose, viewModel::confirm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinConfirmContent(state: JoinConfirmUiState, onClose: () -> Unit, onConfirm: () -> Unit) {
    val submitting = (state as? JoinConfirmUiState.Ready)?.submitting == true
    // 参加・合流の途中で戻ると、合流が途中で止まるため、戻る操作を受け付けない
    BackHandler(enabled = submitting) {}
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.join_confirm_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !submitting) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                JoinConfirmUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is JoinConfirmUiState.Ready -> Ready(state, onClose, onConfirm)
                is JoinConfirmUiState.Unavailable -> Unavailable(state.reason, onClose)
            }
        }
    }
}

@Composable
private fun Ready(state: JoinConfirmUiState.Ready, onClose: () -> Unit, onConfirm: () -> Unit) {
    Body {
        Avatar(name = state.inviterName, photoUrl = state.inviterPhotoUrl, size = 72.dp)
        Spacer(Modifier.height(CartMateSpacing.xLarge))
        Title(stringResource(R.string.join_confirm_question, state.inviterName))
        Spacer(Modifier.height(CartMateSpacing.small))
        Message(
            if (state.mergeListCount != null) {
                stringResource(R.string.join_confirm_merge_message, state.mergeListCount, state.inviterName)
            } else {
                stringResource(R.string.join_confirm_message)
            },
        )
        Spacer(Modifier.height(CartMateSpacing.xLarge))
        when {
            state.progress != null -> Message(stringResource(R.string.join_merging, state.progress.first, state.progress.second))
            !state.online -> Message(stringResource(R.string.offline_unavailable), error = true)
            state.failed -> Message(stringResource(R.string.join_failed), error = true)
        }
        Spacer(Modifier.height(CartMateSpacing.large))
        Button(
            onClick = onConfirm,
            enabled = !state.submitting && state.online,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(
                stringResource(if (state.mergeListCount != null) R.string.join_confirm_merge else R.string.join_confirm_join),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(CartMateSpacing.medium))
        SecondaryButton(stringResource(R.string.action_cancel), enabled = !state.submitting, onClick = onClose)
    }
}

@Composable
private fun Unavailable(reason: JoinConfirmUiState.Reason, onClose: () -> Unit) {
    val (title, message) = when (reason) {
        JoinConfirmUiState.Reason.NotFound -> R.string.join_not_found to R.string.join_not_found_message
        JoinConfirmUiState.Reason.Expired -> R.string.join_expired to R.string.join_expired_message
        JoinConfirmUiState.Reason.Used -> R.string.join_used to R.string.join_used_message
        JoinConfirmUiState.Reason.AlreadyInAnotherFamily -> R.string.join_already_member to R.string.join_already_member_message
        JoinConfirmUiState.Reason.AlreadyInThisFamily -> R.string.join_already_this_family to null
    }
    Body {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(CartMateSpacing.xLarge))
        Title(stringResource(title))
        if (message != null) {
            Spacer(Modifier.height(CartMateSpacing.small))
            Message(stringResource(message))
        }
        Spacer(Modifier.height(CartMateSpacing.xLarge))
        Button(
            onClick = onClose,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(stringResource(R.string.action_close), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Body(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CartMateSpacing.large, vertical = 48.dp),
    ) { content() }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
}

@Composable
private fun Message(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SecondaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun JoinConfirmPreview() {
    CartMateTheme {
        JoinConfirmContent(JoinConfirmUiState.Ready("花子", null, mergeListCount = 3), {}, {})
    }
}
