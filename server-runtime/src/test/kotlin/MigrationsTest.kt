package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test

class MigrationsTest {
    val db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

    @Test
    fun test() = transaction(db) {
        val startSchema = VirtualSchema()
        val endSchema = VirtualSchema(
            PrimitiveTestModel.table,
            Employee.table,
            Company.table,
            ContractsFor.table,
        )
        val out = ArrayList<SchemaBuildingStatement>()
        startSchema.migrateTo(endSchema, out)
        println(out.joinToString("\n"))
    }

}