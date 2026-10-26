package com.cheesecomer.cartmate.ui.join

import kotlinx.serialization.Serializable

@Serializable
data object JoinCodeRoute

/** 参加の確認。[code] は招待コード（招待リンクから開いた場合も、入力した場合も） */
@Serializable
data class JoinConfirmRoute(val code: String)
