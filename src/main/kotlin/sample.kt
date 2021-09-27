package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*


fun Table.exampleTable(name: String) = ForeignKeyColumn(integer(name).references(ExampleTable.id), ExampleTable)

interface ExampleTableColumns : ResultMapper<ExampleTable.Instance>, HasColumnSet {
    val id: Column<Int>
    val testValue: Column<Int>
}

typealias ExampleTableFK = ForeignKey<ExampleTable, ExampleTableColumns, ExampleTable.Instance, Int>
object ExampleTable : ResultMappingTable<ExampleTableColumns, ExampleTable.Instance, Int>(), ExampleTableColumns {

    override val id = this.integer("asdf").autoIncrement()
    override val testValue = this.integer("asdf2")


    data class Instance(val id: Int, val testValue: Int)

    override val set: ColumnSet
        get() = this
    override val convert: (row: ResultRow) -> Instance
        get() = {
            Instance(
                id = it[id],
                testValue = it[testValue]
            )
        }

    @Suppress("UNCHECKED_CAST")
    override fun alias(name: String) = MyAlias((this as Table).alias(name) as Alias<ExampleTable>)
    class MyAlias(val alias: Alias<ExampleTable>) : ExampleTableColumns {
        override val set: ColumnSet get() = alias
        override val id get() = alias[ExampleTable.id]
        override val testValue get() = alias[ExampleTable.testValue]
        override val convert: (row: ResultRow) -> Instance get() = ExampleTable.convert
    }
}

object Example2Table : ResultMappingTable<HasColumnSet, Example2Table.Instance, Int>() {
    data class Instance(val id: Int, val other: ExampleTableFK)

    override val convert: (row: ResultRow) -> Instance
        get() = { row ->
            Instance(
                id = row[id],
                other = ForeignKey(row[other.key], other)
            )
        }

    override val id = integer("asdf").autoIncrement()
    val other = exampleTable("other")
    override fun alias(name: String): HasColumnSet = object: HasColumnSet {
        override val set: ColumnSet
            get() = this@Example2Table
    }
}

fun test(db: Database) {
    SchemaUtils.create(ExampleTable)

    Example2Table.select()
        .filter { it.id greaterEq 20 }
        .map { it.other }
        .filter { it.testValue less 20 }

    Example2Table.select()
        .filter { it.other.value.testValue less 20 }
        .kotlin()
        .filter { it.other.value.testValue < 20 }
        .first()
        .other.value.testValue

    Example2Table.select()
        .filter { it.other.key less 20 }
        .first()
        .other.key
}
