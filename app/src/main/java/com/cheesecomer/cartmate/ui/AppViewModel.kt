package com.cheesecomer.cartmate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.family.Destination
import com.cheesecomer.cartmate.domain.family.StartDestination
import com.cheesecomer.cartmate.domain.repository.AccountRepository
import com.cheesecomer.cartmate.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** ログインと所属から、表示する画面を決める（docs/design/ui.md「最初に表示する画面」） */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    accountRepository: AccountRepository,
    householdRepository: HouseholdRepository,
) : ViewModel() {

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
