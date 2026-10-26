package com.cheesecomer.cartmate.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.ui.components.Avatar
import com.cheesecomer.cartmate.ui.components.AvatarSmall
import com.cheesecomer.cartmate.ui.components.cardFrame
import com.cheesecomer.cartmate.ui.signin.GoogleSignIn
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import kotlinx.coroutines.launch

/** 家族の作成・参加。[removed] なら「家族から外されました」を表示する */
@Composable
fun OnboardingScreen(
    removed: Boolean,
    onJoinWithCode: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    OnboardingContent(
        state = state,
        removed = removed,
        onCreate = viewModel::createHousehold,
        onJoinWithCode = onJoinWithCode,
        onSignOut = {
            scope.launch { GoogleSignIn.clear(context) }
            viewModel.signOut()
        },
    )
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    removed: Boolean,
    onCreate: () -> Unit,
    onJoinWithCode: () -> Unit,
    onSignOut: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.large),
        ) {
            state.account?.let { AccountBar(it, onSignOut) }
            Spacer(Modifier.height(CartMateSpacing.large))
            if (removed) {
                RemovedNotice()
                Spacer(Modifier.height(20.dp))
            }
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(CartMateSpacing.xLarge))
            ChoiceCard(
                title = stringResource(R.string.onboarding_create),
                description = stringResource(R.string.onboarding_create_description),
                enabled = !state.creating,
                onClick = onCreate,
            )
            if (state.createFailed) {
                Spacer(Modifier.height(CartMateSpacing.small))
                Text(
                    text = stringResource(R.string.onboarding_create_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(CartMateSpacing.large))
            ChoiceCard(
                title = stringResource(R.string.onboarding_join),
                description = stringResource(R.string.onboarding_join_description),
                enabled = !state.creating,
                onClick = onJoinWithCode,
            )
        }
    }
}

@Composable
private fun AccountBar(account: Account, onSignOut: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Avatar(name = account.displayName, photoUrl = account.photoUrl, size = AvatarSmall)
        Spacer(Modifier.width(CartMateSpacing.medium))
        Column(Modifier.weight(1f)) {
            Text(account.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                account.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onSignOut) {
            Text(
                stringResource(R.string.action_sign_out),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 外されたことの通知。赤い警告ではなく、控えめな通知にする（docs/family-screens-spec.md 3.3） */
@Composable
private fun RemovedNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(CartMateSpacing.large),
        horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.medium),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                stringResource(R.string.onboarding_removed_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.onboarding_removed_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChoiceCard(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .cardFrame()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(CartMateSpacing.large),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(CartMateSpacing.small))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    CartMateTheme {
        OnboardingContent(
            state = OnboardingUiState(account = Account(UserId("taro"), "山田 太郎", "taro.yamada@gmail.com", null)),
            removed = true,
            onCreate = {},
            onJoinWithCode = {},
            onSignOut = {},
        )
    }
}
