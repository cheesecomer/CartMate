package com.cheesecomer.cartmate.testing

/**
 * ソフトキーボードの表示のタイミングに依存する UI テスト。キーボードの起動が遅い端末（起動したばかりの
 * エミュレータや、テストを続けて実行して遅くなったエミュレータ）では失敗することがあるため、通常の実行からは外す。
 * 落ち着いた端末で `./gradlew :app:connectedDebugAndroidTest -PkeyboardTimingTests` で実行する（docs/testing.md）
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KeyboardTimingTest
