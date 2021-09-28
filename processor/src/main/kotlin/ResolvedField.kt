package com.lightningkite.exposedplus

import java.io.Writer

private fun TabAppendable.access(parts: List<String>) = parts.forEach { append(it); append('.') }

sealed interface ResolvedField {
    val name: String
    val columns: List<Column>
    fun prependColumnName(name: String): ResolvedField
    fun writeInterfaceDeclaration(out: TabAppendable)
    fun writeMainDeclaration(out: TabAppendable, sourceNames: List<String>)
    fun writeAliasDeclaration(out: TabAppendable, sourceNames: List<String>)
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

        override fun prependColumnName(name: String): ResolvedField = copy(column = column.copy(name = name + "_" + column.name))

        override fun writeInterfaceDeclaration(out: TabAppendable) {
            out.append("val ")
            out.append(name)
            out.append(": ")
            column.writeColumnType(out)
            out.appendLine()
        }

        override fun writeMainDeclaration(out: TabAppendable, sourceNames: List<String>) {
            out.append("override val ")
            out.append(name)
            out.append(": ")
            column.writeColumnType(out)
            out.append(" = ")
            column.writeColumnDeclaration(out)
            out.appendLine()
        }

        override fun writeAliasDeclaration(out: TabAppendable, sourceNames: List<String>) {
            out.append("override val ")
            out.append(name)
            out.append(": ")
            column.writeColumnType(out)
            out.append(" = alias[")
            out.access(sourceNames)
            writeColumnAccess(out, column)
            out.append("]")
            out.appendLine()
        }

        override fun writeInstanceConstructionValue(out: TabAppendable, sourceNames: List<String>) {
            out.append("row[")
            out.access(sourceNames)
            writeColumnAccess(out, column)
            out.append("]")
        }

        override fun writeColumnAccess(out: TabAppendable, column: Column) {
            out.append(name)
        }
    }

    data class ForeignKey(
        override val name: String,
        val otherTable: Table,
        val childFields: List<ResolvedField> = otherTable.primaryKeys.map { it.prependColumnName(name) }
    ) : ResolvedField {
        override val columns = childFields.flatMap { it.columns }

        override fun prependColumnName(name: String): ResolvedField = ForeignKey(name, otherTable, childFields = childFields.map { it.prependColumnName(name) })

        override fun writeInterfaceDeclaration(out: TabAppendable) {
            out.append("val ")
            out.append(name)
            out.append(": ")
            out.append(otherTable.simpleName)
            out.append("FKField")
            out.appendLine()
        }

        override fun writeMainDeclaration(out: TabAppendable, sourceNames: List<String>) {
//            override val company: CompanyFKField = object : CompanyFKField {
//                override val id: Column<Long> = long("company_id").references(CompanyTable.id)
//            }
            out.append("override val ")
            out.append(name)
            out.append(": ")
            out.append(otherTable.simpleName)
            out.append("FKField")
            out.append(" = object : ")
            out.append(otherTable.simpleName)
            out.appendLine("FKField { ")
            out.tab {
                val plusMe = sourceNames + name
                for (sub in childFields) {
                    sub.writeMainDeclaration(out, plusMe)
                }
            }
            out.appendLine("}")
        }

        override fun writeAliasDeclaration(out: TabAppendable, sourceNames: List<String>) {
            out.append("override val ")
            out.append(name)
            out.append(": ")
            out.append(otherTable.simpleName)
            out.append("FKField")
            out.append(" = object : ")
            out.append(otherTable.simpleName)
            out.appendLine("FKField { ")
            out.tab {
                val plusMe = sourceNames + name
                for (sub in childFields) {
                    sub.writeAliasDeclaration(out, plusMe)
                }
            }
            out.appendLine("}")
        }

        override fun writeInstanceConstructionValue(out: TabAppendable, sourceNames: List<String>) {
            val plusMe = sourceNames + name
            out.append("ForeignKey(")
            if (otherTable.hasCompoundKey) {
                out.append(otherTable.keyType)
                out.append("(")
                var first = true
                for(f in childFields) {
                    if(first) first = false else out.append(", ")
                    f.writeInstanceConstructionValue(out, plusMe)
                }
                out.append(")")
            } else {
                childFields.single().writeInstanceConstructionValue(out, plusMe)
            }
            out.append(", ")
            out.access(sourceNames)
            out.append(name)
            out.append(")")
        }

        override fun writeColumnAccess(out: TabAppendable, column: Column) {
            out.append(name)
            out.append(".")
            for(child in childFields) {
                if(child.columns.contains(column)) {
                    child.writeColumnAccess(out, column)
                    return
                }
            }
        }

        override fun writeValueAccess(out: TabAppendable, column: Column) {
            out.append(name)
            out.append(".")
            if(otherTable.hasCompoundKey) {
                for(child in childFields) {
                    if(child.columns.contains(column)) {
                        child.writeColumnAccess(out, column)
                        return
                    }
                }
            } else {
                out.append("key")
            }
        }

    }

    data class Compound(
        override val name: String,
        override val columns: List<Column>
    ) : ResolvedField {
        override fun prependColumnName(name: String): ResolvedField {
            TODO("Not yet implemented")
        }

        override fun writeInterfaceDeclaration(out: TabAppendable) {
            TODO("Not yet implemented")
        }

        override fun writeMainDeclaration(out: TabAppendable, sourceNames: List<String>) {
            TODO("Not yet implemented")
        }

        override fun writeAliasDeclaration(out: TabAppendable, sourceNames: List<String>) {
            TODO("Not yet implemented")
        }

        override fun writeInstanceConstructionValue(out: TabAppendable, sourceNames: List<String>) {
            TODO("Not yet implemented")
        }

        override fun writeColumnAccess(out: TabAppendable, column: Column) {
            TODO("Not yet implemented")
        }
    }
}