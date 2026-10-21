package com.cheesecomer.cartmate.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MainDispatcherExtensionTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private class CounterViewModel : ViewModel() {
        private val _count = MutableStateFlow(0)
        val count: StateFlow<Int> = _count

        fun increment() {
            viewModelScope.launch { _count.value += 1 }
        }
    }

    @Test
    fun `viewModelScope の処理がテスト用 Dispatcher で実行される`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.count.test {
            assertThat(awaitItem()).isEqualTo(0)
            viewModel.increment()
            assertThat(awaitItem()).isEqualTo(1)
        }
    }
}
