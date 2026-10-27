package com.cheesecomer.cartmate.testing

/**
 * Firebase のエミュレータ（Auth と Firestore）が必要なテスト。エミュレータを起動して `-PemulatorTests` を付けると、
 * このテストだけを実行する（docs/testing.md「エミュレータを使う結合テスト」）。通常の実行では飛ばす（EmulatorClient.assumeRequested）
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class EmulatorTest
