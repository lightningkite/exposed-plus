package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun main() {
    val db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Company.table, Employee.table)
        Employee.table.select()
            .filter { it.company.value.name eq "Test" }
            .filter { it.company.value.id greaterEq 2 }
            .forEach { println(it) }
    }
}