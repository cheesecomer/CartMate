package com.cheesecomer.cartmate.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import kotlinx.coroutines.delay

/**
 * 入力欄のあるボトムシート。キーボードと一緒に画面の下からせり上がり、キーボードの上端に付いて止まる
 * （docs/design/ui.md「部品」）。開くと [focusRequester] の入力欄にフォーカスしてキーボードを出す。
 * キーボードの動きに合わせて1フレームずつ動かすため、ModalBottomSheet（別のウィンドウ）ではなく、
 * 呼び出し側の画面の上に重ねて表示する（画面全体を覆う Box の中に置く）。
 *
 * @param onDismiss 幕のタップ、戻るキー、下へのドラッグで閉じたとき。フォーカスは外してから呼ぶ
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardSheet(
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current

    // シートはキーボードが出始めるまで画面の下に隠しておき、出始めたらキーボードの進み具合に合わせて
    // 下からせり上げる（キーボードより先には出ない）。一定時間たってもキーボードが出始めない端末
    // （物理キーボードなど）では、キーボードなしでシートだけを出す
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeTarget = WindowInsets.imeAnimationTarget.getBottom(density)
    val navigationBarsBottom = WindowInsets.navigationBars.getBottom(density)
    var appearance by remember { mutableStateOf(SheetAppearance.WAITING) }
    val currentImeTarget by rememberUpdatedState(imeTarget)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        delay(KEYBOARD_WAIT_MILLIS)
        if (appearance == SheetAppearance.WAITING && currentImeTarget == 0) {
            appearance = SheetAppearance.WITHOUT_KEYBOARD
        }
        // キーボードが出始めたまま出きらない場合でも、シートが出ないままにはしない
        delay(KEYBOARD_GIVE_UP_MILLIS - KEYBOARD_WAIT_MILLIS)
        if (appearance == SheetAppearance.WAITING) appearance = SheetAppearance.WITHOUT_KEYBOARD
    }
    // キーボードが出きったら、以後はキーボードの上端に付いたままにする（キーボードを閉じても隠さない）
    LaunchedEffect(imeBottom, imeTarget) {
        if (appearance == SheetAppearance.WAITING && imeTarget > 0 && imeBottom == imeTarget) {
            appearance = SheetAppearance.WITH_KEYBOARD
        }
    }
    val withoutKeyboard by animateFloatAsState(
        if (appearance == SheetAppearance.WITHOUT_KEYBOARD) 1f else 0f,
        label = "sheet",
    )
    // 0 で画面の下に隠れ、1 でキーボード（なければ画面）の下端に付く
    val shown = when (appearance) {
        SheetAppearance.WAITING -> if (imeTarget > 0) imeBottom.toFloat() / imeTarget else 0f
        SheetAppearance.WITH_KEYBOARD -> 1f
        SheetAppearance.WITHOUT_KEYBOARD -> withoutKeyboard
    }
    // 下へのドラッグで閉じる
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val dismiss = {
        focusManager.clearFocus()
        onDismiss()
    }
    BackHandler(onBack = dismiss)

    Box(Modifier.fillMaxSize()) {
        // 背面の画面を覆う幕。タップで閉じる
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown }
                .background(colors.scrim.copy(alpha = SCRIM_ALPHA))
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                // 出す前は画面の下に隠しておく（入力欄はフォーカスできるよう、配置はしておく）。
                // シートの下端はキーボードかナビゲーションバーの上にあるので、自分の高さとその高い方の分
                // （と余白）だけ下げると、画面の外に出る
                .graphicsLayer {
                    val bottomInset = maxOf(imeBottom, navigationBarsBottom)
                    val hiddenOffset = size.height + bottomInset + with(density) { 48.dp.toPx() }
                    translationY = (1 - shown) * hiddenOffset + dragOffset
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) },
                    onDragStopped = {
                        if (dragOffset > with(density) { DRAG_DISMISS_DISTANCE.toPx() }) dismiss() else dragOffset = 0f
                    },
                ),
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
            color = colors.surfaceContainerLowest,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // つまみ
                Box(
                    Modifier
                        .padding(vertical = CartMateSpacing.large)
                        .size(width = 32.dp, height = 4.dp)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                )
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}

private enum class SheetAppearance {
    /** キーボードが出始めるのを待っている、またはキーボードと一緒にせり上がっている */
    WAITING,

    /** キーボードが出きって、その上端に付いている */
    WITH_KEYBOARD,

    /** キーボードが出なかったので、シートだけを出した */
    WITHOUT_KEYBOARD,
}

/**
 * キーボードが出始めるのを待つ最長の時間。キーボードの起動が遅い端末でもシートが先に出ないよう長めにする
 * （物理キーボードの端末では、シートが出るまでこの時間待つことになる）
 */
private const val KEYBOARD_WAIT_MILLIS = 1_500L

/** キーボードが出きるのを待つ最長の時間。過ぎたらキーボードの状態に関係なくシートを出す */
private const val KEYBOARD_GIVE_UP_MILLIS = 3_000L

/** 幕の濃さ（Material 3 のボトムシートと同じ） */
private const val SCRIM_ALPHA = 0.32f

/** この距離より下へドラッグして離すと、シートを閉じる */
private val DRAG_DISMISS_DISTANCE = 96.dp
