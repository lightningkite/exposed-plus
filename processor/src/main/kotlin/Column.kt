package com.lightningkite.exposedplus

import com.google.devtools.ksp.symbol.KSType
import java.io.Writer

data class Column(
    val name: String,
    val type: KSType,
    val annotations: List<ResolvedAnnotation>
) {
    fun writeColumnType(out: TabAppendable) {
        out.append("Column<")
        out.append(type.toKotlin())
        out.append(">")
    }
    fun writeBaseColumnDeclaration(out: TabAppendable, name: String = this.name) {
        when(type.declaration.qualifiedName!!.asString()) {
            "kotlin.Byte" -> out.append("byte(\"$name\")")
            "kotlin.UByte" -> out.append("ubyte(\"$name\")")
            "kotlin.Short" -> out.append("short(\"$name\")")
            "kotlin.UShort" -> out.append("ushort(\"$name\")")
            "kotlin.Int" -> out.append("integer(\"$name\")")
            "kotlin.UInt" -> out.append("uinteger(\"$name\")")
            "kotlin.Long" -> out.append("long(\"$name\")")
            "kotlin.ULong" -> out.append("ulong(\"$name\")")
            "kotlin.Float" -> out.append("float(\"$name\")")
            "kotlin.Double" -> out.append("double(\"$name\")")
            "java.math.BigDecimal" -> out.append("decimal(\"$name\", ${annotations.byNameNumber("Precision") ?: 10}, ${annotations.byNameNumber("Scale") ?: 2})")
            "kotlin.Char" -> out.append("char(\"$name\")")
            "kotlin.String" -> out.append("varchar(\"$name\", ${annotations.byNameNumber("Size") ?: 255})")
            "kotlin.ByteArray" -> out.append("binary(\"$name\", ${annotations.byNameNumber("Size") ?: 255})")
            "org.jetbrains.exposed.sql.statements.api.ExposedBlob" -> out.append("blob(\"$name\")")
            "java.util.UUID" -> out.append("uuid(\"$name\")")
            "kotlin.Boolean" -> out.append("bool(\"$name\")")
        }
        if(type.isMarkedNullable) out.append(".nullable()")
    }
    fun writeValueType(out: TabAppendable) {
        out.append(type.toKotlin())
    }
    fun writeColumnDeclaration(out: TabAppendable) {
        writeBaseColumnDeclaration(out)
        if(annotations.byName("AutoIncrement") != null) out.append(".autoIncrement()")
        annotations.byName("Index")?.let {
            val name = it.arguments.values.firstOrNull()?.toString()
            if(name != null) {
                out.append(".index(\"$name\")")
            } else {
                out.append(".index()")
            }
        }
    }
}