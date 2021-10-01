package com.lightningkite.exposedplus

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.lang.Exception

val tables = HashMap<String, Table>()
class TableGenerator(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    val deferredSymbols = ArrayList<KSAnnotated>()
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getNewFiles()
            .flatMap { it.declarations }
            .mapNotNull { it as? KSClassDeclaration }
            .filter { it.annotation("TableName") != null }
            .map {
                val columns = it.fields()
                val primaryKeys = columns.filter { it.annotations.byName("PrimaryKey") != null }
                val newTable = Table(
                    name = it.qualifiedName!!.asString(),
                    sqlName = it.annotation("TableName")!!.resolve().arguments["tableName"]?.toString()
                        ?.takeUnless { it.isBlank() } ?: it.simpleName.asString(),
                    schemaName = it.annotation("TableName")!!.resolve().arguments["databaseName"]?.toString()
                        ?.takeUnless { it.isBlank() },
                    primaryKey = primaryKeys,
                    fields = columns,
                    raw = it
                )
                tables[it.qualifiedName!!.asString()] = newTable
                tables[it.simpleName.asString()] = newTable
                newTable
            }
            .toList()
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
            .asSequence()
            .filter { !it.handled }
            .forEach {
                it.handled = true
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
        return deferredSymbols
    }
}

class KeyGenerator(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getNewFiles()
            .flatMap { it.declarations }
            .mapNotNull { it as? KSClassDeclaration }
            .filter { it.annotation("TableName") != null }
            .map {
                logger.info("Creating key for ${it.simpleName}")
                codeGenerator.createNewFile(
                    dependencies = it.containingFile?.let { Dependencies(false, it) } ?: Dependencies.ALL_FILES,
                    packageName = it.packageName.asString(),
                    fileName = it.simpleName.asString() + "Key"
                ).bufferedWriter().use { out ->
                    val columns = it.fields()
                    val primaryKeys = columns.filter { it.annotations.byName("PrimaryKey") != null }
                }
            }
        logger.info("Complete.")
        return emptyList()
    }
}

class MyProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return TableGenerator(environment.codeGenerator, environment.logger)
    }
}