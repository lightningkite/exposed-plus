package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import kotlin.sequences.Sequence


fun <Owner : ResultMappingTable<*, T, *>, T> Owner.all(): TypedQuery<Owner, T> = TypedQuery(this, this, this)

data class TypedQuery<FieldOwner, EndType>(
    val base: ColumnSet,
    val columns: FieldOwner,
    val mapper: ResultMapper<EndType>,
    val condition: Op<Boolean>? = null,
    val orderBy: List<Pair<ExpressionWithColumnType<*>, SortOrder>> = listOf(),
    val joins: List<ExistingJoin> = listOf(),
    val limit: Int? = null,
    val offset: Long? = null,
) {

    class ExistingJoin(
        val field: ForeignKeyField<*>,
        val columnsType: BaseColumnsType<*, *>
    )

    val query: Query
        get() {
            var id = 0
            fun genName(): String = "joined_" + ('a' + id++)
            var query = Query(
                set = Slice(
                    joins.fold(
                        base
                    ) { acc: ColumnSet, part: ExistingJoin ->
                        acc.join(part.columnsType.set, JoinType.LEFT) {
                            part.columnsType.matchingKey(part.field.columns)
                        }
                    },
                    fields = mapper.selections
                ),
                where = condition
            ).orderBy(*orderBy.toTypedArray())
            if (limit != null) {
                if (offset != null) query = query.limit(limit, offset)
                else query = query.limit(limit)
            } else if (offset != null) query = query.limit(Int.MAX_VALUE, offset)
            return query
        }

    fun asSequence(): Sequence<EndType> = query.asSequence().map(mapper.convert)
    fun toList(): List<EndType> = asSequence().toList()
    inline fun forEach(action: (EndType) -> Unit) = asSequence().forEach(action)

    fun take(count: Int) = this.copy(limit = count + (limit ?: 0))
    fun drop(count: Int) = drop(count.toLong())
    fun drop(count: Long) = this.copy(offset = count + (offset ?: 0))

    fun single() = take(2).asSequence().single()
    fun first() = take(1).asSequence().first()
    fun last() = asSequence().last()
    fun singleOrNull() = take(2).asSequence().singleOrNull()
    fun firstOrNull() = take(1).asSequence().firstOrNull()
    fun lastOrNull() = asSequence().lastOrNull()

    fun count() = query.count()
}

class JoiningSqlExpressionBuilder(
    val joins: MutableList<TypedQuery.ExistingJoin> = mutableListOf()
) : ISqlExpressionBuilder {
    val <
            TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
            ColumnsType : BaseColumnsType<InstanceType, KeyType>,
            InstanceType,
            KeyType
            >
            ForeignKeyField<TableType>.value: ColumnsType
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

fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.filter(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> Op<Boolean>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.columns)
    return copy(
        joins = x.joins,
        condition = this.condition?.let { it and expr } ?: expr
    )
}

fun <FieldOwner : SingleResultMapper<EndType>, EndType : Number> TypedQuery<FieldOwner, EndType>.sum(): EndType? {
    return this.mapSingle { it.value.sum() }.firstOrNull()
}

fun <T : Number, FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.sumOf(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ExpressionWithColumnType<T>
): T? {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.columns)
    return copy(
        joins = x.joins
    ).mapSingle { expr.sum() }.firstOrNull()
}

fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.sortedBy(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ExpressionWithColumnType<*>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.columns)
    return copy(
        joins = x.joins,
        orderBy = listOf(expr to SortOrder.ASC_NULLS_LAST)
    )
}

fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.sortedByDescending(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ExpressionWithColumnType<*>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.columns)
    return copy(
        joins = x.joins,
        orderBy = listOf(expr to SortOrder.DESC_NULLS_LAST)
    )
}

class SingleResultMapper<T>(val value: ExpressionWithColumnType<T>) : ResultMapper<T> {
    override val selections: List<ExpressionWithColumnType<*>>
        get() = listOf(value)
    override val convert: (row: ResultRow) -> T
        get() = { it[value] }
}

class PairResultMapper<A, B>(val first: ExpressionWithColumnType<A>, val second: ExpressionWithColumnType<B>) :
    ResultMapper<Pair<A, B>> {
    override val selections: List<ExpressionWithColumnType<*>>
        get() = listOf(first, second)
    override val convert: (row: ResultRow) -> Pair<A, B>
        get() = { it[first] to it[second] }
}

fun <T, FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.mapSingle(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ExpressionWithColumnType<T>
): TypedQuery<SingleResultMapper<T>, T> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.columns)
    val m = SingleResultMapper(expr)
    return TypedQuery(
        base = this.base,
        columns = m,
        mapper = m,
        condition = this.condition,
        joins = x.joins,
        limit = this.limit,
        offset = this.offset,
        orderBy = this.orderBy
    )
}

fun <A, B, FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.mapPair(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> Pair<ExpressionWithColumnType<A>, ExpressionWithColumnType<B>>
): TypedQuery<PairResultMapper<A, B>, Pair<A, B>> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.columns)
    val m = PairResultMapper(expr.first, expr.second)
    return TypedQuery(
        base = this.base,
        columns = m,
        mapper = m,
        condition = this.condition,
        joins = x.joins,
        limit = this.limit,
        offset = this.offset,
        orderBy = this.orderBy
    )
}

fun <
        FieldOwner,
        EndType,
        TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
        ColumnsType : BaseColumnsType<InstanceType, KeyType>,
        InstanceType,
        KeyType
        >
        TypedQuery<FieldOwner, EndType>.mapFk(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ForeignKeyField<TableType>
): TypedQuery<ColumnsType, InstanceType> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.columns)
    val existing = joins.find { it.field === expr }

    @Suppress("UNCHECKED_CAST")
    val ct = existing?.columnsType as? ColumnsType ?: run {
        val created = expr.mapper.alias("joined_" + ('a' + joins.size))
        x.joins.add(TypedQuery.ExistingJoin(expr, created))
        created
    }
    return TypedQuery(
        base = this.base,
        columns = ct,
        mapper = ct,
        condition = this.condition,
        joins = x.joins,
        limit = this.limit,
        offset = this.offset,
        orderBy = this.orderBy
    )
}

fun <
        FieldOwner : ResultMappingTable<*, EndType, *>,
        EndType,
        DestFieldOwner : ResultMappingTable<DestColumns, DestEndType, *>,
        DestColumns: BaseColumnsType<DestEndType, *>,
        DestEndType
        > TypedQuery<FieldOwner, EndType>.flatMapReverse(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> Reverse<DestFieldOwner, FieldOwner>
): TypedQuery<DestColumns, DestEndType> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val reverse = makeExpr(x, this.columns)
    val expr = reverse.field
    val existing = joins.find { it.field === expr }

    @Suppress("UNCHECKED_CAST")
    val ct = existing?.columnsType as? DestColumns ?: run {
        val created = reverse.foreignKeyOwner.alias("joined_" + ('a' + joins.size))
        x.joins.add(TypedQuery.ExistingJoin(expr, created))
        created
    }
    return TypedQuery<DestColumns, DestEndType>(
        base = this.base,
        columns = ct,
        mapper = ct,
        condition = this.condition,
        joins = x.joins,
        limit = this.limit,
        offset = this.offset,
        orderBy = this.orderBy
    )
}

fun <
        FieldOwner,
        EndType,
        TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
        ColumnsType : BaseColumnsType<InstanceType, KeyType>,
        InstanceType,
        KeyType : ForeignKey<TableType>
        >
        TypedQuery<FieldOwner, EndType>.prefetch(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> ForeignKeyField<TableType>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr: ForeignKeyField<TableType> = makeExpr(x, this.columns)
    val existing = joins.find { it.field === this } ?: run {
        val created = expr.mapper.alias("joined_" + ('a' + joins.size))
        val full = TypedQuery.ExistingJoin(expr, created)
        x.joins.add(full)
        full
    }

    @Suppress("UNCHECKED_CAST")
    val ct = existing.columnsType as ColumnsType
    val submapper: ResultMapper<EndType> = object : ResultMapper<EndType> {
        val getter = this@prefetch.mapper.getForeignKey(expr)!!
        override val convert: (row: ResultRow) -> EndType
            get() = {
                val basis = this@prefetch.mapper.convert(it)

                @Suppress("UNCHECKED_CAST")
                val fk = getter(basis) as? KeyType
                if (fk != null) {
                    val preResolved = ct.convert(it)
                    fk.prefill(preResolved)
                }
                basis
            }
        override val selections: List<ExpressionWithColumnType<*>>
            get() = this@prefetch.mapper.selections + ct.selections

        override fun getForeignKeyUntyped(key: ForeignKeyField<*>): ((EndType) -> ForeignKey<*>?)? {
            return ct.getForeignKeyUntyped(key)?.let { child ->
                {
                    @Suppress("UNCHECKED_CAST")
                    (getter(it) as? KeyType)?.let { child(it.value) }
                }
            } ?: this@prefetch.mapper.getForeignKeyUntyped(key)
        }
    }
    return TypedQuery(
        base = this.base,
        columns = this.columns,
        mapper = submapper,
        condition = this.condition,
        joins = x.joins,
        limit = this.limit,
        offset = this.offset,
        orderBy = this.orderBy
    )
}

@JvmName("mapCompound")
fun <
        FieldOwner,
        EndType,
        FieldOwnerB,
        EndTypeB,
        > TypedQuery<FieldOwner, EndType>.mapCompound(
    makeExpr: JoiningSqlExpressionBuilder.(FieldOwner) -> FieldOwnerB
): TypedQuery<*, *> where
        FieldOwner : ResultMapper<EndType>,
        FieldOwnerB : ResultMapper<EndTypeB> {
    val x = JoiningSqlExpressionBuilder(this.joins.toMutableList())
    val expr = makeExpr(x, this.columns)
    val existing = joins.find { it.field === this }
    return TypedQuery(
        base = this.base,
        columns = expr,
        mapper = expr,
        condition = this.condition,
        joins = x.joins,
        limit = this.limit,
        offset = this.offset,
        orderBy = this.orderBy
    )
}
