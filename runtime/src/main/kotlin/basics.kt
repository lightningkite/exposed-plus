package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import kotlin.sequences.Sequence

interface ResultMapper<InstanceType> {
    val convert: (row: ResultRow) -> InstanceType
    val selections: List<ExpressionWithColumnType<*>>
    fun getForeignKeyUntyped(key: ForeignKeyField<*>): ((InstanceType)->ForeignKey<*>?)? = null
}
@Suppress("UNCHECKED_CAST")
fun <InstanceType, TableType : ResultMappingTable<*, *, *>> ResultMapper<InstanceType>.getForeignKey(key: ForeignKeyField<TableType>): ((InstanceType)->ForeignKey<TableType>)? {
    return getForeignKeyUntyped(key) as? ((InstanceType)->ForeignKey<TableType>)
}

interface BaseColumnsType<InstanceType, KeyType>: ResultMapper<InstanceType> {
    val set: ColumnSet
    fun matchingKey(key: KeyType): Op<Boolean>
    fun matchingKey(otherColumns: List<Column<*>>): Op<Boolean>
}

abstract class ResultMappingTable<ColumnsType : BaseColumnsType<InstanceType, KeyType>, InstanceType, KeyType>(name: String) : Table(name),
    BaseColumnsType<InstanceType, KeyType> {
    abstract fun alias(name: String): ColumnsType
    abstract fun split(instance: InstanceType): Map<Column<*>, Any?>
}

interface HasSingleColumnPrimaryKey<RawKey, KeyType> {
    val primaryKeyColumn: Column<RawKey>
    abstract fun keyFromColumnValue(value: RawKey): KeyType
}