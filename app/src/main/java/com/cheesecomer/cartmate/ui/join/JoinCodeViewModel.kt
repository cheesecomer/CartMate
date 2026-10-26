package com.cheesecomer.cartmate.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.data.connectivity.ConnectivityObserver
import com.cheesecomer.cartmate.domain.family.InviteCode
import com.cheesecomer.cartmate.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinCodeUiState(
    /** 入力欄に表示する文字列（「7KQ2-M9XP-HA」のように区切る） */
    val text: String = "",
    val searching: Boolean = false,
    val error: JoinCodeError? = null,
    val online: Boolean = true,
) {
    /** 10文字そろったら「次へ」を押せる */
    val code: InviteCode? get() = InviteCode.parse(text)
}

enum class JoinCodeError { NotFound, Offline, Failed }

@HiltViewModel
class JoinCodeViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val input = MutableStateFlow(JoinCodeUiState())

    val uiState: StateFlow<JoinCodeUiState> = combine(input, connectivity.online) { state, online -> state.copy(online = online) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), JoinCodeUiState())

    private val _found = Channel<InviteCode>(Channel.BUFFERED)
    /** 招待が見つかった。参加の確認へ進む（期限切れ・使用済みは、確認の画面で伝える） */
    val found: Flow<InviteCode> = _found.receiveAsFlow()

    fun onTextChange(text: String) {
        input.update { it.copy(text = InviteCode.formatPartial(text), error = null) }
    }

    fun onNext() {
        val state = uiState.value
        val code = state.code ?: return
        if (state.searching) return
        if (!state.online) {
            input.update { it.copy(error = JoinCodeError.Offline) }
            return
        }
        input.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            runCatching { householdRepository.findInvite(code) }
                .onSuccess { invite ->
                    input.update { it.copy(searching = false, error = if (invite == null) JoinCodeError.NotFound else null) }
                    if (invite != null) _found.send(code)
                }
                .onFailure { input.update { it.copy(searching = false, error = JoinCodeError.Failed) } }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
