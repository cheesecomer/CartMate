package com.cheesecomer.cartmate.ui.signin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.TextButton
import com.cheesecomer.cartmate.FirebaseEmulator
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.ui.PRIVACY_POLICY_URL
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SignInContent(
        state = state,
        onSignIn = {
            viewModel.onStart()
            scope.launch { viewModel.onResult(GoogleSignIn.requestIdToken(context)) }
        },
        // エミュレータにつないだデバッグ版だけ（Google のログインはエミュレータでは使えない）
        onSignInTestUser = if (FirebaseEmulator.enabled && FirebaseEmulator.testUserSignInVisible) {
            { scope.launch { FirebaseEmulator.signInTestUser() } }
        } else {
            null
        },
    )
}

@Composable
fun SignInContent(state: SignInUiState, onSignIn: () -> Unit, onSignInTestUser: (() -> Unit)? = null) {
    val uriHandler = LocalUriHandler.current
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // 文字サイズ 200% ではみ出してもスクロールでき、収まるときは画面の中央に置く
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CartMateSpacing.xLarge, vertical = CartMateSpacing.xLarge),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Spacer(Modifier.height(CartMateSpacing.xLarge))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(CartMateSpacing.small))
            Text(
                text = stringResource(R.string.signin_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            OutlinedButton(
                onClick = onSignIn,
                enabled = !state.signingIn,
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
                Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(CartMateSpacing.medium))
                Text(stringResource(R.string.signin_google), style = MaterialTheme.typography.titleMedium)
            }
            onSignInTestUser?.let {
                Spacer(Modifier.height(CartMateSpacing.medium))
                TextButton(onClick = it) { Text(stringResource(R.string.signin_test_user)) }
            }
            if (state.failed) {
                Spacer(Modifier.height(CartMateSpacing.large))
                Text(
                    text = stringResource(R.string.signin_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            // ログインする前に読めるようにする（docs/requirements.md §3.5「プライバシーポリシー」）
            Spacer(Modifier.height(CartMateSpacing.large))
            TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) {
                Text(
                    text = stringResource(R.string.privacy_policy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInPreview() {
    CartMateTheme { SignInContent(SignInUiState(), onSignIn = {}) }
}
