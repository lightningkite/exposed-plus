package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.ReferenceOption

class VirtualSchema() {
    val schemas = HashSet<String>()
    val tables = HashMap<String, VirtualTable>()

    constructor(tables: List<Table>):this() {
        for(table in tables) {
            this.tables[table.tableName] = VirtualTable(table)
            table.tableName.substringBefore('.', "").takeUnless { it.isBlank() }?.let { this.schemas.add(it) }
        }
    }
    constructor(vararg tables: Table):this(tables.toList())
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
    constructor(table: Table) : this(
        name = table.tableName,
        columns = table.columns.map { VirtualColumn(it) },
        primaryKeys = table.primaryKey!!.columns.map { VirtualColumn(it) },
        indices = table.indices.map { VirtualIndex(it) }
    )

    val table: Table by lazy {
        object : Table() {
            init {
                for (col in this@VirtualTable.columns) {
                    val c = registerColumn<Any?>(
                        name = col.name,
                        type = col.type
                    )
                    col.sqlDefault?.let {
                        @Suppress("UNCHECKED_CAST")
                        c.defaultExpression(it as Expression<Any?>)
                    }
                    col.foreignKey?.let {
                        c.foreignKey = ForeignKeyConstraint(
                            target =
                        )
                    }
                }
                for (i in this@VirtualTable.indices) {
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

data class VirtualForeignKeyConstraint(
    val targetTable: VirtualTable,
    val target: VirtualColumn,
    val from: VirtualColumn,
    val onUpdate: ReferenceOption?,
    val onDelete: ReferenceOption?,
    val name: String?
) {
    constructor(constraint: ForeignKeyConstraint):this(
        targetTable = VirtualTable(constraint.target.table),
        target = VirtualColumn(constraint.target),
        from = VirtualColumn(constraint.from),
        onUpdate = constraint.updateRule,
        onDelete = constraint.deleteRule,
        name = constraint.customFkName,
    )
}

data class VirtualColumn(
    var name: String,
    var type: IColumnType,
    var foreignKey: VirtualForeignKeyConstraint? = null,
    var sqlDefault: Expression<*>? = null
) {
    constructor(column: Column<*>) : this(
        name = column.name,
        type = column.columnType,
        foreignKey = column.foreignKey?.let { VirtualForeignKeyConstraint(it) },
        sqlDefault = null /*TODO*/,
    )

    fun toColumn(table: VirtualTable): Column<*> = table.table.columns.find { it.name == name }!!
}

data class VirtualIndex(
    var name: String,
    var on: List<VirtualColumn>,
    var unique: Boolean = false,
    var indexType: String? = null
) {
    constructor(index: Index) : this(
        name = index.indexName,
        on = index.columns.map { VirtualColumn(it) },
        unique = index.unique,
        indexType = index.indexType
    )

    fun toIndex(table: VirtualTable): Index = table.table.indices.find { it.indexName == name }!!
}

data class CreateSchema(val schema: Schema) : SchemaBuildingStatement {
    override val statements: List<String> get() = schema.createStatement()
    override fun reverse(): SchemaBuildingStatement = DropSchema(schema)
    override fun apply(to: VirtualSchema) {
        to.schemas.add(schema.identifier)
    }
}

data class DropSchema(val schema: Schema) : SchemaBuildingStatement {
    override val statements: List<String> get() = schema.dropStatement(false)
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

data class AddColumn(val table: VirtualTable, val column: VirtualColumn) : SchemaBuildingStatement {
    override val statements: List<String> get() = column.toColumn(table).createStatement()
    override fun reverse(): SchemaBuildingStatement = DropColumn(table, column)
    override fun apply(to: VirtualSchema) {
        to.tables[table.name] = to.tables[table.name]!!.let { it.copy(columns = it.columns + column) }
    }
}

data class DropColumn(val table: VirtualTable, val column: VirtualColumn) : SchemaBuildingStatement {
    override val statements: List<String> get() = column.toColumn(table).dropStatement()
    override fun reverse(): SchemaBuildingStatement = AddColumn(table, column)
    override fun apply(to: VirtualSchema) {
        to.tables[table.name] =
            to.tables[table.name]!!.let { it.copy(columns = it.columns.filter { it.name != column.name }) }
    }
}

data class ModifyColumn(val table: VirtualTable, val from: VirtualColumn, val to: VirtualColumn) :
    SchemaBuildingStatement {
    override val statements: List<String>
        get() = to.toColumn(table).modifyStatements(
            nullabilityChanged = from.type.nullable != to.type.nullable,
            autoIncrementChanged = from.type.isAutoInc != to.type.isAutoInc,
            defaultChanged = from.sqlDefault.toString() != to.sqlDefault.toString()
        )

    override fun reverse(): SchemaBuildingStatement = ModifyColumn(table, to, from)
    override fun apply(to: VirtualSchema) {
        to.tables[table.name] =
            to.tables[table.name]!!.let { it.copy(columns = it.columns.filter { it.name != from.name }.plus(this.to)) }
    }

    companion object {
        fun needed(from: VirtualColumn, to: VirtualColumn): Boolean {
            return from.type.nullable != to.type.nullable ||
                    from.type.isAutoInc != to.type.isAutoInc ||
                    from.sqlDefault.toString() != to.sqlDefault.toString()
        }
    }
}

fun VirtualSchema.migrateTo(newTables: VirtualSchema, out: MutableList<SchemaBuildingStatement>) {
    diff(
        old = this.tables.values,
        new = newTables.tables.values,
        compare = { a, b -> a.name == b.name },
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

fun VirtualTable.migrateTo(newTable: VirtualTable, out: MutableList<SchemaBuildingStatement>) {
    assert(this.name == newTable.name)
    diff(
        old = this.columns,
        new = newTable.columns,
        compare = { a, b -> a.name == b.name },
        add = {
            out.add(AddColumn(this, it))
        },
        same = { old, new ->
            if (ModifyColumn.needed(old, new)) {
                out.add(ModifyColumn(this, old, new))
            }
        },
        remove = {
            out.add(DropColumn(this, it))
        }
    )
    diff(
        old = this.indices,
        new = newTable.indices,
        compare = { a, b -> a.name == b.name },
        add = {
            out.add(CreateIndex(this, it))
        },
        same = { old, new -> },
        remove = {
            out.add(DropIndex(this, it))
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
