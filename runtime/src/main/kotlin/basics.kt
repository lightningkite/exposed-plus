package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import kotlin.sequences.Sequence

interface ResultMapper<InstanceType> {
    val convert: (row: ResultRow) -> InstanceType
}

interface BaseColumnsType<KeyType> {
    val set: ColumnSet
    fun matchingKey(key: KeyType): Op<Boolean>
    fun matchingKey(otherColumns: List<Column<*>>): Op<Boolean>
}

abstract class ResultMappingTable<ColumnsType : BaseColumnsType<KeyType>, InstanceType, KeyType>(name: String) : Table(name),
    ResultMapper<InstanceType> {
    abstract fun alias(name: String): ColumnsType
    abstract fun split(instance: InstanceType): Map<Column<*>, Any?>
}

