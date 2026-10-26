package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.Member
import kotlinx.coroutines.flow.Flow

/** 今の家族のメンバー。共有のリストのアバターと、商品詳細入力画面の人数に使う */
fun interface MemberDirectory {
    /** 家族に参加していなければ空 */
    fun observeMembers(): Flow<List<Member>>
}
