package com.cheesecomer.cartmate.ui.family

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.family.InviteCode
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.ui.components.Avatar
import com.cheesecomer.cartmate.ui.components.cardFrame
import com.cheesecomer.cartmate.ui.join.JoinLinks
import com.cheesecomer.cartmate.ui.signin.GoogleSignIn
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun FamilyScreen(
    onBack: () -> Unit,
    onJoinWithCode: () -> Unit,
    onDeleteAccount: () -> Unit,
    viewModel: FamilyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.family_action_failed)
    val offline = stringResource(R.string.offline_unavailable)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FamilyEvent.ShareInvite -> {
                    val text = resources.getString(R.string.family_invite_share, JoinLinks.url(event.code), event.code.formatted())
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null))
                }
                FamilyEvent.Failed -> snackbar.showSnackbar(failed)
                FamilyEvent.Offline -> snackbar.showSnackbar(offline)
            }
        }
    }
    FamilyContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onInvite = viewModel::invite,
        onRevoke = viewModel::revokeInvite,
        onRemove = viewModel::removeMember,
        onJoinWithCode = onJoinWithCode,
        onLeave = viewModel::leave,
        onSignOut = {
            scope.launch { GoogleSignIn.clear(context) }
            viewModel.signOut()
        },
        onDeleteAccount = onDeleteAccount,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyContent(
    state: FamilyUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onRevoke: (InviteCode) -> Unit,
    onRemove: (UserId) -> Unit,
    onJoinWithCode: () -> Unit,
    onLeave: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var removing by remember { mutableStateOf<Member?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.family_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.small),
        ) {
            state.account?.let { account ->
                SectionLabel(stringResource(R.string.family_account))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardFrame()
                        .padding(CartMateSpacing.large),
                ) {
                    Avatar(account.displayName, account.photoUrl, size = 48.dp)
                    Spacer(Modifier.width(CartMateSpacing.large))
                    Column {
                        Text(account.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(account.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (state.solo) {
                Spacer(Modifier.height(CartMateSpacing.xLarge))
                MergeCard(onJoinWithCode)
            } else {
                SectionLabel(stringResource(R.string.family_members, state.members.size))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .cardFrame(),
                ) {
                    state.members.forEachIndexed { index, member ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = CartMateSpacing.large), color = MaterialTheme.colorScheme.surfaceContainer)
                        MemberRow(
                            member = member,
                            isMe = member.userId == state.account?.userId,
                            canRemove = state.isOwner && member.userId != state.account?.userId,
                            onRemove = { removing = member },
                        )
                    }
                }
            }

            SectionLabel(stringResource(if (state.solo) R.string.family_invite_solo_section else R.string.family_invite_section))
            Column(
                Modifier
                    .fillMaxWidth()
                    .cardFrame()
                    .padding(CartMateSpacing.large),
            ) {
                Button(
                    onClick = onInvite,
                    enabled = !state.busy,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(CartMateSpacing.small))
                    Text(stringResource(R.string.family_invite), style = MaterialTheme.typography.titleMedium)
                }
                state.invites.forEach { invite ->
                    Spacer(Modifier.height(CartMateSpacing.large))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    Spacer(Modifier.height(CartMateSpacing.large))
                    InviteRow(invite, enabled = !state.busy, onRevoke = { onRevoke(invite.code) })
                }
            }

            Spacer(Modifier.height(CartMateSpacing.xLarge))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(CartMateSpacing.small))
            if (!state.solo) {
                BottomAction(stringResource(R.string.family_leave), destructive = true) { confirmLeave = true }
            }
            BottomAction(stringResource(R.string.action_sign_out), destructive = false, onClick = onSignOut)
            BottomAction(stringResource(R.string.family_delete_account), destructive = true) { confirmDelete = true }
            Spacer(Modifier.height(CartMateSpacing.xLarge))
        }
    }

    removing?.let { member ->
        ConfirmDialog(
            title = stringResource(R.string.family_remove_title, member.displayName),
            message = stringResource(R.string.family_remove_message),
            confirm = stringResource(R.string.family_remove_confirm),
            onConfirm = {
                removing = null
                onRemove(member.userId)
            },
            onDismiss = { removing = null },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.family_leave_title),
            message = stringResource(R.string.family_leave_message),
            confirm = stringResource(R.string.family_leave_confirm),
            onConfirm = {
                confirmLeave = false
                onLeave()
            },
            onDismiss = { confirmLeave = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.family_delete_title),
            message = stringResource(R.string.family_delete_message),
            confirm = stringResource(R.string.family_delete_confirm),
            onConfirm = {
                confirmDelete = false
                onDeleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CartMateSpacing.xLarge, bottom = CartMateSpacing.small),
    )
}

@Composable
private fun MemberRow(member: Member, isMe: Boolean, canRemove: Boolean, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.small),
    ) {
        Avatar(member.displayName, member.photoUrl, size = 40.dp)
        Spacer(Modifier.width(CartMateSpacing.large))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.small),
            modifier = Modifier.weight(1f),
        ) {
            Text(member.displayName, style = MaterialTheme.typography.titleMedium)
            if (member.role == MemberRole.OWNER) OwnerBadge()
        }
        when {
            isMe -> Text(stringResource(R.string.family_me), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            canRemove -> {
                val description = stringResource(R.string.family_remove_description, member.displayName)
                TextButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = description }) {
                    Text(stringResource(R.string.family_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun OwnerBadge() {
    Text(
        stringResource(R.string.family_owner),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = CartMateSpacing.medium, vertical = CartMateSpacing.xSmall),
    )
}

@Composable
private fun InviteRow(invite: ActiveInvite, enabled: Boolean, onRevoke: () -> Unit) {
    Column {
        Text(stringResource(R.string.family_invite_codes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(CartMateSpacing.small))
        Text(
            invite.code.formatted(),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.family_invite_remaining, invite.remainingHours),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRevoke, enabled = enabled) {
                Text(stringResource(R.string.family_invite_revoke), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MergeCard(onJoinWithCode: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .cardFrame()
            .padding(CartMateSpacing.large),
    ) {
        Text(stringResource(R.string.family_merge_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(CartMateSpacing.small))
        Text(stringResource(R.string.family_merge_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(CartMateSpacing.large))
        OutlinedButton(
            onClick = onJoinWithCode,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(stringResource(R.string.family_merge_action), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BottomAction(text: String, destructive: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
    }
}

/** 破壊的な操作の確認。確定ボタンは error 色にする（docs/design/ui.md「家族とアカウント」） */
@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun FamilyPreview() {
    val taro = UserId("taro")
    CartMateTheme {
        FamilyContent(
            state = FamilyUiState(
                account = Account(taro, "山田 太郎", "taro.yamada@gmail.com", null),
                members = listOf(
                    Member(taro, MemberRole.OWNER, "山田 太郎", null, Instant.EPOCH),
                    Member(UserId("hanako"), MemberRole.MEMBER, "山田 花子", null, Instant.EPOCH),
                ),
                invites = listOf(ActiveInvite(InviteCode.parse("7KQ2M9XPHA")!!, 23)),
            ),
            snackbar = SnackbarHostState(),
            onBack = {}, onInvite = {}, onRevoke = {}, onRemove = {}, onJoinWithCode = {},
            onLeave = {}, onSignOut = {}, onDeleteAccount = {},
        )
    }
}
