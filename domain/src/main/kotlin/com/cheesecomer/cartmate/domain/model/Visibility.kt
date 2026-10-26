package com.cheesecomer.cartmate.domain.model

/**
 * 公開範囲。リストと項目のそれぞれにあり、登録したあとは変更できない（docs/requirements.md §3.4）。
 * プライベートなものは、登録した本人にだけ見える
 */
enum class Visibility { SHARED, PRIVATE }
