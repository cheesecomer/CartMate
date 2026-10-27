package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.notice.MemberNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** 今の家族のメンバー。共有のリストのアバターと、商品詳細入力画面の人数に使う */
fun interface MemberDirectory {
    /** 家族に参加していなければ空 */
    fun observeMembers(): Flow<List<Member>>

    /** 自分がほかのメンバーに付けた呼び名（docs/requirements.md §3.5「表示名と呼び名」） */
    fun observeAliases(): Flow<Map<UserId, String>> = flowOf(emptyMap())

    /** 自分の画面に出すメンバーの名前 */
    fun observeNames(): Flow<MemberNames> = combine(observeMembers(), observeAliases(), ::MemberNames)
}
