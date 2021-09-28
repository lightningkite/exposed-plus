package com.lightningkite.exposedplus

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.File
import java.util.*
import kotlin.math.min

fun KSAnnotated.annotation(name: String, packageName: String = "com.lightningkite.exposedplus"): KSAnnotation? {
    return this.annotations.find {
        it.shortName.getShortName() == name &&
                it.annotationType.resolve().declaration.qualifiedName?.asString() == "$packageName.$name"
    }
}

val tables = HashMap<String, Table>()

data class Column(
    val name: String,
    val baseType: KSDeclaration,
    val kotlinType: KSType,
    val annotations: List<ResolvedAnnotation>
) {
    val isForeignKey: Boolean by lazy {
        when(kotlinType.declaration.qualifiedName!!.asString()) {
            "com.lightningkite.exposedplus.ForeignKey",
            "com.lightningkite.exposedplus.FK" -> true
            else -> false
        }
    }
    val foreignKeyType: KSType by lazy { kotlinType.arguments[2].type!!.resolve() }
    val foreignKeyFQN: String by lazy {
        kotlinType.arguments[2].type!!.resolve()
            .declaration.qualifiedName!!.asString()
    }
}
data class Table(
    val name: String,
    val primaryKey: List<Column>,
    val columns: List<Column>,
    val raw: KSClassDeclaration
) {
    val packageName: String get() = raw.packageName.asString()
    val simpleName: String get() = raw.simpleName.getShortName()
    val tableName: String get() = "${simpleName}Table"
}

data class ResolvedAnnotation(
    val type: KSClassDeclaration,
    val arguments: Map<String, Any?>
)

fun KSType.toKotlin(): String {
    return this.declaration.qualifiedName.toString() + (if(this.isMarkedNullable) "?" else "")
}

fun List<ResolvedAnnotation>.byName(
    name: String,
    packageName: String = "com.lightningkite.exposedplus"
): ResolvedAnnotation? = this.find {
    it.type.qualifiedName?.asString() == "$packageName.$name"
}

fun List<ResolvedAnnotation>.byNameNumber(
    name: String,
    packageName: String = "com.lightningkite.exposedplus"
) = byName(name)?.arguments?.values?.first() as? Int

fun KSAnnotation.resolve(): ResolvedAnnotation {
    val type = this.annotationType.resolve().declaration as KSClassDeclaration
    val params = type.primaryConstructor!!.parameters
    return ResolvedAnnotation(
        type = type,
        arguments = this.arguments.withIndex().associate {
            val paramName =
                it.value.name?.getShortName() ?: params[min(params.lastIndex, it.index)].name!!.getShortName()
            paramName to it.value.value
        }
    )
}

fun KSPropertyDeclaration.toColumn(): Column {
    val k = this.type.resolve()
    return Column(
        name = this.simpleName.getShortName(),
        baseType = k.declaration,
        kotlinType = k,
        annotations = this.annotations.map { it.resolve() }.toList()
    )
}

fun makeColumnBase(
    name: String,
    type: KSType,
    annotations: List<ResolvedAnnotation>
): String {
    return when (val qn = type.declaration.qualifiedName!!.asString()) {
        "kotlin.Byte" -> "byte(\"$name\")"
        "kotlin.UByte" -> "ubyte(\"$name\")"
        "kotlin.Short" -> "short(\"$name\")"
        "kotlin.UShort" -> "ushort(\"$name\")"
        "kotlin.Int" -> "integer(\"$name\")"
        "kotlin.UInt" -> "uinteger(\"$name\")"
        "kotlin.Long" -> "long(\"$name\")"
        "kotlin.ULong" -> "ulong(\"$name\")"
        "kotlin.Float" -> "float(\"$name\")"
        "kotlin.Double" -> "double(\"$name\")"
        "java.math.BigDecimal" -> "decimal(\"$name\", ${annotations.byNameNumber("Precision") ?: 10}, ${annotations.byNameNumber("Scale") ?: 2})"
        "kotlin.Char" -> "char(\"$name\")"
        "kotlin.String" -> "varchar(\"$name\", ${annotations.byNameNumber("Size") ?: 255})"
        "kotlin.ByteArray" -> "binary(\"$name\", ${annotations.byNameNumber("Size") ?: 255})"
        "org.jetbrains.exposed.sql.statements.api.ExposedBlob" -> "blob(\"$name\")"
        "java.util.UUID" -> "uuid(\"$name\")"
        "kotlin.Boolean" -> "bool(\"$name\")"
        else -> {
            val baseType = type.declaration
            if(baseType is KSClassDeclaration && baseType.classKind == ClassKind.ENUM_CLASS) "enumeration(\"$name\", ${qn}::class)"
            else throw IllegalArgumentException("Cannot convert ${baseType.qualifiedName?.asString()} to column")
        }
    }
}

fun Column.rawColumnType(): String = if(isForeignKey) {
    foreignKeyType.let {
        val typeName = it.declaration.qualifiedName!!.asString()
        tables[typeName]!!.primaryKey.single().rawColumnType()
    }
} else {
    kotlinType.declaration.qualifiedName!!.asString()
}

fun Column.columnType(): String = if(isForeignKey) {
    foreignKeyType.let {
        val typeName = it.declaration.qualifiedName!!.asString()
        "ForeignKeyColumn<${typeName}Table, ${typeName}Columns, ${typeName}, ${(tables[typeName] ?: throw IllegalStateException("Could not find table for ${typeName}")).primaryKey.single().rawColumnType()}>"
    }
} else {
    "Column<${kotlinType.declaration.qualifiedName!!.asString()}>"
}

fun Column.makeColumn() = buildString {
    if(isForeignKey) {
        foreignKeyType.let {
            val typeName = it.declaration.qualifiedName!!.asString()
            val pkCol = tables[typeName]!!.primaryKey.single()
            val base = makeColumnBase(name, pkCol.kotlinType, pkCol.annotations)
            append("ForeignKeyColumn(${base}.references(${typeName}Table.id), ${typeName}Table)")
        }
    } else {
        append(makeColumnBase(name, kotlinType, annotations))
    }
    if(kotlinType.isMarkedNullable) append(".nullable()")
    if(annotations.byName("AutoIncrement") != null) append(".autoIncrement()")
    if(annotations.byName("Index") != null) append(".index()")
}

fun Column.assignment(): String {
    return when(kotlinType.declaration.qualifiedName!!.asString()) {
        "com.lightningkite.exposedplus.ForeignKey",
        "com.lightningkite.exposedplus.FK" -> "$name = ForeignKey(row[$name.key], $name)"
        else -> "$name = row[$name]"
    }
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
                    primaryKey = primaryKeys,
                    columns = columns,
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
                    out.appendLine("package ${it.packageName}")
                    out.appendLine("")
                    out.appendLine("import com.lightningkite.exposedplus.*")
                    out.appendLine("import org.jetbrains.exposed.sql.*")
                    out.appendLine("")
                    out.appendLine("interface ${it.simpleName}Columns : ResultMapper<${it.simpleName}> {")
                    for(col in it.columns) {
                        out.appendLine("    val ${col.name}: ${col.columnType()}")
                    }
                    out.appendLine("}")
                    out.appendLine("")
                    out.appendLine("object ${it.simpleName}Table : ResultMappingTable<${it.simpleName}Columns, ${it.simpleName}, ${it.primaryKey.single().rawColumnType()}>(), ${it.simpleName}Columns {")
                    out.appendLine("    override val primaryKeyColumn get() = ${it.primaryKey.single().name}")
                    for(col in it.columns) {
                        out.appendLine("    override val ${col.name}: ${col.columnType()} = ${col.makeColumn()}")
                    }
                    out.appendLine("    ")
                    out.appendLine("    override val convert: (row: ResultRow) -> ${it.simpleName} = { row ->")
                    out.appendLine("       ${it.simpleName}(${
                        it.columns.joinToString { it.assignment() }
                    })")
                    out.appendLine("    }")
                    out.appendLine("    ")
                    out.appendLine("    override fun alias(name: String) = MyAlias((this as Table).alias(name) as Alias<${it.simpleName}Table>)")
                    out.appendLine("    class MyAlias(val alias: Alias<${it.simpleName}Table>) : ${it.simpleName}Columns {")
                    out.appendLine("        override val convert: (row: ResultRow) -> ${it.simpleName} = ${it.simpleName}Table.convert")
                    for(col in it.columns) {
                        if(col.isForeignKey) {
                            out.appendLine("        override val ${col.name}: ${col.columnType()} get() = ForeignKeyColumn(alias[${it.simpleName}Table.${col.name}.key], ${col.foreignKeyFQN}Table)")
                        } else {
                            out.appendLine("        override val ${col.name}: ${col.columnType()} get() = alias[${it.simpleName}Table.${col.name}]")
                        }
                    }
                    out.appendLine("    }")
//                        override val id get() = alias[ExampleTable.primaryKeyColumn]
//                        override val testValue get() = alias[ExampleTable.testValue]
//                        override val convert: (row: ResultRow) -> Instance get() = ExampleTable.convert
//                    }
                    out.appendLine("")
                    out.appendLine("}")
                    out.appendLine("val ${it.simpleName}.Companion.table: ${it.simpleName}Table get() = ${it.simpleName}Table")
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