package com.lightningkite.exposedplus.sync

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.reactive.ChannelMessage
import io.lettuce.core.pubsub.api.reactive.PatternMessage
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono


fun <K : Any, V : Any> StatefulRedisPubSubConnection<K, V>.coroutines(): RedisPubSubCoroutinesCommands<K, V> = RedisPubSubCoroutinesCommandsImpl(reactive())

interface RedisPubSubCoroutinesCommands<K: Any, V: Any>: RedisCoroutinesCommands<K, V>, RedisPubSubDetailCoroutinesCommands<K, V>
interface RedisPubSubDetailCoroutinesCommands<K: Any, V: Any> {

    /**
     * Flux for messages (pmessage) received though pattern subscriptions. The connection needs to be subscribed to
     * one or more patterns using [.psubscribe].
     *
     *
     * Warning! This method uses [reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER] This does unbounded buffering
     * and may lead to [OutOfMemoryError]. Use [.observePatterns] to specify a different
     * strategy.
     *
     *
     * @return hot Flux for subscriptions to pmessage's.
     */
    fun observePatterns(): Flow<PatternMessage<K, V>>

    /**
     * Flux for messages (pmessage) received though pattern subscriptions. The connection needs to be subscribed to
     * one or more patterns using [.psubscribe].
     *
     * @param overflowStrategy the overflow strategy to use.
     * @return hot Flux for subscriptions to pmessage's.
     */
    fun observePatterns(overflowStrategy: FluxSink.OverflowStrategy): Flow<PatternMessage<K, V>>

    /**
     * Flux for messages (message) received though channel subscriptions. The connection needs to be subscribed to
     * one or more channels using [.subscribe].
     *
     *
     *
     * Warning! This method uses [reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER] This does unbounded buffering
     * and may lead to [OutOfMemoryError]. Use [.observeChannels] to specify a different
     * strategy.
     *
     *
     * @return hot Flux for subscriptions to message's.
     */
    fun observeChannels(): Flow<ChannelMessage<K, V>>

    /**
     * Flux for messages (message) received though channel subscriptions. The connection needs to be subscribed to
     * one or more channels using [.subscribe].
     *
     * @param overflowStrategy the overflow strategy to use.
     * @return hot Flux for subscriptions to message's.
     */
    fun observeChannels(overflowStrategy: FluxSink.OverflowStrategy?): Flow<ChannelMessage<K, V>>

    /**
     * Listen for messages published to channels matching the given patterns. The [Mono] completes without a result as
     * soon as the pattern subscription is registered.
     *
     * @param patterns the patterns.
     * @return Mono&lt;Void&gt; Mono for `psubscribe` command.
     */
    suspend fun psubscribe(vararg patterns: K)

    /**
     * Stop listening for messages posted to channels matching the given patterns. The [Mono] completes without a result
     * as soon as the pattern subscription is unregistered.
     *
     * @param patterns the patterns.
     * @return Mono&lt;Void&gt; Mono for `punsubscribe` command.
     */
    suspend fun punsubscribe(vararg patterns: K)

    /**
     * Listen for messages published to the given channels. The [Mono] completes without a result as soon as the *
     * subscription is registered.
     *
     * @param channels the channels.
     * @return Mono&lt;Void&gt; Mono for `subscribe` command.
     */
    suspend fun subscribe(vararg channels: K)

    /**
     * Stop listening for messages posted to the given channels. The [Mono] completes without a result as soon as the
     * subscription is unregistered.
     *
     * @param channels the channels.
     * @return Mono&lt;Void&gt; Mono for `unsubscribe` command.
     */
    suspend fun unsubscribe(vararg channels: K)
}

class RedisPubSubCoroutinesCommandsImpl<K: Any, V: Any>(
    protected val ops: RedisPubSubReactiveCommands<K, V>
): RedisPubSubCoroutinesCommands<K, V>, RedisCoroutinesCommands<K, V> by RedisPubSubCoroutinesCommandsImpl(ops) {
    override fun observePatterns(): Flow<PatternMessage<K, V>> = ops.observePatterns().asFlow()
    override fun observePatterns(overflowStrategy: FluxSink.OverflowStrategy): Flow<PatternMessage<K, V>> = ops.observePatterns().asFlow()
    override fun observeChannels(): Flow<ChannelMessage<K, V>> = ops.observeChannels().asFlow()
    override fun observeChannels(overflowStrategy: FluxSink.OverflowStrategy?): Flow<ChannelMessage<K, V>> = ops.observeChannels().asFlow()
    override suspend fun psubscribe(vararg patterns: K) { ops.psubscribe(*patterns).awaitFirst() }
    override suspend fun punsubscribe(vararg patterns: K) { ops.punsubscribe(*patterns).awaitFirst() }
    override suspend fun subscribe(vararg channels: K) { ops.subscribe(*channels).awaitFirst() }
    override suspend fun unsubscribe(vararg channels: K) { ops.unsubscribe(*channels).awaitFirst() }
}