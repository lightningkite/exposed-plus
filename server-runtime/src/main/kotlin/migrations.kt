package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Index

class VirtualSchema {
    val schemas = HashSet<String>()
    val tables = HashMap<String, VirtualTable>()
}

sealed interface SchemaBuildingStatement {
    val statements: List<String>
    fun reverse(): SchemaBuildingStatement
    fun apply(to: VirtualSchema)
}

data class VirtualTable(
    var name: String,
    var columns: List<VirtualColumn>,
    var primaryKeys: List<VirtualColumn>,
    var indices: List<VirtualIndex>
) {
    constructor(table: Table): this(
        name = table.tableName,
        columns = table.columns.map { VirtualColumn(it) },
        primaryKeys = table.primaryKey!!.columns.map { VirtualColumn(it) },
        indices = table.indices.map { VirtualIndex(it) }
    )
    val table: Table by lazy {
        object: Table() {
            init {
                for(col in this@VirtualTable.columns) {
                    val c = registerColumn<Any?>(
                        name = col.name,
                        type = col.type
                    )
                    if(col.sqlDefault != null) {
                        @Suppress("UNCHECKED_CAST")
                        c.dbDefaultValueHack = col.sqlDefault as Expression<Any?>?
                    }
                }
                for(i in this@VirtualTable.indices) {
                    index(
                        customIndexName = i.name,
                        isUnique = i.unique,
                        columns = i.on.map { columns.find { c -> it.name == c.name }!! }.toTypedArray(),
                        indexType = i.indexType
                    )
                }
            }

            override val primaryKey: PrimaryKey = PrimaryKey(*this@VirtualTable.primaryKeys.map {
                columns.find { c -> c.name == it.name }!!
            }.toTypedArray())
        }
    }
}

data class VirtualColumn(
    var name: String,
    var type: IColumnType,
    var sqlDefault: Expression<*>? = null
) {
    constructor(column: Column<*>):this (
        name = column.name,
        type = column.columnType,
        sqlDefault = column.dbDefaultValueHack,
    )

    fun toColumn(table: VirtualTable): Column<*> = table.table.columns.find { it.name == name }!!
}

data class VirtualIndex(
    var name: String,
    var on: List<VirtualColumn>,
    var unique: Boolean = false,
    var indexType: String? = null
) {
    constructor(index: Index): this(
        name = index.indexName,
        on = index.columns.map { VirtualColumn(it) },
        unique = index.unique,
        indexType = index.indexType
    )

    fun toIndex(table: VirtualTable): Index = table.table.indices.find { it.indexName == name }!!
}

val dbDefaultValueHackHelper by lazy { Column::class.java.getMethod("getDbDefaultValue") }
val dbDefaultValueHackHelper2 by lazy { Column::class.java.getMethod("setDbDefaultValue", Expression::class.java) }
@Suppress("UNCHECKED_CAST")
var <T> Column<T>.dbDefaultValueHack: Expression<T>?
    get() = dbDefaultValueHackHelper(this) as Expression<T>?
    set(value) { dbDefaultValueHackHelper2(this, value) }

data class CreateSchema(val schema: Schema) : SchemaBuildingStatement {
    override val statements: List<String> get() = schema.createStatement()
    override fun reverse(): SchemaBuildingStatement = DropSchema(schema)
    override fun apply(to: VirtualSchema) {
        to.schemas.add(schema.identifier)
    }
}

data class DropSchema(val schema: Schema) : SchemaBuildingStatement {
    override val statements: List<String> get() = schema.dropStatement()
    override fun reverse(): SchemaBuildingStatement = CreateSchema(schema)
    override fun apply(to: VirtualSchema) {
        to.schemas.remove(schema.identifier)
    }
}

data class CreateTable(val table: VirtualTable) : SchemaBuildingStatement {
    override val statements: List<String> get() = table.table.createStatement()
    override fun reverse(): SchemaBuildingStatement = DropTable(table)
    override fun apply(to: VirtualSchema) {
        to.tables[table.name] = table
    }
}

data class DropTable(val table: VirtualTable) : SchemaBuildingStatement {
    override val statements: List<String> get() = table.table.dropStatement()
    override fun reverse(): SchemaBuildingStatement = CreateTable(table)
    override fun apply(to: VirtualSchema) {
        to.tables.remove(table.name)
    }
}

data class CreateIndex(val table: VirtualTable, val index: VirtualIndex) : SchemaBuildingStatement {
    override val statements: List<String> get() = index.toIndex(table).createStatement()
    override fun reverse(): SchemaBuildingStatement = DropIndex(table, index)
    override fun apply(to: VirtualSchema) {
        to.tables[table.name]!!.indices += index
    }
}

data class DropIndex(val table: VirtualTable, val index: VirtualIndex) : SchemaBuildingStatement {
    override val statements: List<String> get() = index.toIndex(table).dropStatement()
    override fun reverse(): SchemaBuildingStatement = CreateIndex(table, index)
    override fun apply(to: VirtualSchema) {
        to.tables[table.name]!!.indices -= index
    }
}

data class AddColumn(val column: VirtualColumn) : SchemaBuildingStatement {
    override val statements: List<String> get() = column.createStatement()
    override fun reverse(): SchemaBuildingStatement = DropColumn(column)
    override fun apply(to: VirtualSchema) {
        to.tables[column.table.tableName]!!.registerColumn<Any?>(column.name, column.columnType)
        if(colu)
    }
}

data class DropColumn(val column: VirtualColumn) : SchemaBuildingStatement {
    override val statements: List<String> get() = column.dropStatement()
    override fun reverse(): SchemaBuildingStatement = AddColumn(column)
}

data class ModifyColumn(val from: VirtualColumn, val to: VirtualColumn) : SchemaBuildingStatement {
    override val statements: List<String>
        get() = to.modifyStatements(
            nullabilityChanged = from.columnType.nullable != to.columnType.nullable,
            autoIncrementChanged = from.columnType.isAutoInc != to.columnType.isAutoInc,
            defaultChanged = from.dbDefaultValueHack.toString() != to.dbDefaultValueHack.toString()
        )

    override fun reverse(): SchemaBuildingStatement = ModifyColumn(to, from)

    companion object {
        fun needed(from: Column<*>, to: Column<*>): Boolean {
            return from.columnType.nullable != to.columnType.nullable ||
                    from.columnType.isAutoInc != to.columnType.isAutoInc ||
                    from.dbDefaultValueHack.toString() != to.dbDefaultValueHack.toString()
        }
    }
}

fun Collection<Table>.migrateTo(newTables: Collection<Table>, out: MutableList<SchemaBuildingStatement>) {
    diff(
        old = this,
        new = newTables,
        compare = { a, b -> a.tableName == b.tableName },
        add = {
            out.add(CreateTable(it))
        },
        same = { old, new ->
            old.migrateTo(new, out)
        },
        remove = {
            out.add(DropTable(it))
        }
    )
}

fun Table.migrateTo(newTable: Table, out: MutableList<SchemaBuildingStatement>) {
    assert(this.tableName == newTable.tableName)
    diff(
        old = this.columns,
        new = newTable.columns,
        compare = { a, b -> a.name == b.name },
        add = {
            out.add(AddColumn(it))
        },
        same = { old, new ->
            if (ModifyColumn.needed(old, new)) {
                out.add(ModifyColumn(old, new))
            }
        },
        remove = {
            out.add(DropColumn(it))
        }
    )
    diff(
        old = this.indices,
        new = newTable.indices,
        compare = { a, b -> a.indexName == b.indexName },
        add = {
            out.add(CreateIndex(it))
        },
        same = { old, new -> },
        remove = {
            out.add(DropIndex(it))
        }
    )
}

private inline fun <T> diff(
    old: Collection<T>,
    new: Collection<T>,
    compare: (T, T) -> Boolean,
    add: (T) -> Unit,
    same: (T, T) -> Unit,
    remove: (T) -> Unit
) {
    for (o in old) {
        val updated = new.find { n -> compare(o, n) }
        if (updated != null) {
            same(o, updated)
        } else {
            remove(o)
        }
    }
    for (n in new) {
        if (old.any { o -> compare(o, n) }) continue
        add(n)
    }
}
