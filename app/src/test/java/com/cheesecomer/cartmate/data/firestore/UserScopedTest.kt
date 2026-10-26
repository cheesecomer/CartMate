package com.cheesecomer.cartmate.data.firestore

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UserScopedTest {

    @Test
    fun `ログインした直後は、前の状態（ログインしていない）の値を流さない`() = runTest {
        // アプリの起動時はログインしていないので、共有している購読は「家族なし」を持っている。
        // ログインした直後にそれを流すと、家族に参加済みでも家族の作成・参加の画面が一瞬見える
        val uid = MutableStateFlow<String?>(null)
        val household = MutableStateFlow(UserValue<String?>(null, null))

        // ログインしてから、家族の判定が購読を始める（AppViewModel の flatMapLatest）
        uid.value = "taro"
        currentUserValue(uid, household).test {
            expectNoEvents()

            household.value = UserValue("taro", "taro-family")
            assertThat(awaitItem()).isEqualTo("taro-family")
        }
    }

    @Test
    fun `アカウントを切り替えた直後は、前のアカウントの値を流さない`() = runTest {
        val uid = MutableStateFlow<String?>("taro")
        val household = MutableStateFlow(UserValue<String?>("taro", "taro-family"))

        // hanako でログインし直してから、家族の判定が購読を始める
        uid.value = "hanako"
        currentUserValue(uid, household).test {
            expectNoEvents()

            household.value = UserValue("hanako", null)
            assertThat(awaitItem()).isNull()
        }
    }
}
