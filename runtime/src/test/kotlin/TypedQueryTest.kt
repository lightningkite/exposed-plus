package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals

class TypedQueryTest {
    val db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    class MyDatabase {
        init {
            SchemaUtils.create(Company.table, Employee.table, ContractsFor.table, PrimitiveTestModel.table)
        }

        val companyLK: Company = Company.table.insertAndGetKey(Company(id = -1L, name = "Lightning Kite")).value
        val companyE3: Company = Company.table.insertAndGetKey(Company(id = -1L, name = "Emergent Three")).value
        val dan: Employee = Employee.table.insertAndGetKey(
                Employee(
                    id = -1L,
                    name = "Dan",
                    company = companyLK.key,
                    manager = null,
                    location = LatLong(40.0, 40.0)
                )
            ).value
        val lorenzo: Employee = Employee.table.insertAndGetKey(
                Employee(
                    id = -1L,
                    name = "Lorenzo",
                    company = companyLK.key,
                    manager = Employee.table.all().filter { it.name eq "Dan" }.single().key,
                    location = LatLong(40.0, 40.0)
                )
            ).value
        val joseph: Employee = Employee.table.insertAndGetKey(
                Employee(
                    id = -1L,
                    name = "Joseph",
                    company = companyLK.key,
                    manager = Employee.table.all().filter { it.name eq "Lorenzo" }.single().key,
                    location = LatLong(40.0, 40.0)
                )
            ).value
        val preston: Employee = Employee.table.insertAndGetKey(
                Employee(
                    id = -1L,
                    name = "Preston",
                    company = companyE3.key,
                    manager = null,
                    location = LatLong(41.0, 40.0)
                )
            ).value
        val employees = listOf(dan, lorenzo, joseph, preston)
        val companies = listOf(companyLK, companyE3)
    }


    @Test
    fun test_asSequence() = transaction {
        val testData = MyDatabase()
        val results = Employee.table.all().asSequence().toList()
        assertEquals(testData.employees, results)
    }

    @Test
    fun test_toList() = transaction {
        val testData = MyDatabase()
        val results = Employee.table.all().toList()
        assertEquals(testData.employees, results)
    }

    @Test
    fun test_forEach() = transaction {
        val testData = MyDatabase()
        val results = ArrayList<Employee>()
        Employee.table.all().forEach {
            results.add(it)
        }
        assertEquals(testData.employees, results)
    }

    @Test
    fun test_take() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.take(2),
            Employee.table.all().take(2).toList()
        )
    }

    @Test
    fun test_drop() = transaction {
        addLogger(StdOutSqlLogger)
        val testData = MyDatabase()
        assertEquals(
            testData.employees.drop(2),
            Employee.table.all().drop(2).toList()
        )
    }

    @Test
    fun test_single() = transaction {
        val testData = MyDatabase()
        var thrown = false
        try {
            Employee.table.all().single()
        } catch(e: Exception) {
            thrown = true
        }
        assert(thrown)
    }

    @Test
    fun test_first() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.sortedBy { it.name }.first(),
            Employee.table.all().sortedBy { it.name }.first()
        )
    }

    @Test
    fun test_last() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.sortedBy { it.name }.last(),
            Employee.table.all().sortedBy { it.name }.last()
        )
    }

    @Test
    fun test_singleOrNull() = transaction {
        val testData = MyDatabase()
        assertEquals(
            null,
            Employee.table.all().singleOrNull()
        )
    }

    @Test
    fun test_firstOrNull() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.sortedBy { it.name }.firstOrNull(),
            Employee.table.all().sortedBy { it.name }.firstOrNull()
        )
    }

    @Test
    fun test_lastOrNull() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.sortedBy { it.name }.lastOrNull(),
            Employee.table.all().sortedBy { it.name }.lastOrNull()
        )
    }

    @Test
    fun test_count() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.count().toLong(),
            Employee.table.all().count()
        )
    }

    @Test
    fun test_filter() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.filter { it.id > 2 },
            Employee.table.all().filter { it.id greater 2 }.toList()
        )
    }

    @Test
    fun test_sum() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.map { it.id }.sum(),
            Employee.table.all().mapSingle { it.id }.sum()
        )
    }

    @Test
    fun test_sumOf() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.sumOf { it.id },
            Employee.table.all().sumOf { it.id }
        )
    }

    @Test
    fun test_sortedBy() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.sortedBy { it.name },
            Employee.table.all().sortedBy { it.name }.toList()
        )
    }

    @Test
    fun test_sortedByDescending() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.sortedByDescending { it.name },
            Employee.table.all().sortedByDescending { it.name }.toList()
        )
    }

    @Test
    fun test_mapSingle() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.map { it.name },
            Employee.table.all().mapSingle { it.name }.toList()
        )
    }

    @Test
    fun test_mapPair() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.map { it.name to it.id },
            Employee.table.all().mapPair { it.name to it.id }.toList()
        )
    }

    @Test
    fun test_mapFk() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.map { it.company.value },
            Employee.table.all().mapFk { it.company }.toList()
        )
    }

    @Test
    fun test_prefetch() = transaction {
        val testData = MyDatabase()
        val results = Employee.table.all().prefetch { it.company }.toList()
        for(r in results)
            assert(r.company.untypedValue != null)
    }

    @Test
    fun test_mapCompound() = transaction {
        val testData = MyDatabase()
        assertEquals(
            testData.employees.map { it.location },
            Employee.table.all().mapCompound { it.location }.toList()
        )
    }
}