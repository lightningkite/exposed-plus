package com.lightningkite.exposedplus

import com.lightningkite.exposedplus.*
import org.jetbrains.exposed.sql.*

interface CompanyColumns : ResultMapper<Company>, BaseColumnsType<kotlin.Long> {
    val id: Column<kotlin.Long>
    val name: Column<kotlin.String>

}
interface CompanyFKField: ForeignKeyColumn<CompanyTable, CompanyColumns, Company, kotlin.Long> {
    val id: Column<kotlin.Long>
    override val mapper: CompanyTable get() = CompanyTable
    override val columns: List<Column<*>> get() = listOf(id)
}

object CompanyTable : ResultMappingTable<CompanyColumns, Company, kotlin.Long>(), CompanyColumns {
    override val id: Column<kotlin.Long> = long("id").autoIncrement()
    override val name: Column<kotlin.String> = varchar("name", 255)
    override val primaryKey: PrimaryKey = PrimaryKey(id)
    
    override fun matchingKey(key: kotlin.Long): Op<Boolean> = SqlExpressionBuilder.run { id eq key }
    override fun matchingKey(otherColumns: List<Column<*>>): Op<Boolean> = SqlExpressionBuilder.run { id eq (otherColumns[0] as Column<kotlin.Long>) }
    
    override val convert: (row: ResultRow) -> Company = { row ->
       Company(id = row[id], name = row[name])
    }
    
    override fun alias(name: String) = MyAlias((this as Table).alias(name) as Alias<CompanyTable>)
    class MyAlias(val alias: Alias<CompanyTable>) : CompanyColumns {
        override val convert: (row: ResultRow) -> Company = CompanyTable.convert
        override val id: Column<kotlin.Long> get() = alias[CompanyTable.id]
        override val name: Column<kotlin.String> get() = alias[CompanyTable.name]
        override fun matchingKey(key: kotlin.Long): Op<Boolean> = SqlExpressionBuilder.run { id eq key }
        override fun matchingKey(otherColumns: List<Column<*>>): Op<Boolean> = SqlExpressionBuilder.run { id eq (otherColumns[0] as Column<Long>) }
    }

}
val Company.Companion.table: CompanyTable get() = CompanyTable
