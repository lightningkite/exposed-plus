package com.lightningkite.exposedplus

import com.google.devtools.ksp.symbol.KSClassDeclaration

val subTableCache = HashMap<KSClassDeclaration, CompoundSubTable>()
val KSClassDeclaration.subTable: CompoundSubTable get() = subTableCache.getOrPut(this) { CompoundSubTable(this) }

data class CompoundSubTable(
    val raw: KSClassDeclaration,
    val fields: List<Field>,
) {
    var handled = false
    constructor(raw: KSClassDeclaration):this(
        raw = raw,
        fields = raw.fields()
    )

    val packageName: String get() = raw.packageName.asString()
    val simpleName: String get() = raw.simpleName.getShortName()

    val resolved by lazy { fields.map { it.resolve() } }

    fun writeFile(out: TabAppendable) {
        out.appendLine("package ${packageName}")
        out.appendLine("")
        out.appendLine("import com.lightningkite.exposedplus.*")
        out.appendLine("import org.jetbrains.exposed.sql.*")
        out.appendLine("")
        out.appendLine("data class ${simpleName}SubTable(")
        out.tab {
            for (col in resolved) {
                col.writePropertyDeclaration(out)
                out.appendLine(",")
            }
        }
        out.appendLine(") : ResultMapper<${simpleName}> {")
        out.tab {
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

            out.append("override val selections: List<ExpressionWithColumnType<*>> = listOf(")
            var first = true
            for (pk in resolved) {
                for (col in pk.columns) {
                    if (first) first = false else out.append(", ")
                    pk.writeColumnAccess(out, col)
                }
            }
            out.appendLine(")")
        }
        out.appendLine("}")
    }
}