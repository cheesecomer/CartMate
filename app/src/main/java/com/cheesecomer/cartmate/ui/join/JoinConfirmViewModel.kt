package com.cheesecomer.cartmate.ui.join

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cheesecomer.cartmate.data.connectivity.ConnectivityObserver
import com.cheesecomer.cartmate.domain.family.InviteCheck
import com.cheesecomer.cartmate.domain.family.InviteCode
import com.cheesecomer.cartmate.domain.family.InviteValidation
import com.cheesecomer.cartmate.domain.family.JoinMode
import com.cheesecomer.cartmate.domain.family.JoinPolicy
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.Invite
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.repository.AccountRepository
import com.cheesecomer.cartmate.domain.repository.HouseholdRepository
import com.cheesecomer.cartmate.domain.repository.JoinResult
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
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
import java.time.Clock
import javax.inject.Inject

sealed interface JoinConfirmUiState {
    data object Loading : JoinConfirmUiState

    /** 参加・合流できる。[mergeListCount] は合流で移すリストの件数（参加なら null） */
    data class Ready(
        val inviterName: String,
        val inviterPhotoUrl: String?,
        val mergeListCount: Int?,
        val submitting: Boolean = false,
        val progress: Pair<Int, Int>? = null,
        val failed: Boolean = false,
        val online: Boolean = true,
    ) : JoinConfirmUiState

    /** 参加できない。理由を表示して「閉じる」だけを置く */
    data class Unavailable(val reason: Reason) : JoinConfirmUiState

    enum class Reason { NotFound, Expired, Used, AlreadyInAnotherFamily, AlreadyInThisFamily }
}

@HiltViewModel
class JoinConfirmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val householdRepository: HouseholdRepository,
    listRepository: ShoppingListRepository,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
) : ViewModel() {

    private val code = InviteCode.parse(savedStateHandle.toRoute<JoinConfirmRoute>().code)

    private sealed interface Loaded {
        data object Loading : Loaded
        data class Found(val invite: Invite, val mode: JoinMode) : Loaded
        data class Unavailable(val reason: JoinConfirmUiState.Reason) : Loaded
    }

    private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)
    private val submit = MutableStateFlow(SubmitState())

    private data class SubmitState(val submitting: Boolean = false, val progress: Pair<Int, Int>? = null, val failed: Boolean = false)

    val uiState: StateFlow<JoinConfirmUiState> = combine(
        loaded,
        submit,
        listRepository.observeLists(),
        connectivity.online,
    ) { loaded, submit, lists, online ->
        when (loaded) {
            Loaded.Loading -> JoinConfirmUiState.Loading
            is Loaded.Unavailable -> JoinConfirmUiState.Unavailable(loaded.reason)
            is Loaded.Found -> JoinConfirmUiState.Ready(
                inviterName = loaded.invite.inviterName,
                // 参加前の人は members を読めないので、招待した人の写真は表示しない（頭文字にする）
                inviterPhotoUrl = null,
                mergeListCount = (loaded.mode as? JoinMode.Merge)?.let { lists.count { it.status == ListStatus.ACTIVE } },
                submitting = submit.submitting,
                progress = submit.progress,
                failed = submit.failed,
                online = online,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), JoinConfirmUiState.Loading)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val code = code ?: run {
            loaded.value = Loaded.Unavailable(JoinConfirmUiState.Reason.NotFound)
            return
        }
        val invite = runCatching { householdRepository.findInvite(code) }.getOrNull()
        loaded.value = when (val check = InviteValidation.check(invite, clock.instant())) {
            InviteCheck.NotFound -> Loaded.Unavailable(JoinConfirmUiState.Reason.NotFound)
            InviteCheck.Expired -> Loaded.Unavailable(JoinConfirmUiState.Reason.Expired)
            InviteCheck.Used -> Loaded.Unavailable(JoinConfirmUiState.Reason.Used)
            is InviteCheck.Valid -> {
                val account = accountRepository.observeAccount().filterNotNull().first()
                val membership = householdRepository.observeMembership().first()
                // 外された人は、古い家族が所属に残っていても、家族に参加していないとして扱う
                val current = membership.householdId.takeIf { membership.isMember == true }
                val members = if (current == null) emptyList() else householdRepository.observeMembers().first()
                when (val mode = JoinPolicy.decide(account.userId, current, members, check.invite.householdId)) {
                    JoinMode.AlreadyInAnotherFamily -> Loaded.Unavailable(JoinConfirmUiState.Reason.AlreadyInAnotherFamily)
                    JoinMode.AlreadyInThisFamily -> Loaded.Unavailable(JoinConfirmUiState.Reason.AlreadyInThisFamily)
                    else -> Loaded.Found(check.invite, mode)
                }
            }
        }
    }

    /** 参加・合流する。終わると所属が変わり、表示する画面は CartMateApp が切り替える */
    fun confirm() {
        val found = loaded.value as? Loaded.Found ?: return
        val state = uiState.value as? JoinConfirmUiState.Ready ?: return
        if (state.submitting || !state.online) return
        submit.value = SubmitState(submitting = true)
        viewModelScope.launch {
            val account: Account = accountRepository.observeAccount().filterNotNull().first()
            val result = when (val mode = found.mode) {
                is JoinMode.Merge -> householdRepository.merge(found.invite, account, mode.from) { done, total ->
                    submit.update { it.copy(progress = done to total) }
                }
                else -> householdRepository.join(found.invite, account)
            }
            when (result) {
                JoinResult.Joined -> Unit
                // 確認しているあいだに、ほかの人が使った・期限が切れた
                JoinResult.InviteUnavailable -> {
                    submit.value = SubmitState()
                    load()
                }
                is JoinResult.Failed -> submit.value = SubmitState(failed = true)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
