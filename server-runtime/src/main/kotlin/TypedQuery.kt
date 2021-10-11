package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import kotlin.sequences.Sequence


@Suppress("UNCHECKED_CAST")
fun <Owner : ResultMappingTable<Columns, T, *>, Columns, T> Owner.all(): TypedQuery<Columns, T> = TypedQuery(this, this as Columns, this)

private fun ColumnSet.findPrimaryKeys(): Array<out Column<*>> = when(this) {
    is Table -> this.primaryKey!!.columns
    is Alias<*> -> this.delegate.findPrimaryKeys()
    else -> throw IllegalStateException()
}

data class TypedQuery<FieldOwner, EndType>(
    val base: ColumnSet,
    val columns: FieldOwner,
    val mapper: ResultMapper<EndType>,
    val condition: Op<Boolean>? = null,
    val orderBy: List<Pair<ExpressionWithColumnType<*>, SortOrder>> = listOf(),
    val joins: Map<Any, Join> = mapOf(),
    val needsGroupBy: Boolean = false,
    val limit: Int? = null,
    val offset: Long? = null,
) {

    class Join(
        val type: JoinType = JoinType.LEFT,
        val columnsType: BaseColumnsType<*, *>,
        val operation: Op<Boolean>
    )

    val query: Query by lazy {
            var id = 0
            fun genName(): String = "joined_" + ('a' + id++)
            var query = Query(
                set = Slice(
                    joins.values.fold(
                        base
                    ) { acc: ColumnSet, part: Join ->
                        org.jetbrains.exposed.sql.Join(
                            table = acc,
                            otherTable = part.columnsType.set,
                            joinType = part.type,
                            additionalConstraint = {part.operation}
                        )
                    },
                    fields = mapper.selections
                ),
                where = condition
            ).orderBy(*orderBy.toTypedArray())
            if (limit != null) {
                if (offset != null) query = query.limit(limit, offset)
                else query = query.limit(limit)
            } else if (offset != null) query = query.limit(Int.MAX_VALUE, offset)
            if(needsGroupBy) {
                query = query.groupBy(*base.findPrimaryKeys())
            }
            query
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

    fun reversed() = copy(orderBy = orderBy.map { it.first to it.second.reversed })

    fun count() = query.count()
    fun any(): Boolean = firstOrNull() != null
    fun none(): Boolean = firstOrNull() == null
    fun isNotEmpty(): Boolean = firstOrNull() != null
    fun isEmpty(): Boolean = firstOrNull() == null
}

class JoiningSqlExpressionBuilder<FieldOwner, EndType>(
    val basis: TypedQuery<FieldOwner, EndType>
) : ISqlExpressionBuilder {
    val joins: LinkedHashMap<Any, TypedQuery.Join> = basis.joins.toMap(LinkedHashMap())
    var needsGroupBy: Boolean = basis.needsGroupBy

    fun <FieldOwner, EndType> build(
        base: ColumnSet = basis.base,
        columns: FieldOwner,
        mapper: ResultMapper<EndType>,
        condition: Op<Boolean>? = basis.condition,
        orderBy: List<Pair<ExpressionWithColumnType<*>, SortOrder>> = basis.orderBy,
        joins: Map<Any, TypedQuery.Join> = this.joins,
        needsGroupBy: Boolean = this.needsGroupBy,
        limit: Int? = basis.limit,
        offset: Long? = basis.offset,
    ): TypedQuery<FieldOwner, EndType> = TypedQuery(
        base,
        columns,
        mapper,
        condition,
        orderBy,
        joins,
        needsGroupBy,
        limit,
        offset
    )

    fun build(
        base: ColumnSet = basis.base,
        condition: Op<Boolean>? = basis.condition,
        orderBy: List<Pair<ExpressionWithColumnType<*>, SortOrder>> = basis.orderBy,
        joins: Map<Any, TypedQuery.Join> = this.joins,
        needsGroupBy: Boolean = this.needsGroupBy,
        limit: Int? = basis.limit,
        offset: Long? = basis.offset,
    ): TypedQuery<FieldOwner, EndType> = TypedQuery(
        base,
        basis.columns,
        basis.mapper,
        condition,
        orderBy,
        joins,
        needsGroupBy,
        limit,
        offset
    )

    val <
            TableType : ResultMappingTable<ColumnsType, *, *>,
            ColumnsType : BaseColumnsType<*, *>
            >
            ForeignKeyField<TableType>.value: ColumnsType
        get() {
            val existing = joins[this]
            @Suppress("UNCHECKED_CAST")
            return existing?.columnsType as? ColumnsType ?: run {
                val created = this.mapper.alias("joined_" + ('a' + joins.size))
                joins[this] = TypedQuery.Join(
                    columnsType = created,
                    operation = SqlExpressionBuilder.run { created.matchingKey(this@value.columns) }
                )
                return created
            }
        }

    val <
            PointedToColumns : BaseColumnsType<*, *>,
            PointedTo : ResultMappingTable<PointedToColumns, *, *>,
            HasFKColumns: BaseColumnsType<*, *>,
            HasFK : ResultMappingTable<HasFKColumns, *, *>
            >
            Reverse<HasFK, HasFKColumns, PointedTo, PointedToColumns>.anyValue: HasFKColumns
        get() {
            val existing = joins[this]
            @Suppress("UNCHECKED_CAST")
            return existing?.columnsType as? HasFKColumns ?: run {
                val hasFkAlias = this.hasFK.alias("joined_" + ('a' + joins.size))
                val linkedItemsIds = this.field(hasFkAlias).columns
                joins[this] = TypedQuery.Join(
                    columnsType = hasFkAlias,
                    operation = SqlExpressionBuilder.run {
                        pointedTo.matchingKey(linkedItemsIds)
                    }
                )
                return hasFkAlias
            }
        }

    fun <
            PointedToColumns : BaseColumnsType<*, *>,
            PointedTo : ResultMappingTable<PointedToColumns, *, *>,
            HasFKColumns: BaseColumnsType<*, *>,
            HasFK : ResultMappingTable<HasFKColumns, *, *>
            >
            Reverse<HasFK, HasFKColumns, PointedTo, PointedToColumns>.any(
        selector: (HasFKColumns) -> Op<Boolean>
    ): Op<Boolean> {
        needsGroupBy = true
        return selector(anyValue)
    }

    fun <
            PointedToColumns : BaseColumnsType<*, *>,
            PointedTo : ResultMappingTable<PointedToColumns, *, *>,
            HasFKColumns: BaseColumnsType<*, *>,
            HasFK : ResultMappingTable<HasFKColumns, *, *>
            >
            Reverse<HasFK, HasFKColumns, PointedTo, PointedToColumns>.count(): Count {
        needsGroupBy = true
        return anyValue.primaryKeyColumns.first().count()
    }
}

inline fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.filter(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> Op<Boolean>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this)
    val expr = makeExpr(x, this.columns)
    return x.build(
        condition = this.condition?.let { it and expr } ?: expr
    )
}

inline fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.any(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> Op<Boolean>
): Boolean = filter(makeExpr).any()

inline fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.none(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> Op<Boolean>
): Boolean = filter(makeExpr).none()

inline fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.all(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> Op<Boolean>
): Boolean = filter { not(makeExpr(it)) }.none()

inline fun <T : Comparable<T>, FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.minByOrNull(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> ExpressionWithColumnType<T>
): EndType? = sortedBy(makeExpr).firstOrNull()

inline fun <T : Comparable<T>, FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.maxByOrNull(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> ExpressionWithColumnType<T>
): EndType? = sortedByDescending(makeExpr).firstOrNull()

fun <FieldOwner : SingleResultMapper<EndType>, EndType : Number> TypedQuery<FieldOwner, EndType>.sum(): EndType? {
    return this.mapSingle { it.value.sum() }.firstOrNull()
}

inline fun <T : Number, FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.sumOf(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> ExpressionWithColumnType<T>
): T? {
    val x = JoiningSqlExpressionBuilder(this)
    val expr = makeExpr(x, this.columns)
    return x.build().mapSingle { expr.sum() }.firstOrNull()
}

inline fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.sortedBy(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> ExpressionWithColumnType<*>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this)
    val expr = makeExpr(x, this.columns)
    return x.build(
        orderBy = listOf(expr to SortOrder.ASC_NULLS_LAST)
    )
}

inline fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.sortedByDescending(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> ExpressionWithColumnType<*>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this)
    val expr = makeExpr(x, this.columns)
    return x.build(
        orderBy = listOf(expr to SortOrder.DESC_NULLS_LAST)
    )
}

inline fun <FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.skipColumns(
    makeExpr: (FieldOwner) -> Map<Column<*>, *>
): TypedQuery<FieldOwner, EndType> {
    val defaults = makeExpr(this.columns)
    return TypedQuery(
        base = base,
        columns = columns,
        mapper = object: ResultMapper<EndType> {
            override val convert: (row: ResultRow) -> EndType = { row ->
                for(default in defaults) {
                    row[default.key] = default.value
                }
                this@skipColumns.mapper.convert(row)
            }
            override val selections: List<ExpressionWithColumnType<*>> = this@skipColumns.mapper.selections - defaults.keys
        },
        condition = condition,
        orderBy = orderBy,
        joins = joins,
        needsGroupBy = needsGroupBy,
        limit = limit,
        offset = offset
    )
}

class SingleResultMapper<T>(val value: ExpressionWithColumnType<T>) : ResultMapper<T> {
    override val selections: List<ExpressionWithColumnType<*>>
        get() = listOf(value)
    override val convert: (row: ResultRow) -> T
        get() = { it[value] }
}

inline fun <T, FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.mapSingle(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> ExpressionWithColumnType<T>
): TypedQuery<SingleResultMapper<T>, T> {
    val x = JoiningSqlExpressionBuilder(this)
    val expr = makeExpr(x, this.columns)
    val m = SingleResultMapper(expr)
    return x.build(
        columns = m,
        mapper = m,
    )
}

class PairResultMapper<A, B>(val first: ExpressionWithColumnType<A>, val second: ExpressionWithColumnType<B>) :
    ResultMapper<Pair<A, B>> {
    override val selections: List<ExpressionWithColumnType<*>>
        get() = listOf(first, second)
    override val convert: (row: ResultRow) -> Pair<A, B>
        get() = { it[first] to it[second] }
}

inline fun <A, B, FieldOwner, EndType> TypedQuery<FieldOwner, EndType>.mapPair(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> Pair<ExpressionWithColumnType<A>, ExpressionWithColumnType<B>>
): TypedQuery<PairResultMapper<A, B>, Pair<A, B>> {
    val x = JoiningSqlExpressionBuilder(this)
    val expr = makeExpr(x, this.columns)
    val m = PairResultMapper(expr.first, expr.second)
    return x.build(
        columns = m,
        mapper = m,
    )
}

inline fun <
        FieldOwner,
        EndType,
        TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
        ColumnsType : BaseColumnsType<InstanceType, KeyType>,
        InstanceType,
        KeyType
        >
        TypedQuery<FieldOwner, EndType>.mapFk(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> ForeignKeyField<TableType>
): TypedQuery<ColumnsType, InstanceType> {
    val x = JoiningSqlExpressionBuilder(this)
    val expr = makeExpr(x, this.columns)
    val ct = with(x) { expr.value }
    return x.build(
        columns = ct,
        mapper = ct
    )
}

inline fun <
        PointedToColumns : BaseColumnsType<PointedToInstance, *>,
        PointedTo : ResultMappingTable<PointedToColumns, PointedToInstance, *>,
        PointedToInstance,
        HasFKColumns: BaseColumnsType<HasFKInstance, *>,
        HasFK : ResultMappingTable<HasFKColumns, HasFKInstance, *>,
        HasFKInstance
        > TypedQuery<PointedToColumns, PointedToInstance>.flatMapReverse(
    makeExpr: JoiningSqlExpressionBuilder<PointedToColumns, PointedToInstance>.(PointedToColumns) -> Reverse<HasFK, HasFKColumns, PointedTo, PointedToColumns>
): TypedQuery<HasFKColumns, HasFKInstance> {
    val x = JoiningSqlExpressionBuilder(this)
    val expr = makeExpr(x, this.columns)
    val ct = with(x) { expr.anyValue }
    return x.build(
        columns = ct,
        mapper = ct,
    )
}

inline fun <
        FieldOwner,
        EndType,
        TableType : ResultMappingTable<ColumnsType, InstanceType, KeyType>,
        ColumnsType : BaseColumnsType<InstanceType, KeyType>,
        InstanceType,
        KeyType : ForeignKey<InstanceType>
        >
        TypedQuery<FieldOwner, EndType>.prefetch(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> ForeignKeyField<TableType>
): TypedQuery<FieldOwner, EndType> {
    val x = JoiningSqlExpressionBuilder(this)
    val expr: ForeignKeyField<TableType> = makeExpr(x, this.columns)
    val ct = with(x) { expr.value }
    val submapper: ResultMapper<EndType> = object : ResultMapper<EndType> {
        val getter = this@prefetch.mapper.getForeignKey(expr)!!
        override val convert: (row: ResultRow) -> EndType
            get() = {
                val basis = this@prefetch.mapper.convert(it)

                @Suppress("UNCHECKED_CAST")
                val fk = getter(basis) as? KeyType
                if (fk != null) {
                    val preResolved = ct.convert(it)
                    fk.populate(preResolved)
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
    return x.build(
        columns = this.columns,
        mapper = submapper
    )
}

inline fun <
        FieldOwner,
        EndType,
        FieldOwnerB,
        EndTypeB,
        > TypedQuery<FieldOwner, EndType>.mapCompound(
    makeExpr: JoiningSqlExpressionBuilder<FieldOwner, EndType>.(FieldOwner) -> FieldOwnerB
): TypedQuery<FieldOwnerB, EndTypeB> where
        FieldOwner : ResultMapper<EndType>,
        FieldOwnerB : ResultMapper<EndTypeB> {
    val x = JoiningSqlExpressionBuilder(this)
    val expr = makeExpr(x, this.columns)
    return x.build(
        columns = expr,
        mapper = expr,
    )
}
