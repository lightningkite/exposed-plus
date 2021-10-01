package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    val db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Company.table, Employee.table)

        Company.table.insert(Company(id = -1L, name = "Test"))
        Company.table.insert(Company(id = -1L, name = "Test 2"))
        val testCompany = Company.table.all().filter { it.name eq "Test" }.single()
        val test2Company = Company.table.all().filter { it.name eq "Test 2" }.single()

        Employee.table.insert(
            Employee(
            id = -1L,
            name = "Gerry",
            company = testCompany.key,
            location = LatLong(40.0, 40.0)
        ))
        Employee.table.insert(
            Employee(
            id = -1L,
            name = "Gerald",
            company = test2Company.key,
            location = LatLong(41.0, 40.0)
        ))

        Employee.table.all()
            .filter { it.company.value.name eq "Test" }
            .forEach { println(it) }

        Employee.table.all()
            .mapPair { it.id to it.company.id }
            .forEach { println(it) }

        Employee.table.all()
            .mapSingle { it.id.sum() }
            .forEach { println("Sum is $it") }

        Employee.table.all()
            .sumBy { it.id }
            .let { println("Sum is $it") }

        Employee.table.all()
            .mapSingle { it.id }
            .sum()
            .let { println("Sum is $it") }

        Employee.table.all()
            .sortedByDescending { it.id }
            .forEach { println(it) }

//        Employee.table.slice(Employee.table.id.count(), Employee.table.company.id)
//            .selectAll()
//            .groupBy(Employee.table.company.id)

//        Employee.table.selectAll().count()
//        Employee.table.selectAll().sumOf {  }
//
//        Employee.table.all()
//            .groupingBy { it.company.id }
//            .eachCount()
//
//        CompanyTable.all()
//            .map { c -> c to Employee.table.all().filter { it.company.id eq c.id } }
//
//        CompanyTable.all()
//            .map { c -> c to Employee.table.all().asSequence().filter { it.company.id == c.id } }
    }
}
