package com.cheesecomer.cartmate.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.repository.AccountRepository
import com.cheesecomer.cartmate.domain.repository.HouseholdRepository
import com.cheesecomer.cartmate.ui.PendingNavigation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val account: Account? = null,
    /** 家族を作成している間。カードを押せなくする */
    val creating: Boolean = false,
    val createFailed: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val householdRepository: HouseholdRepository,
    private val pendingNavigation: PendingNavigation,
) : ViewModel() {

    private val progress = MutableStateFlow(OnboardingUiState())

    val uiState: StateFlow<OnboardingUiState> =
        combine(accountRepository.observeAccount(), progress) { account, progress -> progress.copy(account = account) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), OnboardingUiState())

    fun createHousehold() {
        if (progress.value.creating) return
        progress.update { it.copy(creating = true, createFailed = false) }
        viewModelScope.launch {
            val account = accountRepository.observeAccount().filterNotNull().first()
            // 作成すると所属が変わってこの画面が閉じるので、家族画面を開く予約を先にしておく（招待を促す）
            pendingNavigation.requestFamilyScreen()
            runCatching { householdRepository.createHousehold(account) }
                .onSuccess { progress.update { it.copy(creating = false) } }
                .onFailure {
                    pendingNavigation.cancelFamilyScreen()
                    progress.update { it.copy(creating = false, createFailed = true) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch { accountRepository.signOut() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
