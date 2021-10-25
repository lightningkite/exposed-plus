package com.lightningkite.exposedplus.sync

import com.lightningkite.exposedplus.TypedQuery
import io.lettuce.core.RedisClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.jetbrains.exposed.sql.EqOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table

private val client: RedisClient = TODO()

suspend fun test() {
    val x = client.connectPubSub()
    client.connectPubSub().coroutines().run {
        subscribe("asdf")
        observeChannels().collect {
            println(it.channel + ": " + it.message)
        }
    }
}

// goal function
sealed interface ListChange<T> {
    class Reset<T>(val list: List<T>): ListChange<T>
    class Insert<T>(val at: Int, val new: T): ListChange<T>
    class Remove<T>(val at: Int, val old: T): ListChange<T>
}
fun <OWNER, END> TypedQuery<OWNER, END>.watch(): Flow<ListChange<END>> {

}

fun Expression<*>.execute(given: Map<String, Any?>): Any = when(this) {
    is EqOp -> this.expr1.execute(given) == this.expr2.execute(given)
}

/*
TODO

[ ] Local executor for expressions - used to identify list changes without querying the database
[X] Redis interaction - to send changes to other application servers
[ ] Update interception - to trigger change sending
 */