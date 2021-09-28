package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import kotlin.sequences.Sequence

interface ResultMapper<InstanceType> {
    val convert: (row: ResultRow) -> InstanceType
}

abstract class ResultMappingTable<ColumnsType, InstanceType, KeyType : Comparable<KeyType>> : Table(),
    ResultMapper<InstanceType> {
    abstract val primaryKeyColumn: Column<KeyType>
    override val primaryKey: PrimaryKey get() = PrimaryKey(primaryKeyColumn)
    abstract fun alias(name: String): ColumnsType
}

data class ForeignKeyColumn<TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>, ColumnsType, InstanceType, KeyType : Comparable<KeyType>>(
    val key: Column<KeyType>,
    val mapper: TableType
)

//fun <
//        TableType: ResultMappingTable<InstanceType, KeyType>,
//        InstanceType,
//        KeyType: Comparable<KeyType>
//        > Table.reference(name: String, other: TableType): ForeignKeyColumn<TableType, InstanceType, KeyType>
//    = ForeignKeyColumn(this.registerColumn<KeyType>(name, other.id.columnType).references(other.id), other)

typealias FK<InstanceType> = ForeignKey<*, *, InstanceType, *>

class ForeignKey<
        TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
        ColumnsType,
        InstanceType,
        KeyType : Comparable<KeyType>
        >(val key: KeyType, val source: ForeignKeyColumn<TableType, ColumnsType, InstanceType, KeyType>) {
    private var filled: Boolean = false
    private var _value: InstanceType? = null
    val value: InstanceType
        get() {
            @Suppress("UNCHECKED_CAST")
            return if (filled) _value as InstanceType
            else {
                val calculated =
                    this.source.mapper.select { source.key eq key }.first().let { source.mapper.convert(it) }
                _value = calculated
                calculated
            }
        }

    fun prefill(value: InstanceType) {
        _value = value
    }
}

fun <Owner : ResultMappingTable<*, T, *>, T> Owner.select(): TypedQuery<Owner, T> = TypedQuery(this, this.columns, this)

data class TypedQuery<FieldOwner : ResultMapper<EndType>, EndType>(
    val base: ColumnSet,
    val select: List<Expression<*>>,
    val owner: FieldOwner,
    val condition: Op<Boolean>? = null,
    val joins: List<ForeignKeyColumn<*, *, *, *>> = listOf(),
    val limit: Int? = null,
    val offset: Long? = null,
) : Sequence<EndType> {

    val query: Query
        get() {
            val query = Query(
                set = Slice(
                    joins.fold(
                        base
                    ) { acc: ColumnSet, part: ForeignKeyColumn<*, *, *, *> ->
                        acc.join(part.mapper, JoinType.LEFT, part.key, part.mapper.primaryKeyColumn)
                    },
                    fields = select
                ),
                where = condition
            )
            return query
        }

    override fun iterator(): Iterator<EndType> = query.asSequence().map(owner.convert).iterator()
}

class JoiningSqlExpressionBuilder(
    val joins: MutableList<ForeignKeyColumn<*, *, *, *>> = mutableListOf()
) : ISqlExpressionBuilder {
    val <
            TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
            ColumnsType,
            InstanceType,
            KeyType : Comparable<KeyType>
            > ForeignKeyColumn<TableType, ColumnsType, InstanceType, KeyType>.value: ColumnsType
        get() {
            if (this in joins) return this.mapper as ColumnsType
            joins.add(this)
            return this.mapper as ColumnsType
        }
}

fun <FieldOwner : ResultMapper<EndType>, EndType> TypedQuery<FieldOwner, EndType>.filter(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> Op<Boolean>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.owner)
    return copy(
        joins = x.joins,
        condition = this.condition?.let { it and expr } ?: expr
    )
}

fun <
        FieldOwner : ResultMapper<EndType>,
        EndType,
        NewTableType : ResultMappingTable<*, NewInstanceType, NewKeyType>,
        NewInstanceType,
        NewKeyType : Comparable<NewKeyType>
        > TypedQuery<FieldOwner, EndType>.alsoSelect(
    makeExpr: (FieldOwner) -> ForeignKeyColumn<NewTableType, *, NewInstanceType, NewKeyType>
): TypedQuery<FieldOwner, EndType> {
    val other = makeExpr(this.owner)
    return copy(
        select = this.select + other.mapper.columns,
        joins = this.joins + other
    )
}

//fun <
//        FieldOwner : ResultMapper<EndType>,
//        EndType,
//        NewTableType : ResultMappingTable<*, NewInstanceType, NewKeyType>,
//        NewInstanceType,
//        NewKeyType : Comparable<NewKeyType>
//        > TypedQuery<FieldOwner, EndType>.map(
//    makeExpr: (FieldOwner) -> ForeignKeyColumn<NewTableType, *, NewInstanceType, NewKeyType>
//): TypedQuery<NewTableType, NewInstanceType> = TODO()
//
//fun <FieldOwner : ResultMapper<EndType>, EndType, NewOwner : ResultMapper<NewType>, NewType> TypedQuery<FieldOwner, EndType>.flatMap(
//    makeExpr: SqlExpressionBuilder.(FieldOwner) -> TypedQuery<NewOwner, NewType>
//): TypedQuery<NewOwner, NewType> = SqlExpressionBuilder.makeExpr(owner).copyQuery {
//    TODO()
//}
