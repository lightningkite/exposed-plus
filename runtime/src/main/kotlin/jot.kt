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
}

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
    val source: ForeignKeyField<TableType, ColumnsType, InstanceType, KeyType>
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
                    this.source.mapper.select { source.mapper.matchingKey(key) }.first()
                        .let { source.mapper.convert(it) }
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
    val joins: List<ExistingJoin> = listOf(),
    val limit: Int? = null,
    val offset: Long? = null,
) : Sequence<EndType> {

    data class ExistingJoin(
        val field: ForeignKeyField<*, *, *, *>,
        val columnsType: BaseColumnsType<*>
    )

    val query: Query
        get() {
            var id = 0
            fun genName(): String = "joined_" + ('a' + id++)
            val query = Query(
                set = Slice(
                    joins.fold(
                        base
                    ) { acc: ColumnSet, part: ExistingJoin ->
                        acc.join(part.columnsType.set, JoinType.LEFT) {
                            part.columnsType.matchingKey(part.field.columns)
                        }
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
    val joins: MutableList<TypedQuery.ExistingJoin> = mutableListOf()
) : ISqlExpressionBuilder {
    val <TableType, ColumnsType, InstanceType, KeyType>
            ForeignKeyField<TableType, ColumnsType, InstanceType, KeyType>.value: ColumnsType
            where
            TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
            TableType : BaseColumnsType<KeyType>,
            ColumnsType : BaseColumnsType<KeyType>
        get() {
            val existing = joins.find { it.field === this }
            @Suppress("UNCHECKED_CAST")
            return existing?.columnsType as? ColumnsType ?: run {
                val created = this.mapper.alias("joined_" + ('a' + joins.size))
                joins.add(TypedQuery.ExistingJoin(this, created))
                return created
            }
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

//fun <
//        FieldOwner : ResultMapper<EndType>,
//        EndType,
//        NewTableType : ResultMappingTable<*, NewInstanceType, NewKeyType>,
//        NewInstanceType,
//        NewKeyType : Comparable<NewKeyType>
//        > TypedQuery<FieldOwner, EndType>.alsoSelect(
//    makeExpr: (FieldOwner) -> ForeignKeyColumn<NewTableType, *, NewInstanceType, NewKeyType>
//): TypedQuery<FieldOwner, EndType> {
//    val other = makeExpr(this.owner)
//    return copy(
//        select = this.select + other.mapper.columns,
//        joins = this.joins + other
//    )
//}

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
