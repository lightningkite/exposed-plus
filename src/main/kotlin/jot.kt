package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import java.util.*
import kotlin.sequences.Sequence

interface ResultMapper<InstanceType> {
    val convert: (row: ResultRow)-> InstanceType
}

interface HasColumnSet {
    val set: ColumnSet
}

abstract class ResultMappingTable<ColumnsType: HasColumnSet, InstanceType, KeyType : Comparable<KeyType>> : Table(), ResultMapper<InstanceType> {
    abstract val id: Column<KeyType>
    abstract fun alias(name: String): ColumnsType
}

data class ForeignKeyColumn<TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>, ColumnsType: HasColumnSet, InstanceType, KeyType : Comparable<KeyType>>(
    val key: Column<KeyType>,
    val mapper: TableType
)

//fun <
//        TableType: ResultMappingTable<InstanceType, KeyType>,
//        InstanceType,
//        KeyType: Comparable<KeyType>
//        > Table.reference(name: String, other: TableType): ForeignKeyColumn<TableType, InstanceType, KeyType>
//    = ForeignKeyColumn(this.registerColumn<KeyType>(name, other.id.columnType).references(other.id), other)

class ForeignKey<
        TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
        ColumnsType: HasColumnSet,
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

fun <Owner : ResultMappingTable<*, T, *>, T> Owner.select(): TypedQuery<Owner, T> = TypedQuery(this, this.selectAll())

class JoiningQueryBuilder(var query: Query) : ISqlExpressionBuilder {
    val <
            TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
            ColumnsType: HasColumnSet,
            InstanceType,
            KeyType : Comparable<KeyType>
            > ForeignKeyColumn<TableType, ColumnsType, InstanceType, KeyType>.value: ColumnsType
        get() {
            query.set.let { it as? Join }?.joinParts?.let {
                for(part in it) {
                    part.conditions.singleOrNull()?.let {
                        if(it.first == key) return part.joinPart as ColumnsType
                    }
                }
            }
            val alias = mapper.alias("J" + UUID.randomUUID().toString().filter { it.isJavaIdentifierPart() })
            query.adjustColumnSet { this.join(alias.set, JoinType.LEFT, key, mapper.id) }
            return alias
        }
}

fun <FieldOwner : ResultMapper<EndType>, EndType> TypedQuery<FieldOwner, EndType>.filter(
    makeExpr: JoiningQueryBuilder.(FieldOwner) -> Op<Boolean>
): TypedQuery<FieldOwner, EndType> {
    return copyQuery { it.andWhere { JoiningQueryBuilder(it).makeExpr(owner) } }
}


class TypedQuery<FieldOwner : ResultMapper<EndType>, EndType>(val owner: FieldOwner, val query: Query) :
    SizedIterable<EndType> {
    inline fun modQuery(action: (Query) -> Query): TypedQuery<FieldOwner, EndType> = TypedQuery(owner, action(query))
    inline fun copyQuery(action: (Query) -> Query): TypedQuery<FieldOwner, EndType> =
        TypedQuery(owner, action(query.copy()))

    override fun copy(): TypedQuery<FieldOwner, EndType> = TypedQuery(owner, query)
    override fun count(): Long = query.count()
    override fun empty(): Boolean = query.empty()
    override fun limit(n: Int, offset: Long): TypedQuery<FieldOwner, EndType> = copyQuery { it.limit(n, offset) }
    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): TypedQuery<FieldOwner, EndType> =
        copyQuery { it.orderBy(*order) }

    override fun iterator(): Iterator<EndType> = query.asSequence().map { owner.convert(it) }.iterator()

    fun kotlin(): Sequence<EndType> = query.asSequence().map { owner.convert(it) }
}

fun <
        FieldOwner : ResultMapper<EndType>,
        EndType,
        NewTableType : ResultMappingTable<*, NewInstanceType, NewKeyType>,
        NewInstanceType,
        NewKeyType : Comparable<NewKeyType>
        > TypedQuery<FieldOwner, EndType>.map(
    makeExpr: (FieldOwner) -> ForeignKeyColumn<NewTableType, *, NewInstanceType, NewKeyType>
): TypedQuery<NewTableType, NewInstanceType> = TODO()

fun <
        FieldOwner : ResultMapper<EndType>,
        EndType,
        NewTableType : ResultMappingTable<*, NewInstanceType, NewKeyType>,
        NewInstanceType,
        NewKeyType : Comparable<NewKeyType>
        > TypedQuery<FieldOwner, EndType>.alsoSelect(
    makeExpr: (FieldOwner) -> ForeignKeyColumn<NewTableType, *, NewInstanceType, NewKeyType>
): TypedQuery<FieldOwner, EndType> = TODO()

fun <FieldOwner : ResultMapper<EndType>, EndType, NewOwner : ResultMapper<NewType>, NewType> TypedQuery<FieldOwner, EndType>.flatMap(
    makeExpr: SqlExpressionBuilder.(FieldOwner) -> TypedQuery<NewOwner, NewType>
): TypedQuery<NewOwner, NewType> = SqlExpressionBuilder.makeExpr(owner).copyQuery {
    TODO()
}
