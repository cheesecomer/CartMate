package com.cheesecomer.cartmate.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    /** アカウントの選択からログインが終わるまで。ボタンを押せなくする */
    val signingIn: Boolean = false,
    val failed: Boolean = false,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /** Google のアカウント選択を始める */
    fun onStart() {
        _uiState.update { it.copy(signingIn = true, failed = false) }
    }

    /** Google のアカウント選択の結果。ログインできたら、表示する画面は CartMateNavHost が切り替える */
    fun onResult(result: GoogleSignIn.Result) {
        when (result) {
            is GoogleSignIn.Result.Success -> viewModelScope.launch {
                runCatching { accountRepository.signInWithGoogle(result.idToken) }
                    .onSuccess { _uiState.update { it.copy(signingIn = false) } }
                    .onFailure { _uiState.update { it.copy(signingIn = false, failed = true) } }
            }
            GoogleSignIn.Result.Cancelled -> _uiState.update { it.copy(signingIn = false) }
            is GoogleSignIn.Result.Failed -> _uiState.update { it.copy(signingIn = false, failed = true) }
        }
    }
}
