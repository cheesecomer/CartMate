package com.cheesecomer.cartmate.testing

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/**
 * テストの間だけ、端末の画面の大きさを [width] x [height]（px）に変える。終わったら元に戻す。
 * 画面を起動する前に変えるため、ComposeTestRule より外側（小さい order）に置く
 */
class ScreenSizeRule(private val width: Int, private val height: Int) : ExternalResource() {

    override fun before() {
        shell("wm size ${width}x$height")
        // 変更が反映される前に画面を起動すると、反映されたときに画面が作り直されてしまうので、反映を待つ
        val windowManager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(WindowManager::class.java)
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (windowManager.maximumWindowMetrics.bounds.let { it.width() != width || it.height() != height }) {
            check(SystemClock.uptimeMillis() < deadline) { "画面の大きさが ${width}x$height に変わらない" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    override fun after() = shell("wm size reset")

    private fun shell(command: String) {
        // 出力を読みきって、コマンドが終わるのを待つ
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 100L
    }
}
