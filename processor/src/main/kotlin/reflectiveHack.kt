package com.lightningkite.exposedplus

import com.google.devtools.ksp.symbol.KSTypeReference

val KSTypeReference.isMarkedNullable: Boolean get() {
    return hack(this)
}

private val hack: (KSTypeReference)->Boolean by lazy {
    val kClass = Class.forName("com.google.devtools.ksp.symbol.impl.kotlin.KSTypeReferenceImpl")
    val getTypeReference = kClass.getMethod("getKtTypeReference")
    val kClass2 = Class.forName("org.jetbrains.kotlin.psi.KtTypeReference")
    val getTypeElement = kClass2.getMethod("getTypeElement")
    val correctType = Class.forName("org.jetbrains.kotlin.psi.KtNullableType")
    return@lazy {
        kClass.isInstance(it) && correctType.isInstance(getTypeElement(getTypeReference(it)))
    }
}