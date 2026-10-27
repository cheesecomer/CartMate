package com.cheesecomer.cartmate.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.data.connectivity.ConnectivityObserver
import com.cheesecomer.cartmate.domain.family.DisplayName
import com.cheesecomer.cartmate.domain.family.InviteCode
import com.cheesecomer.cartmate.domain.family.InviteValidation
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.Invite
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.repository.AccountRepository
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
import java.time.Clock
import javax.inject.Inject

data class FamilyUiState(
    val account: Account? = null,
    val members: List<Member> = emptyList(),
    /** 自分がほかのメンバーに付けた呼び名 */
    val aliases: Map<UserId, String> = emptyMap(),
    /** 有効な招待コードと、期限までの残り時間 */
    val invites: List<ActiveInvite> = emptyList(),
    val online: Boolean = true,
    /** 招待の発行など、通信の終わりを待っている間 */
    val busy: Boolean = false,
) {
    val isOwner: Boolean get() = members.any { it.userId == account?.userId && it.role == MemberRole.OWNER }

    /** 自分しかいない家族。ほかの家族への合流を表示し、「家族から抜ける」は表示しない */
    val solo: Boolean get() = members.none { it.userId != account?.userId }

    /** 自分の members。家族の作成の直後などで、まだ読めていなければ null */
    val me: Member? get() = members.find { it.userId == account?.userId }

    /** 自分の表示名（設定していなければ Google アカウントの名前） */
    val myName: String get() = me?.let { DisplayName.of(it, alias = null) } ?: account?.displayName.orEmpty()

    /** 自分の画面に出す [member] の名前（docs/requirements.md §3.5「表示名と呼び名」） */
    fun nameOf(member: Member): String =
        DisplayName.of(member, alias = aliases[member.userId].takeIf { member.userId != account?.userId }) ?: member.displayName
}

data class ActiveInvite(val code: InviteCode, val remainingHours: Long)

sealed interface FamilyEvent {
    /** 共有シートで招待を送る */
    data class ShareInvite(val code: InviteCode) : FamilyEvent
    data object Failed : FamilyEvent
    data object Offline : FamilyEvent
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val householdRepository: HouseholdRepository,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
) : ViewModel() {

    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<FamilyUiState> = combine(
        accountRepository.observeAccount(),
        householdRepository.observeMembers().combine(householdRepository.observeAliases(), ::Pair),
        householdRepository.observeInvites(),
        connectivity.online,
        busy,
    ) { account, (members, aliases), invites, online, busy ->
        val now = clock.instant()
        FamilyUiState(
            account = account,
            members = members,
            aliases = aliases,
            invites = InviteValidation.active(invites, now).map { ActiveInvite(it.code, InviteValidation.remainingHours(it, now)) },
            online = online,
            busy = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FamilyUiState())

    private val _events = Channel<FamilyEvent>(Channel.BUFFERED)
    val events: Flow<FamilyEvent> = _events.receiveAsFlow()

    /** 招待を発行して、共有シートで送る */
    fun invite() = online {
        // 参加する人はまだ呼び名を付けていないので、自分の表示名を出す
        val invite: Invite = householdRepository.createInvite(inviterName = uiState.value.myName)
        _events.send(FamilyEvent.ShareInvite(invite.code))
    }

    /** 自分の表示名を設定する。空にすると解除。オフラインでも行える */
    fun setNickname(input: String) {
        if (!DisplayName.isValid(input)) return
        val googleName = uiState.value.account?.displayName.orEmpty()
        viewModelScope.launch { householdRepository.setNickname(DisplayName.nicknameToSave(input, googleName)) }
    }

    /** [userId] に自分だけの呼び名を付ける。空にすると解除。オフラインでも行える */
    fun setAlias(userId: UserId, input: String) {
        if (!DisplayName.isValid(input)) return
        viewModelScope.launch { householdRepository.setAlias(userId, DisplayName.normalize(input)) }
    }

    fun revokeInvite(code: InviteCode) = online { householdRepository.revokeInvite(code) }

    fun removeMember(userId: UserId) = online { householdRepository.removeMember(userId) }

    /** 抜けると所属が変わり、表示する画面は CartMateApp が切り替える */
    fun leave() = online { householdRepository.leave() }

    fun signOut() {
        viewModelScope.launch { accountRepository.signOut() }
    }

    /** オンラインのときだけ行える操作（docs/requirements.md §3.3）。失敗したら知らせる */
    private fun online(action: suspend () -> Unit) {
        if (busy.value) return
        if (!uiState.value.online) {
            _events.trySend(FamilyEvent.Offline)
            return
        }
        busy.update { true }
        viewModelScope.launch {
            runCatching { action() }.onFailure { _events.send(FamilyEvent.Failed) }
            busy.update { false }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
