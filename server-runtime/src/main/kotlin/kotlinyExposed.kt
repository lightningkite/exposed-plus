package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder

inline fun ExpressionWithColumnType<String>.startsWith(prefix: String) = with(SqlExpressionBuilder) { this@startsWith like "$prefix%" }
inline fun ExpressionWithColumnType<String>.endsWith(postfix: String) = with(SqlExpressionBuilder) { this@endsWith like "%$postfix" }
inline fun ExpressionWithColumnType<String>.contains(other: String) = with(SqlExpressionBuilder) { this@contains like "%$other%" }
inline fun ExpressionWithColumnType<String>.startsWith(prefix: String, ignoreCase: Boolean) = with(SqlExpressionBuilder) { this@startsWith like "$prefix%" }
inline fun ExpressionWithColumnType<String>.endsWith(postfix: String, ignoreCase: Boolean) = with(SqlExpressionBuilder) { this@endsWith like  "%$postfix" }
inline fun ExpressionWithColumnType<String>.contains(other: String, ignoreCase: Boolean) = with(SqlExpressionBuilder) { this@contains like "%$other%" }

val SortOrder.reversed: SortOrder get() = when(this) {
    SortOrder.ASC -> SortOrder.DESC
    SortOrder.DESC -> SortOrder.ASC
    SortOrder.ASC_NULLS_FIRST -> SortOrder.DESC_NULLS_LAST
    SortOrder.DESC_NULLS_FIRST -> SortOrder.ASC_NULLS_LAST
    SortOrder.ASC_NULLS_LAST -> SortOrder.DESC_NULLS_FIRST
    SortOrder.DESC_NULLS_LAST -> SortOrder.ASC_NULLS_FIRST
}