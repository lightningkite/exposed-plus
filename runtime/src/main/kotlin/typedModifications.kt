package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.autoIncColumnType
import org.jetbrains.exposed.sql.insert

fun <Owner : ResultMappingTable<*, T, *>, T> Owner.insert(value: T) = this.insert {
    for(broken in this.split(value)) {
        if(broken.key.autoIncColumnType != null) continue
        @Suppress("UNCHECKED_CAST")
        it[broken.key as Column<Any?>] = broken.value
    }
}

fun <Owner, T, Key, RawKey> Owner.insertAndGetKey(value: T): Key where Owner : ResultMappingTable<*, T, Key>, Owner: HasSingleColumnPrimaryKey<RawKey, Key> {
    return this.keyFromColumnValue(insert(value)[this.primaryKeyColumn])
}

fun <Owner : ResultMappingTable<*, T, *>, T> Owner.batchInsert(
    values: Iterable<T>,
    ignore: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true,
) = this.batchInsert(
    data = values,
    ignore = ignore,
    shouldReturnGeneratedValues = shouldReturnGeneratedValues,
    body = { value ->
        for(broken in split(value)) {
            if(broken.key.autoIncColumnType != null) continue
            @Suppress("UNCHECKED_CAST")
            this[broken.key as Column<Any?>] = broken.value
        }
    }
)

fun <Owner : ResultMappingTable<*, T, *>, T> Owner.update(value: T): Int {
    val split = this.split(value)
    return this.update(
        where = {
            @Suppress("UNCHECKED_CAST")
            primaryKey!!.columns
                .asSequence()
                .map { (it as Column<Any?>) eq split[it] }
                .reduce() { acc, b -> acc and b }
        },
        body = {
            for(broken in split) {
                if(broken.key in primaryKey!!.columns) continue
                @Suppress("UNCHECKED_CAST")
                it[broken.key as Column<Any?>] = broken.value
            }
        }
    )
}
