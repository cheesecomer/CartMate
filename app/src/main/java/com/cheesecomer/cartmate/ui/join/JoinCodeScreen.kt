package com.cheesecomer.cartmate.ui.join

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.family.InviteCode
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import com.cheesecomer.cartmate.ui.theme.CartMateTheme

@Composable
fun JoinCodeScreen(
    onBack: () -> Unit,
    onFound: (InviteCode) -> Unit,
    viewModel: JoinCodeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.found.collect(onFound) }
    JoinCodeContent(state, onBack, viewModel::onTextChange, viewModel::onNext)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinCodeContent(
    state: JoinCodeUiState,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.join_code_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.xLarge),
        ) {
            val error = state.error ?: JoinCodeError.Offline.takeIf { !state.online }
            OutlinedTextField(
                // 区切りを入れても、カーソルは常に末尾に置く
                value = TextFieldValue(state.text, TextRange(state.text.length)),
                onValueChange = { onTextChange(it.text) },
                label = { Text(stringResource(R.string.join_code_label)) },
                singleLine = true,
                isError = error == JoinCodeError.NotFound,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CartMateSpacing.small))
            when (error) {
                JoinCodeError.NotFound -> ErrorText(
                    title = stringResource(R.string.join_not_found),
                    message = stringResource(R.string.join_not_found_message),
                )
                JoinCodeError.Offline -> ErrorText(title = stringResource(R.string.offline_unavailable), message = null)
                JoinCodeError.Failed -> ErrorText(title = stringResource(R.string.join_failed), message = null)
                null -> Text(
                    stringResource(R.string.join_code_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(CartMateSpacing.xLarge))
            Button(
                onClick = onNext,
                enabled = state.code != null && !state.searching && state.online,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.join_code_next), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ErrorText(title: String, message: String?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(CartMateSpacing.xSmall))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JoinCodePreview() {
    CartMateTheme {
        JoinCodeContent(JoinCodeUiState(text = "7KQ2-M9XP-HA", error = JoinCodeError.NotFound), {}, {}, {})
    }
}
