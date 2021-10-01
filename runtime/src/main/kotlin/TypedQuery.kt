package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import kotlin.sequences.Sequence


fun <Owner : ResultMappingTable<*, T, *>, T> Owner.all(): TypedQuery<Owner, T> = TypedQuery(this, this.columns, this)

data class TypedQuery<FieldOwner : ResultMapper<EndType>, EndType>(
    val base: ColumnSet,
    val select: List<ExpressionWithColumnType<*>>,
    val owner: FieldOwner,
    val condition: Op<Boolean>? = null,
    val orderBy: List<Pair<ExpressionWithColumnType<*>, SortOrder>> = listOf(),
    val joins: List<ExistingJoin> = listOf(),
    val limit: Int? = null,
    val offset: Long? = null,
) {

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
            ).orderBy(*orderBy.toTypedArray())
            return query
        }

    fun asSequence(): Sequence<EndType> = query.asSequence().map(owner.convert)
    fun toList(): List<EndType> = asSequence().toList()
    inline fun forEach(action: (EndType)->Unit) = asSequence().forEach(action)

    fun take(count: Int) = this.copy(limit = count + (limit ?: 0))
    fun drop(count: Long) = this.copy(offset = count + (offset ?: 0))

    fun single() = take(1).asSequence().single()
    fun first() = take(1).asSequence().first()
    fun last() = asSequence().last()
    fun singleOrNull() = take(1).asSequence().singleOrNull()
    fun firstOrNull() = take(1).asSequence().firstOrNull()
    fun lastOrNull() = asSequence().lastOrNull()

    fun count() = query.count()
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

fun <FieldOwner : SingleResultMapper<EndType>, EndType: Number> TypedQuery<FieldOwner, EndType>.sum(): EndType? {
    return this.mapSingle { it.value.sum() }.firstOrNull()
}
fun <T: Number, FieldOwner : ResultMapper<EndType>, EndType> TypedQuery<FieldOwner, EndType>.sumBy(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ExpressionWithColumnType<T>
): T? {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.owner)
    return copy(
        joins = x.joins
    ).mapSingle { expr.sum() }.firstOrNull()
}

fun <FieldOwner : ResultMapper<EndType>, EndType> TypedQuery<FieldOwner, EndType>.sortedBy(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ExpressionWithColumnType<*>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.owner)
    return copy(
        joins = x.joins,
        orderBy = listOf(expr to SortOrder.ASC_NULLS_LAST)
    )
}

fun <FieldOwner : ResultMapper<EndType>, EndType> TypedQuery<FieldOwner, EndType>.sortedByDescending(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ExpressionWithColumnType<*>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.owner)
    return copy(
        joins = x.joins,
        orderBy = listOf(expr to SortOrder.DESC_NULLS_LAST)
    )
}

class SingleResultMapper<T>(val value: ExpressionWithColumnType<T>): ResultMapper<T> {
    override val convert: (row: ResultRow) -> T
        get() = { it[value] }
}

class PairResultMapper<A, B>(val first: ExpressionWithColumnType<A>, val second: ExpressionWithColumnType<B>): ResultMapper<Pair<A, B>> {
    override val convert: (row: ResultRow) -> Pair<A, B>
        get() = { it[first] to it[second] }
}

@JvmName("mapSingle")
fun <T, FieldOwner : ResultMapper<EndType>, EndType> TypedQuery<FieldOwner, EndType>.mapSingle(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ExpressionWithColumnType<T>
): TypedQuery<SingleResultMapper<T>, T> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.owner)
    return TypedQuery(
        base = this.base,
        select = listOf(expr),
        owner = SingleResultMapper(expr),
        condition = this.condition,
        joins = this.joins,
        limit = this.limit,
        offset = this.offset,
        orderBy = this.orderBy
    )
}

@JvmName("mapPair")
fun <A, B, FieldOwner : ResultMapper<EndType>, EndType> TypedQuery<FieldOwner, EndType>.mapPair(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> Pair<ExpressionWithColumnType<A>, ExpressionWithColumnType<B>>
): TypedQuery<PairResultMapper<A, B>, Pair<A, B>> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.owner)
    return TypedQuery(
        base = this.base,
        select = listOf(expr.first, expr.second),
        owner = PairResultMapper(expr.first, expr.second),
        condition = this.condition,
        joins = this.joins,
        limit = this.limit,
        offset = this.offset,
        orderBy = this.orderBy
    )
}

//fun <N: Number, FieldOwner : ResultMapper<EndType>, EndType>

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
