package com.lightningkite.exposedplus

import com.lightningkite.exposedplus.*
import org.jetbrains.exposed.sql.*

interface EmployeeColumns : ResultMapper<Employee>, BaseColumnsType<kotlin.Long> {
    val id: Column<kotlin.Long>
    val name: Column<kotlin.String>
    val company: CompanyFKField
}

object EmployeeTable : ResultMappingTable<EmployeeColumns, Employee, kotlin.Long>(), EmployeeColumns {
    override val id: Column<kotlin.Long> = long("id").autoIncrement()
    override val name: Column<kotlin.String> = varchar("name", 255)
    override val company: CompanyFKField = object : CompanyFKField {
        override val id: Column<Long> = long("company_id").references(CompanyTable.id)
    }
    override val primaryKey: PrimaryKey = PrimaryKey(id)
    
    override fun matchingKey(key: kotlin.Long): Op<Boolean> = SqlExpressionBuilder.run { id eq key }
    override fun matchingKey(otherColumns: List<Column<*>>): Op<Boolean> = SqlExpressionBuilder.run { id eq (otherColumns[0] as Column<kotlin.Long>) }
    
    override val convert: (row: ResultRow) -> Employee = { row ->
       Employee(id = row[id], name = row[name], company = ForeignKey(row[company.id], company))
    }
    
    override fun alias(name: String) = MyAlias((this as Table).alias(name) as Alias<EmployeeTable>)
    class MyAlias(val alias: Alias<EmployeeTable>) : EmployeeColumns {
        override val convert: (row: ResultRow) -> Employee = EmployeeTable.convert
        override val id: Column<kotlin.Long> get() = alias[EmployeeTable.id]
        override val name: Column<kotlin.String> get() = alias[EmployeeTable.name]
        override val company: CompanyFKField = object : CompanyFKField {
            override val id: Column<Long> get() = alias[EmployeeTable.company.id]
        }
        override fun matchingKey(key: kotlin.Long): Op<Boolean> = SqlExpressionBuilder.run { id eq key }
        override fun matchingKey(otherColumns: List<Column<*>>): Op<Boolean> = SqlExpressionBuilder.run { id eq (otherColumns[0] as Column<Long>) }
    }

}
val Employee.Companion.table: EmployeeTable get() = EmployeeTable
