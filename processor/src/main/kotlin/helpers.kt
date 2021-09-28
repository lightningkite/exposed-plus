package com.lightningkite.exposedplus

import com.google.devtools.ksp.innerArguments
import com.google.devtools.ksp.symbol.*
import kotlin.math.min


data class ResolvedAnnotation(
    val type: KSClassDeclaration,
    val arguments: Map<String, Any?>
)

fun KSTypeArgument.toKotlin(): String {
    return when(this.variance) {
        Variance.STAR -> "*"
        Variance.INVARIANT -> type!!.resolve().toKotlin()
        Variance.COVARIANT -> "out " + type!!.resolve().toKotlin()
        Variance.CONTRAVARIANT -> "in " + type!!.resolve().toKotlin()
    }
}

val KSDeclaration.importSafeName: String get() = when(packageName.asString()) {
    "kotlin", "kotlin.collection", "com.lightningkite.exposedplus", "org.jetbrains.exposed.sql" -> this.simpleName.asString()
    else -> this.qualifiedName!!.asString()
}

fun KSType.toKotlin(): String {
    return when(val base = this.declaration) {
        is KSTypeAlias -> {
            base.type.resolve().declaration.importSafeName + if(this.arguments.isNotEmpty()) this.arguments.joinToString(", ", "<", ">") { it.toKotlin() } else "" + (if(this.isMarkedNullable) "?" else "")
        }
        is KSClassDeclaration -> {
            base.importSafeName + if(this.arguments.isNotEmpty()) this.arguments.joinToString(", ", "<", ">") { it.toKotlin() } else "" + (if(this.isMarkedNullable) "?" else "")
        }
        else -> throw IllegalArgumentException()
    }
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

fun KSAnnotated.annotation(name: String, packageName: String = "com.lightningkite.exposedplus"): KSAnnotation? {
    return this.annotations.find {
        it.shortName.getShortName() == name &&
                it.annotationType.resolve().declaration.qualifiedName?.asString() == "$packageName.$name"
    }
}