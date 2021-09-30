package com.lightningkite.exposedplus

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

val tables = HashMap<String, Table>()

var runCount = 0

class MyProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("Starting up...")
        if(runCount > 0) {
            return emptyList()
        }
        runCount++
        resolver
            .getSymbolsWithAnnotation("com.lightningkite.exposedplus.TableName")
            .mapNotNull { it as? KSClassDeclaration }
            .filter { it.annotation("TableName") != null }
            .forEach {
                val columns = it.fields()
                val primaryKeys = columns.filter { it.annotations.byName("PrimaryKey") != null }
                tables[it.qualifiedName!!.asString()] = Table(
                    name = it.qualifiedName!!.asString(),
                    sqlName = it.annotation("TableName")!!.resolve().arguments["tableName"]?.toString()?.takeUnless { it.isBlank() } ?: it.simpleName.asString(),
                    schemaName = it.annotation("TableName")!!.resolve().arguments["databaseName"]?.toString()?.takeUnless { it.isBlank() },
                    primaryKey = primaryKeys,
                    fields = columns,
                    raw = it
                )
            }
        logger.info("Discovered tables:")
        tables.forEach { logger.info(it.key + ": " + it.value) }
        tables
            .values
            .forEach {
                logger.info("Creating table for ${it.simpleName}")
                codeGenerator.createNewFile(
                    dependencies = it.raw.containingFile?.let { Dependencies(false, it) } ?: Dependencies.ALL_FILES,
                    packageName = it.packageName,
                    fileName = it.simpleName + "Table"
                ).bufferedWriter().use { out ->
                    it.writeFile(TabAppendable(out))
                }
            }
        subTableCache.values
            .forEach {
                logger.info("Creating subtable for ${it.simpleName}")
                codeGenerator.createNewFile(
                    dependencies = it.raw.containingFile?.let { Dependencies(false, it) } ?: Dependencies.ALL_FILES,
                    packageName = it.packageName,
                    fileName = it.simpleName + "SubTable"
                ).bufferedWriter().use { out ->
                    it.writeFile(TabAppendable(out))
                }
            }
        logger.info("Complete.")
        return emptyList()
    }
}

class MyProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return MyProcessor(environment.codeGenerator, environment.logger)
    }
}