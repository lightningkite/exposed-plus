package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import kotlin.reflect.KClass


interface ForeignKeyField<TableType : ResultMappingTable<*, *, *>> {
    val mapper: TableType
    val columns: List<Column<*>>
}

val <KeyType : ForeignKey<InstanceType>, InstanceType> KeyType.value: InstanceType
    get() {
        @Suppress("UNCHECKED_CAST")
        return if (filled) cachedValue as InstanceType
        else {
            val calculated = testResolve()
            populate(calculated)
            calculated
        }
    }

private val tableMap = HashMap<KeyHandle<*>, ResultMappingTable<*, *, *>>()
@Suppress("UNCHECKED_CAST")
val <Self: ForeignKey<T>, T> Self.table: ResultMappingTable<*, T, Self> get() = tableMap[this.handle] as ResultMappingTable<*, T, Self>
fun <Self: KeyHandle<T>, T> ResultMappingTable<*, T, *>.register(to: Self) {
    if(tableMap.containsKey(to)) { throw IllegalStateException("Handle $this already registered to ${tableMap[to]}, trying to set to $this") }
    tableMap[to] = this
}

fun <KeyType : ForeignKey<InstanceType>, InstanceType> KeyType.testResolve(): InstanceType {
    val table = this.table
    return table.select(table.matchingKey(this)).first()
        .let { table.convert(it) }
}

data class Reverse<
        HasFK: ResultMappingTable<HasFkColumns, *, *>,
        HasFkColumns: BaseColumnsType<*, *>,
        PointedTo: ResultMappingTable<PointedToColumns, *, *>,
        PointedToColumns: BaseColumnsType<*, *>
        >(
    val pointedTo: PointedToColumns,
    val hasFK: HasFK,
    val field: (HasFkColumns) -> ForeignKeyField<PointedTo>
)