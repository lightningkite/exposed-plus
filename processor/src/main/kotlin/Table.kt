package com.lightningkite.exposedplus

import com.google.devtools.ksp.symbol.KSClassDeclaration

data class Table(
    val name: String,
    val sqlName: String,
    val schemaName: String? = null,
    val primaryKey: List<Field>,
    val fields: List<Field>,
    val raw: KSClassDeclaration
) {
    val packageName: String get() = raw.packageName.asString()
    val simpleName: String get() = raw.simpleName.getShortName()
    val tableName: String get() = "${simpleName}Table"
    val hasCompoundKey: Boolean get() = primaryKey.size > 1
    val keyType: String get() = "${simpleName}Key"

    val sqlFullName: String get() = if (schemaName != null) schemaName + sqlName else sqlName
    val resolved by lazy { fields.map { it.resolve() } }
    val primaryKeys by lazy { primaryKey.map { it.resolve() } }

    fun writeKeyAccess(out: TabAppendable, tabChars: String = "") {
        out.appendLine("${tabChars}@Suppress(\"UNCHECKED_CAST\")")
        out.appendLine("${tabChars}override fun matchingKey(key: ${keyType}): Op<Boolean> = SqlExpressionBuilder.run {")
        out.append("${tabChars}    ")
        var first = true
        for (pk in primaryKeys) {
            for (col in pk.columns) {
                if (first) first = false else out.append(" and ")
                out.append('(')
                pk.writeColumnAccess(out, col)
                out.append(" eq key.")
                pk.writeValueAccess(out, col)
                out.append(')')
            }
        }
        out.appendLine()
        out.appendLine("${tabChars}}")
        out.appendLine("${tabChars}@Suppress(\"UNCHECKED_CAST\")")
        out.appendLine("${tabChars}override fun matchingKey(otherColumns: List<Column<*>>): Op<Boolean> = SqlExpressionBuilder.run {")
        out.append("${tabChars}    ")
        first = true
        var index = 0
        for (pk in primaryKeys) {
            for (col in pk.columns) {
                if (first) first = false else out.append(" and ")
                out.append('(')
                pk.writeColumnAccess(out, col)
                out.append(" eq (otherColumns[$index] as ")
                col.writeColumnType(out)
                out.append("))")
                index++
            }
        }
        out.appendLine()
        out.appendLine("${tabChars}}")
    }

    fun writeFile(out: TabAppendable) {
        var first = false
        out.appendLine("package ${packageName}")
        out.appendLine("")
        out.appendLine("import com.lightningkite.exposedplus.*")
        out.appendLine("import org.jetbrains.exposed.sql.*")
        out.appendLine("")
        out.appendLine("interface ${simpleName}Columns : BaseColumnsType<${simpleName}, ${keyType}> {")
        out.tab {
            for (col in resolved) {
                col.writePropertyDeclaration(out)
                out.appendLine()
            }
        }
        out.appendLine("}")
        out.appendLine("")
        out.appendLine("data class ${simpleName}Key(")
        out.tab {
            for (p in primaryKey) {
                out.append("val ${p.name}: ${p.kotlinType.toKotlin()}")
                out.appendLine(",")
            }
        }
        out.appendLine("): ForeignKey<${simpleName}Table, ${simpleName}Columns, ${simpleName}, ${simpleName}Key>(${simpleName}Table)")
        out.appendLine("")
        out.appendLine("data class ${simpleName}FKField(")
        out.tab {
            for (col in primaryKeys) {
                col.writePropertyDeclaration(out)
                out.appendLine(",")
            }
        }
        out.appendLine(") : ForeignKeyField<${simpleName}Table, ${simpleName}Columns, $simpleName, $keyType> {")
        out.tab {
            out.appendLine("override val mapper: ${simpleName}Table get() = ${simpleName}Table")
            out.append("override val columns: List<Column<*>> get() = listOf(")
            first = true
            for (pk in primaryKeys) {
                for (col in pk.columns) {
                    if (first) first = false else out.append(", ")
                    pk.writeColumnAccess(out, col)
                }
            }
            out.appendLine(")")
        }
        out.appendLine("}")
        out.appendLine("")
        out.appendLine("object ${simpleName}Table : ResultMappingTable<${simpleName}Columns, ${simpleName}, ${keyType}>(\"$sqlFullName\"), ${simpleName}Columns {")
        out.tab {
            out.appendLine("override val set: ColumnSet get() = this")
            for (col in resolved) {
                col.writeMainDeclaration(out, listOf())
            }
            out.append("override val primaryKey: PrimaryKey = PrimaryKey(")
            first = true
            for (pk in primaryKeys) {
                for (col in pk.columns) {
                    if (first) first = false else out.append(", ")
                    pk.writeColumnAccess(out, col)
                }
            }
            out.appendLine(")")
            out.appendLine("")
            out.append("override val selections: List<ExpressionWithColumnType<*>> = listOf(")
            first = true
            for (pk in resolved) {
                for (col in pk.columns) {
                    if (first) first = false else out.append(", ")
                    pk.writeColumnAccess(out, col)
                }
            }
            out.appendLine(")")
            out.appendLine("")
            writeKeyAccess(out, "")
            out.appendLine("")
            out.appendLine("override val convert: (row: ResultRow) -> ${simpleName} = { row ->")
            out.tab {
                out.appendLine("${simpleName}(")
                out.tab {
                    for (field in resolved) {
                        out.append("")
                        field.writeInstanceConstructionPart(out, listOf())
                        out.appendLine(",")
                    }
                }
                out.appendLine(")")
            }
            out.appendLine("}")
            out.appendLine("")
            out.appendLine("override fun split(instance: ${simpleName}): Map<Column<*>, Any?> = mapOf(")
            out.tab {
                for (f in resolved) {
                    for (col in f.columns) {
                        f.writeColumnAccess(out, col)
                        out.append(" to instance.")
                        f.writeValueAccess(out, col)
                        out.appendLine(",")
                    }
                }
            }
            out.appendLine(")")
            out.appendLine("")
            out.appendLine("@Suppress(\"UNCHECKED_CAST\")")
            out.appendLine("override fun alias(name: String) = MyAlias((this as Table).alias(name) as Alias<${simpleName}Table>)")
            out.appendLine("class MyAlias(val alias: Alias<${simpleName}Table>) : ${simpleName}Columns {")
            out.tab {

                out.appendLine("override val set: ColumnSet get() = alias")
                out.appendLine("override val convert: (row: ResultRow) -> ${simpleName} = { row ->")
                out.tab {

                    out.appendLine("${simpleName}(")
                    out.tab {
                        for (field in resolved) {
                            out.append("")
                            field.writeInstanceConstructionPart(out, listOf())
                            out.appendLine(",")
                        }
                    }
                    out.appendLine(")")
                }
                out.appendLine("}")
                for (field in resolved) {
                    field.writeAliasDeclaration(out, listOf("${simpleName}Table"))
                }
                writeKeyAccess(out, "")
                out.append("override val selections: List<ExpressionWithColumnType<*>> = listOf(")
                first = true
                for (pk in resolved) {
                    for (col in pk.columns) {
                        if (first) first = false else out.append(", ")
                        pk.writeColumnAccess(out, col)
                    }
                }
                out.appendLine(")")
            }
            out.appendLine("}")
            out.appendLine("")
        }
        out.appendLine("}")
        out.appendLine("inline val ${simpleName}.Companion.table: ${simpleName}Table get() = ${simpleName}Table")

        out.appendLine("inline val ${simpleName}.key get() = $keyType(")
        out.tab {
            for (col in primaryKeys) {
                out.append(col.name)
                out.append(" = ")
                out.append(col.name)
                out.appendLine(",")
            }
        }
        out.appendLine(")")
    }
}