package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.select


interface ForeignKeyField<TableType, ColumnsType, InstanceType, KeyType> where
TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
TableType : BaseColumnsType<KeyType>,
ColumnsType : BaseColumnsType<KeyType> {
    val mapper: TableType
    val columns: List<Column<*>>

    @Suppress("UNCHECKED_CAST")
    val columnsType: ColumnsType
        get() = mapper as ColumnsType
}

typealias FK<InstanceType> = ForeignKey<*, *, InstanceType, *>

class ForeignKey<TableType, ColumnsType, InstanceType, KeyType>(
    val key: KeyType,
    val table: TableType
) where
TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
TableType : BaseColumnsType<KeyType>,
ColumnsType : BaseColumnsType<KeyType> {
    private var filled: Boolean = false
    private var _value: InstanceType? = null
    val value: InstanceType
        get() {
            @Suppress("UNCHECKED_CAST")
            return if (filled) _value as InstanceType
            else {
                val calculated =
                    this.table.select { table.matchingKey(key) }.first()
                        .let { table.convert(it) }
                _value = calculated
                calculated
            }
        }

    fun prefill(value: InstanceType) {
        _value = value
    }
}