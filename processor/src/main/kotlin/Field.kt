package com.lightningkite.exposedplus

import com.google.devtools.ksp.symbol.*

data class Field(
    val name: String,
    val kotlinType: KSTypeReference,
    val annotations: List<ResolvedAnnotation>
) {
    fun resolve(qn: String = kotlinType.toString().removeSuffix("?"), nullable: Boolean = kotlinType.toString().endsWith("?")): ResolvedField {
        return Column.ColumnType.fromQn(qn)?.let {
            ResolvedField.Single(
                name,
                Column(
                    name = name,
                    type = it,
                    nullable = nullable,
                    annotations = annotations
                )
            )
        } ?: when(qn) {
            "com.lightningkite.exposedplus.ForeignKey", "ForeignKey" -> ResolvedField.ForeignKey(
                name = name,
                otherTable = tables[kotlinType.element!!.typeArguments[0].type!!.resolve().declaration.qualifiedName!!.asString()]!!,
                annotations = annotations,
            )
            else -> {
                if(qn.endsWith("Key")) {
                    ResolvedField.ForeignKey(
                        name = name,
                        otherTable = tables[qn.substringBefore("Key")]!!,
                        annotations = annotations,
                    )
                } else {
                    //I give up, we need to resolve
                    val baseType = kotlinType.tryResolve()!!.declaration as KSClassDeclaration
                    if (baseType.classKind == ClassKind.ENUM_CLASS)
                        ResolvedField.Single(
                            name,
                            Column(
                                name = name,
                                type = Column.ColumnType.TypeEnum(baseType),
                                nullable = nullable,
                                annotations = annotations
                            )
                        )
                    else if (Modifier.DATA in baseType.modifiers) ResolvedField.Compound(name, baseType.subTable)
                    else throw IllegalArgumentException("Cannot convert ${baseType.qualifiedName?.asString()} to column")
                }
            }
        }
    }
}

fun KSPropertyDeclaration.toField(): Field {
    return Field(
        name = this.simpleName.getShortName(),
        kotlinType = this.type,
        annotations = this.annotations.map { it.resolve() }.toList()
    )
}

fun KSClassDeclaration.fields(): List<Field> {
    val allProps = getAllProperties().associateBy { it.simpleName.getShortName() }
    return primaryConstructor!!
        .parameters
        .filter { it.isVal || it.isVar }
        .mapNotNull { allProps[it.name?.getShortName()] }
        .map { it.toField() }
}