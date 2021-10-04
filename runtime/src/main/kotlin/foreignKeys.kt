package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select


interface ForeignKeyField<TableType> where TableType : ResultMappingTable<*, *, *> {

//    fun getter(basis: EndType): ForeignKey<TableType, ColumnsType, InstanceType, KeyType>
    val mapper: TableType
    val columns: List<Column<*>>
}

typealias FK<TableType> = ForeignKey<TableType>

abstract class ForeignKey<TableType>(
    val table: TableType
) where
TableType : ResultMappingTable<*, *, *>{
    var filled: Boolean = false
    var untypedValue: Any? = null
}

val <TableType: ResultMappingTable<*, InstanceType, KeyType>, KeyType: ForeignKey<TableType>, InstanceType> KeyType.value: InstanceType
    get() {
        @Suppress("UNCHECKED_CAST")
        return if (filled) untypedValue as InstanceType
        else {
            val calculated =
                this.table.select(table.matchingKey(this)).first()
                    .let { table.convert(it) }
            untypedValue = calculated
            calculated
        }
    }

fun <TableType: ResultMappingTable<*, InstanceType, KeyType>, KeyType: ForeignKey<TableType>, InstanceType> KeyType.prefill(value: InstanceType) {
    untypedValue = value
}