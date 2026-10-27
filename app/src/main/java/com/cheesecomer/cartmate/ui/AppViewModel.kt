package com.cheesecomer.cartmate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.family.Destination
import com.cheesecomer.cartmate.domain.family.StartDestination
import com.cheesecomer.cartmate.domain.repository.AccountRepository
import com.cheesecomer.cartmate.domain.repository.HouseholdRepository
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ログインと所属から、表示する画面を決める（docs/design/ui.md「最初に表示する画面」） */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    householdRepository: HouseholdRepository,
    productRepository: ProductRepository,
    /** 画面の切り替えをまたいで渡す遷移の予約 */
    val pendingNavigation: PendingNavigation,
) : ViewModel() {

    private val _deleteFailed = Channel<Unit>(Channel.BUFFERED)
    /** アカウントの削除に失敗した */
    val deleteFailed: Flow<Unit> = _deleteFailed.receiveAsFlow()

    /** 商品の統合や解除がサーバーで拒否された。どの画面にいても知らせる（docs/design/sync.md「商品の統合の競合」） */
    val mergeRejected: Flow<Unit> = productRepository.observeRejectedMergeChanges()

    /** [idToken] は直前に再ログインして取得した Google の ID トークン。削除するとログイン画面に戻る */
    fun deleteAccount(idToken: String) {
        viewModelScope.launch {
            runCatching { accountRepository.deleteAccount(idToken) }.onFailure { _deleteFailed.send(Unit) }
        }
    }

    init {
        // 途中で止まった合流があれば、ログインしたときに続きから終える
        viewModelScope.launch {
            accountRepository.observeAccount().filterNotNull().collect {
                runCatching { householdRepository.resumePendingMerge() }
            }
        }
        // 前回アプリを終了するまでに確定しなかった商品の統合や解除が、拒否されていないかを確かめる
        viewModelScope.launch {
            accountRepository.observeAccount().filterNotNull().first()
            runCatching { productRepository.verifyPendingMergeChanges() }
        }
    }

    val destination: StateFlow<Destination> = accountRepository.observeAccount()
        .map { it != null }
        .distinctUntilChanged()
        .flatMapLatest { signedIn ->
            if (!signedIn) flowOf(Destination.Login)
            else householdRepository.observeMembership().map {
                StartDestination.of(signedIn = true, householdId = it.householdId, isMember = it.isMember)
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Destination.Loading)
}
