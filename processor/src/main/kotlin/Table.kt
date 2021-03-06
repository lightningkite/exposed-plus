package com.lightningkite.exposedplus

import com.google.devtools.ksp.symbol.KSClassDeclaration

data class Table(
    val declaration: KSClassDeclaration
) {
    val declaredFields: List<Field> = declaration.fields()
    val declaredPrimaryKeys: List<Field> = declaredFields.filter { it.annotations.byName("PrimaryKey") != null }
    val schemaName: String? = declaration.annotation("TableName")!!.resolve().arguments["databaseName"]?.toString()
        ?.takeUnless { it.isBlank() }
    val sqlName: String = declaration.annotation("TableName")!!.resolve().arguments["tableName"]?.toString()
        ?.takeUnless { it.isBlank() } ?: declaration.simpleName.asString()
    val packageName: String get() = declaration.packageName.asString()
    val simpleName: String get() = declaration.simpleName.getShortName()
    val tableName: String get() = "${simpleName}Table"
    val hasSingleKey: Boolean get() = resolvedPrimaryKeys.size == 1 && resolvedPrimaryKeys.single().columns.size == 1
    val keyType: String get() = "${simpleName}Key"

    val sqlFullName: String get() = if (schemaName != null) schemaName + sqlName else sqlName
    val resolvedFields: List<ResolvedField> by lazy { declaredFields.map { it.resolve() } }
    val resolvedForeignKeys: List<ResolvedField.ForeignKey> by lazy {
        resolvedFields
            .filter { it is ResolvedField.ForeignKey }
            .map { it as ResolvedField.ForeignKey }
    }
    val resolvedPrimaryKeys: List<ResolvedField> by lazy { declaredPrimaryKeys.map { it.resolve() } }

    fun writeKeyAccess(out: TabAppendable, tabChars: String = "") {
        out.appendLine("${tabChars}@Suppress(\"UNCHECKED_CAST\")")
        out.appendLine("${tabChars}override fun matchingKey(key: ${keyType}): Op<Boolean> = SqlExpressionBuilder.run {")
        out.append("${tabChars}    ")
        var first = true
        for (pk in resolvedPrimaryKeys) {
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
        for (pk in resolvedPrimaryKeys) {
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

    fun writeKeyFile(out: TabAppendable) {
        out.appendLine("package ${packageName}")
        out.appendLine("")
        out.appendLine("data class ${simpleName}Key(")
        out.tab {
            for (p in declaredPrimaryKeys) {
                out.append("val ${p.name}: ${p.kotlinType.toKotlin()}")
                out.appendLine(",")
            }
        }
        out.appendLine("): ForeignKey<${simpleName}>() {")
        out.tab {
            out.appendLine("object Handle: KeyHandle<$simpleName>")
            out.appendLine("override val handle = Handle")
        }
        out.appendLine("}")
    }

    fun writeExposedFile(out: TabAppendable) {
        var first = false
        out.appendLine("package ${packageName}")
        out.appendLine("")
        out.appendLine("import com.lightningkite.exposedplus.*")
        out.appendLine("import org.jetbrains.exposed.sql.*")
        out.appendLine("import org.jetbrains.exposed.sql.transactions.transaction")
        out.appendLine("import org.jetbrains.exposed.sql.ReferenceOption")
        out.appendLine("")
        out.appendLine("interface ${simpleName}Columns : BaseColumnsType<${simpleName}, ${keyType}> {")
        out.tab {
            for (col in resolvedFields) {
                col.writePropertyDeclaration(out)
                out.appendLine()
            }
        }
        out.appendLine("}")
        out.appendLine("")
        out.appendLine("data class ${simpleName}FKField(")
        out.tab {
            for (col in resolvedPrimaryKeys) {
                col.writePropertyDeclaration(out)
                out.appendLine(",")
            }
        }
        out.appendLine(") : ForeignKeyField<${simpleName}Table> {")
        out.tab {
            out.appendLine("override val mapper: ${simpleName}Table get() = ${simpleName}Table")
            out.append("override val columns: List<Column<*>> get() = listOf(")
            first = true
            for (pk in resolvedPrimaryKeys) {
                for (col in pk.columns) {
                    if (first) first = false else out.append(", ")
                    pk.writeColumnAccess(out, col)
                }
            }
            out.appendLine(")")
        }
        out.appendLine("}")
        out.appendLine("")
        out.appendLine("data class ${simpleName}FKFieldNullable(")
        val nullablePks = resolvedPrimaryKeys.map { it.forceNullable() }
        out.tab {
            for (col in nullablePks) {
                col.writePropertyDeclaration(out)
                out.appendLine(",")
            }
        }
        out.appendLine(") : ForeignKeyField<${simpleName}Table> {")
        out.tab {
            out.appendLine("override val mapper: ${simpleName}Table get() = ${simpleName}Table")
            out.append("override val columns: List<Column<*>> get() = listOf(")
            first = true
            for (pk in nullablePks) {
                for (col in pk.columns) {
                    if (first) first = false else out.append(", ")
                    pk.writeColumnAccess(out, col)
                }
            }
            out.appendLine(")")
        }
        out.appendLine("}")
        out.appendLine("")
        out.append("object ${simpleName}Table : ResultMappingTable<${simpleName}Columns, ${simpleName}, ${keyType}>(\"$sqlFullName\"), ${simpleName}Columns")
        if (hasSingleKey) {
            val p = resolvedPrimaryKeys.first()
            val k = p.columns.first()
            out.append(", HasSingleColumnPrimaryKey<")
            k.writeValueType(out)
            out.append(", $keyType>")
        }
        out.appendLine(" {")
        out.tab {
            out.appendLine("init { register(${simpleName}Key.Handle) }")
            out.appendLine("override val set: ColumnSet get() = this")
            for (col in resolvedFields) {
                col.writeMainDeclaration(out, listOf())
            }
            out.append("override val primaryKeyColumns: List<Column<*>> get() = listOf(")
            first = true
            for (pk in resolvedPrimaryKeys) {
                for (col in pk.columns) {
                    if (first) first = false else out.append(", ")
                    pk.writeColumnAccess(out, col)
                }
            }
            out.appendLine(")")
            out.append("override val primaryKey: PrimaryKey = PrimaryKey(")
            first = true
            for (pk in resolvedPrimaryKeys) {
                for (col in pk.columns) {
                    if (first) first = false else out.append(", ")
                    pk.writeColumnAccess(out, col)
                }
            }
            out.appendLine(")")
            out.appendLine()
            out.append("override val selections: List<ExpressionWithColumnType<*>> = listOf(")
            first = true
            for (pk in resolvedFields) {
                for (col in pk.columns) {
                    if (first) first = false else out.append(", ")
                    pk.writeColumnAccess(out, col)
                }
            }
            out.appendLine(")")
            out.appendLine("")
            writeKeyAccess(out, "")
            out.appendLine("")
            val fks = resolvedFields.mapNotNull { it as? ResolvedField.ForeignKey }
            if (fks.isNotEmpty()) {
                out.appendLine("override fun getForeignKeyUntyped(key: ForeignKeyField<*>): (($simpleName) -> ForeignKey<*>?)? = when(key) {")
                out.tab {
                    for (fk in fks) {
                        out.appendLine("${fk.name} -> {{ it.${fk.name} }}")
                    }
                    out.appendLine("else -> null")
                }
                out.appendLine("}")
                out.appendLine("")
            }
            out.appendLine("override val convert: (row: ResultRow) -> ${simpleName} = { row ->")
            out.tab {
                out.appendLine("${simpleName}(")
                out.tab {
                    for (field in resolvedFields) {
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
                for (f in resolvedFields) {
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
            if (hasSingleKey) {
                val p = resolvedPrimaryKeys.first()
                val k = p.columns.first()
                out.append("override fun keyFromColumnValue(value: ")
                k.writeValueType(out)
                out.appendLine("): ${simpleName}Key = ${simpleName}Key(value)")

                out.append("override val primaryKeyColumn: ")
                k.writeColumnType(out)
                out.append(" = ")
                p.writeColumnAccess(out, k)
                out.appendLine()

                out.appendLine()
            }
            out.appendLine("@Suppress(\"UNCHECKED_CAST\")")
            out.appendLine("override fun alias(name: String) = MyAlias((this as Table).alias(name) as Alias<${simpleName}Table>)")
            out.appendLine("class MyAlias(val alias: Alias<${simpleName}Table>) : ${simpleName}Columns {")
            out.tab {

                out.appendLine("override val set: ColumnSet get() = alias")
                out.appendLine("override val convert: (row: ResultRow) -> ${simpleName} = { row ->")
                out.tab {

                    out.appendLine("${simpleName}(")
                    out.tab {
                        for (field in resolvedFields) {
                            out.append("")
                            field.writeInstanceConstructionPart(out, listOf())
                            out.appendLine(",")
                        }
                    }
                    out.appendLine(")")
                }
                out.appendLine("}")
                for (field in resolvedFields) {
                    field.writeAliasDeclaration(out, listOf("${simpleName}Table"))
                }
                writeKeyAccess(out, "")
                out.append("override val selections: List<ExpressionWithColumnType<*>> = listOf(")
                first = true
                for (pk in resolvedFields) {
                    for (col in pk.columns) {
                        if (first) first = false else out.append(", ")
                        pk.writeColumnAccess(out, col)
                    }
                }
                out.appendLine(")")
                out.append("override val primaryKeyColumns: List<Column<*>> get() = listOf(")
                first = true
                for (pk in resolvedPrimaryKeys) {
                    for (col in pk.columns) {
                        if (first) first = false else out.append(", ")
                        pk.writeColumnAccess(out, col)
                    }
                }
                out.append(")")
            }
            out.appendLine("}")
            out.appendLine("")
        }
        out.appendLine("}")
        out.appendLine("inline val ${simpleName}.Companion.table: ${simpleName}Table get() = ${simpleName}Table")

        out.appendLine("inline val ${simpleName}.key get() = $keyType(")
        out.tab {
            for (col in resolvedPrimaryKeys) {
                out.append(col.name)
                out.append(" = ")
                out.append(col.name)
                out.appendLine(",")
            }
        }
        out.appendLine(")")
        out.appendLine("")


        // ForeignKeys that are also primary keys.
        val foreignPrimaries =
            resolvedForeignKeys.filter { foreign -> resolvedPrimaryKeys.any { primary -> foreign.name == primary.name } }
        if (foreignPrimaries.size > 1) {
            // This signifies it is a many to many, and the reverse access
            // needs to be different, We need to connect two tables that aren't this one.
            for (firstKey in foreignPrimaries) {
                for (secondKey in foreignPrimaries) {
                    if (firstKey.name != secondKey.name) {
                        firstKey.writeReverseManyAccess(out, secondKey, this)
                    }
                }
            }
        } else {
            // Else this is a regular table, but just uses a foreign key as a primaryKey. It's a thing
            for (col in foreignPrimaries) {
                col.writeReverseAccess(out, this)
            }
        }

        // Regular foreign keys
        val nonPrimaryForeign =
            resolvedForeignKeys.filter { current -> !foreignPrimaries.any { other -> current.name == other.name } }
        for (col in nonPrimaryForeign) {
            col.writeReverseAccess(out, this)
        }
    }
}