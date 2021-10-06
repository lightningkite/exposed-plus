package com.lightningkite.exposedplus

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

val tables = HashMap<String, Table>()

class TableGenerator(val codeGenerator: CodeGenerator, val logger: KSPLogger, val generateKeys: Boolean, val generateExposed: Boolean) : SymbolProcessor {
    val deferredSymbols = ArrayList<KSClassDeclaration>()
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getNewFiles()
            .flatMap { it.declarations }
            .mapNotNull { it as? KSClassDeclaration }
            .filter { it.annotation("TableName") != null }
            .map{
                val newTable = Table(
                    declaration = it
                )
                tables[it.qualifiedName!!.asString()] = newTable
                tables[it.simpleName.asString()] = newTable
                newTable
            }
            .toList()
            .forEach {
                if(generateKeys) {
                    codeGenerator.createNewFile(
                        dependencies = it.declaration.containingFile?.let { Dependencies(false, it) }
                            ?: Dependencies.ALL_FILES,
                        packageName = it.packageName,
                        fileName = it.simpleName + "Key"
                    ).bufferedWriter().use { out ->
                        it.writeKeyFile(TabAppendable(out))
                    }
                }
                if(generateExposed) {
                    codeGenerator.createNewFile(
                        dependencies = it.declaration.containingFile?.let { Dependencies(false, it) }
                            ?: Dependencies.ALL_FILES,
                        packageName = it.packageName,
                        fileName = it.simpleName + "Table"
                    ).bufferedWriter().use { out ->
                        it.writeExposedFile(TabAppendable(out))
                    }
                }
            }
        if(generateExposed) {
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
        return TableGenerator(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            generateKeys = environment.options["generateKeys"].toBoolean(),
            generateExposed = environment.options["generateExposed"].toBoolean()
        )
    }
}