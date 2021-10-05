package com.lightningkite.exposedplus

private fun TabAppendable.access(parts: List<String>) = parts.forEach { append(it); append('.') }

sealed interface ResolvedField {
    val name: String
    val columns: List<Column>
    fun forceNullable(): ResolvedField
    fun replaceAnnotations(newAnnotations: List<ResolvedAnnotation>): ResolvedField
    fun prependColumnName(name: String): ResolvedField
    fun writePropertyDeclaration(out: TabAppendable)
    fun writeMainDeclaration(out: TabAppendable, sourceNames: List<String>) {
        out.append("override ")
        writePropertyDeclaration(out)
        out.append(" = ")
        writeMainValue(out, sourceNames)
        out.appendLine()
    }

    fun writeAliasDeclaration(out: TabAppendable, sourceNames: List<String>) {
        out.append("override ")
        writePropertyDeclaration(out)
        out.append(" = ")
        writeAliasValue(out, sourceNames)
        out.appendLine()
    }

    fun writeMainValue(out: TabAppendable, sourceNames: List<String>)
    fun writeAliasValue(out: TabAppendable, sourceNames: List<String>)
    fun writeInstanceConstructionPart(out: TabAppendable, sourceNames: List<String>) {
        out.append(name)
        out.append(" = ")
        writeInstanceConstructionValue(out, sourceNames)
    }

    fun writeInstanceConstructionValue(out: TabAppendable, sourceNames: List<String>)
    fun writeColumnAccess(out: TabAppendable, column: Column)
    fun writeValueAccess(out: TabAppendable, column: Column) = writeColumnAccess(out, column)

    data class Single(
        override val name: String,
        val column: Column
    ) : ResolvedField {
        override val columns: List<Column>
            get() = listOf(column)

        override fun replaceAnnotations(newAnnotations: List<ResolvedAnnotation>): ResolvedField = copy(column = column.copy(annotations = newAnnotations))

        override fun forceNullable(): ResolvedField = copy(column = column.copy(nullable = true))

        override fun prependColumnName(name: String): ResolvedField =
            copy(column = column.copy(name = name + "_" + column.name))

        override fun writePropertyDeclaration(out: TabAppendable) {
            out.append("val ")
            out.append(name)
            out.append(": ")
            column.writeColumnType(out)
        }

        override fun writeMainValue(out: TabAppendable, sourceNames: List<String>) {
            column.writeColumnDeclaration(out)
        }

        override fun writeAliasValue(out: TabAppendable, sourceNames: List<String>) {
            out.append("alias[")
            out.access(sourceNames)
            writeColumnAccess(out, column)
            out.append("]")
        }

        override fun writeInstanceConstructionValue(out: TabAppendable, sourceNames: List<String>) {
            if(column.nullable) {
                out.append("row.getOrNull(")
                out.access(sourceNames)
                writeColumnAccess(out, column)
                out.append(")")
            } else {
                out.append("row[")
                out.access(sourceNames)
                writeColumnAccess(out, column)
                out.append("]")
            }
        }

        override fun writeColumnAccess(out: TabAppendable, column: Column) {
            out.append(name)
        }
    }

    data class ForeignKey(
        override val name: String,
        val otherTable: Table,
        val nullable: Boolean,
        val annotations: List<ResolvedAnnotation>,
        val childFields: List<ResolvedField> = otherTable.resolvedPrimaryKeys.map {
            if(nullable) it.prependColumnName(name).forceNullable().replaceAnnotations(annotations) else it.prependColumnName(name).replaceAnnotations(annotations)
        },
    ) : ResolvedField {
        override val columns = childFields.flatMap { it.columns }

        override fun replaceAnnotations(newAnnotations: List<ResolvedAnnotation>): ResolvedField = ForeignKey(
            name,
            otherTable,
            nullable = true,
            childFields = childFields.map { it.replaceAnnotations(newAnnotations) },
            annotations = listOf()
        )

        override fun forceNullable(): ResolvedField = ForeignKey(
            name,
            otherTable,
            nullable = true,
            childFields = childFields.map { it.forceNullable() },
            annotations = listOf()
        )

        override fun prependColumnName(name: String): ResolvedField = ForeignKey(
            name,
            otherTable,
            nullable = nullable,
            childFields = childFields.map { it.prependColumnName(name) },
            annotations = listOf()
        )

        override fun writePropertyDeclaration(out: TabAppendable) {
            out.append("val ")
            out.append(name)
            out.append(": ")
            out.append(otherTable.simpleName)
            out.append("FKField")
            if(nullable) out.append("Nullable")
        }

        override fun writeMainValue(out: TabAppendable, sourceNames: List<String>) {
            if(nullable) out.append("/*nullable*/ ")
            out.append(otherTable.simpleName)
            out.append("FKField")
            if(nullable) out.append("Nullable")
            out.appendLine("(")
            out.tab {
                val plusMe = sourceNames + name
                for (sub in childFields) {
                    sub.writeMainValue(out, plusMe)
                    out.appendLine(",")
                }
            }
            out.appendLine(")")
        }

        override fun writeAliasValue(out: TabAppendable, sourceNames: List<String>) {
            out.append(otherTable.simpleName)
            out.append("FKField")
            if(nullable) out.append("Nullable")
            out.appendLine("(")
            out.tab {
                val plusMe = sourceNames + name
                for (sub in childFields) {
                    sub.writeAliasValue(out, plusMe)
                    out.appendLine(",")
                }
            }
            out.appendLine(")")
        }

        override fun writeInstanceConstructionValue(out: TabAppendable, sourceNames: List<String>) {
            if (nullable) {
                val plusMe = sourceNames + name
                childFields.first().writeInstanceConstructionValue(out, plusMe)
                out.append("?.let {")
                out.append(otherTable.keyType)
                out.append("(")
                var first = true
                for (f in childFields) {
                    if (first) {
                        first = false
                        out.append("it")
                    } else {
                        out.append(", ")
                        f.writeInstanceConstructionValue(out, plusMe)
                        out.append("!!")
                    }
                }
                out.append(")")
                out.append("}")
            } else {
                val plusMe = sourceNames + name
                out.append(otherTable.keyType)
                out.append("(")
                var first = true
                for (f in childFields) {
                    if (first) first = false else out.append(", ")
                    f.writeInstanceConstructionValue(out, plusMe)
                }
                out.append(")")
            }
        }

        override fun writeColumnAccess(out: TabAppendable, column: Column) {
            out.append(name)
            out.append(".")
            for (child in childFields) {
                if (child.columns.contains(column)) {
                    child.writeColumnAccess(out, column)
                    return
                }
            }
        }

        override fun writeValueAccess(out: TabAppendable, column: Column) {
            out.append(name)
            if (nullable)
                out.append("?.")
            else
                out.append(".")
            for (child in childFields) {
                if (child.columns.contains(column)) {
                    child.writeColumnAccess(out, column)
                    return
                }
            }
        }

        fun writeReverseAccess(out: TabAppendable, table: Table) {
            val fieldName =
                annotations.find { it.type.simpleName.asString() == "ReverseName" }?.arguments?.get("name") as? String
                    ?: table.simpleName.lowerCaseFirst().makePlural()
            out.appendLine("val ${otherTable.simpleName}.$fieldName: TypedQuery<${table.simpleName}Table, ${table.simpleName}> ")
            out.tab {
                out.appendLine("get() {")
                out.tab {

                    out.appendLine("return transaction {")
                    out.tab {
                        out.appendLine("${table.simpleName}.table.all()")
                        out.tab {
                            for (pk in childFields) {
                                out.appendLine(".filter { it.${name}.${pk.name} eq this@$fieldName.${pk.name} }")
                            }
                        }
                    }
                    out.appendLine("}")
                }
                out.appendLine("}")
            }
            out.appendLine()
        }

        fun writeReverseManyAccess(out: TabAppendable, otherKey: ForeignKey, table: Table) {
            val fieldName =
                otherKey.annotations.find { it.type.simpleName.asString() == "ReverseName" }?.arguments?.get("name") as? String
                    ?: otherKey.otherTable.simpleName.lowerCaseFirst().makePlural()
            out.appendLine("val ${otherTable.simpleName}.$fieldName: TypedQuery<${otherKey.otherTable.simpleName}Columns, ${otherKey.otherTable.simpleName}> ")
            out.tab {
                out.appendLine("get() {")
                out.tab {
                    out.appendLine("return transaction {")
                    out.tab {
                        out.appendLine("${table.simpleName}.table.all()")
                        out.tab {
                            for (pk in childFields) {
                                out.appendLine(".filter { it.${name}.${pk.name} eq this@$fieldName.${pk.name} }")
                            }
                            out.appendLine(".mapFk { it.${otherKey.name} }")
                        }
                    }
                    out.appendLine("}")
                }
                out.appendLine("}")
            }
            out.appendLine()
        }

    }

    data class Compound(
        override val name: String,
        val subTable: CompoundSubTable,
        val childFields: List<ResolvedField> = subTable.resolved.map { it.prependColumnName(name) }
    ) : ResolvedField {
        override val columns = childFields.flatMap { it.columns }

        override fun replaceAnnotations(newAnnotations: List<ResolvedAnnotation>): ResolvedField
            = Compound(name, subTable, childFields = childFields.map { it.replaceAnnotations(newAnnotations) })

        override fun forceNullable(): ResolvedField = Compound(name, subTable, childFields = childFields.map { it.forceNullable() })

        override fun prependColumnName(name: String): ResolvedField =
            Compound(name, subTable, childFields = childFields.map { it.prependColumnName(name) })

        override fun writePropertyDeclaration(out: TabAppendable) {
            out.append("val ")
            out.append(name)
            out.append(": ")
            out.append(subTable.simpleName)
            out.append("SubTable")
        }

        override fun writeMainValue(out: TabAppendable, sourceNames: List<String>) {
            out.append(subTable.simpleName)
            out.appendLine("SubTable(")
            out.tab {
                val plusMe = sourceNames + name
                for (sub in childFields) {
                    sub.writeMainValue(out, plusMe)
                    out.appendLine(",")
                }
            }
            out.appendLine(")")
        }

        override fun writeAliasValue(out: TabAppendable, sourceNames: List<String>) {
            out.append(subTable.simpleName)
            out.appendLine("SubTable(")
            out.tab {
                val plusMe = sourceNames + name
                for (sub in childFields) {
                    sub.writeAliasValue(out, plusMe)
                    out.appendLine(",")
                }
            }
            out.appendLine(")")
        }

        override fun writeInstanceConstructionValue(out: TabAppendable, sourceNames: List<String>) {
            val plusMe = sourceNames + name
            out.append(subTable.simpleName)
            out.append("(")
            var first = true
            for (f in childFields) {
                if (first) first = false else out.append(", ")
                f.writeInstanceConstructionValue(out, plusMe)
            }
            out.append(")")
        }

        override fun writeColumnAccess(out: TabAppendable, column: Column) {
            out.append(name)
            out.append(".")
            for (child in childFields) {
                if (child.columns.contains(column)) {
                    child.writeColumnAccess(out, column)
                    return
                }
            }
        }

        override fun writeValueAccess(out: TabAppendable, column: Column) {
            out.append(name)
            out.append(".")
            for (child in childFields) {
                if (child.columns.contains(column)) {
                    child.writeColumnAccess(out, column)
                    return
                }
            }
        }
    }
}