package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.SqlExpressionBuilder

inline fun ExpressionWithColumnType<String>.startsWith(prefix: String) = with(SqlExpressionBuilder) { this@startsWith like "$prefix%" }
inline fun ExpressionWithColumnType<String>.endsWith(postfix: String) = with(SqlExpressionBuilder) { this@endsWith like "%$postfix" }
inline fun ExpressionWithColumnType<String>.contains(other: String) = with(SqlExpressionBuilder) { this@contains like "%$other%" }
inline fun ExpressionWithColumnType<String>.startsWith(prefix: String, ignoreCase: Boolean) = with(SqlExpressionBuilder) { this@startsWith like "$prefix%" }
inline fun ExpressionWithColumnType<String>.endsWith(postfix: String, ignoreCase: Boolean) = with(SqlExpressionBuilder) { this@endsWith like  "%$postfix" }
inline fun ExpressionWithColumnType<String>.contains(other: String, ignoreCase: Boolean) = with(SqlExpressionBuilder) { this@contains like "%$other%" }