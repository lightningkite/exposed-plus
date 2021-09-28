package com.lightningkite.exposedplus

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.File

val tables = HashMap<String, Table>()

fun KSPropertyDeclaration.toColumn(): Field {
    val k = this.type.resolve()
    return Field(
        name = this.simpleName.getShortName(),
        baseType = k.declaration,
        kotlinType = k,
        annotations = this.annotations.map { it.resolve() }.toList()
    )
}

var runCount = 0

class MyProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        File("/home/jivie/Projects/exposed-plus/test.log").appendText("Starting up...")
        if(runCount > 0) {
            return emptyList()
        }
        runCount++
        resolver
            .getSymbolsWithAnnotation("com.lightningkite.exposedplus.TableName")
            .mapNotNull { it as? KSClassDeclaration }
            .filter { it.annotation("TableName") != null }
            .forEach {
                val allProps = it.getAllProperties().associateBy { it.simpleName.getShortName() }
                val columns = it.primaryConstructor!!
                    .parameters
                    .filter { it.isVal || it.isVar }
                    .mapNotNull { allProps[it.name?.getShortName()] }
                    .map { it.toColumn() }
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
        File("/home/jivie/Projects/exposed-plus/test.log").appendText("Discovered tables:")
        tables.forEach { File("/home/jivie/Projects/exposed-plus/test.log").appendText(it.key + ": " + it.value) }
        tables
            .values
            .forEach {
                File("/home/jivie/Projects/exposed-plus/test.log").appendText("Creating table for ${it.simpleName}")
                codeGenerator.createNewFile(
                    dependencies = it.raw.containingFile?.let { Dependencies(false, it) } ?: Dependencies.ALL_FILES,
                    packageName = it.packageName,
                    fileName = it.simpleName + "Table"
                ).bufferedWriter().use { out ->
                    it.writeFile(TabAppendable(out))
                }
            }
        File("/home/jivie/Projects/exposed-plus/test.log").appendText("Complete.")
        return emptyList()
    }
}

class MyProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return MyProcessor(environment.codeGenerator, environment.logger)
    }
}