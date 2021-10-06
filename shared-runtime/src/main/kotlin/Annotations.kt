package com.lightningkite.exposedplus


enum class ReferenceOption {
    CASCADE,
    SET_NULL,
    RESTRICT,
    NO_ACTION;
    override fun toString(): String = name.replace("_", " ")
}

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class TableName(
    val tableName: String = "",
    val schemaName: String = ""
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class Size(val count: Int)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class Scale(val digits: Int)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class Precision(val digits: Int)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class PrimaryKey()

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class Index()

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class OnDelete(val behavior: ReferenceOption)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class OnUpdate(val behavior: ReferenceOption)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class AutoIncrement()

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class ReverseName(val name:String)

interface KeyHandle<T>
abstract class ForeignKey<T> {
    abstract val handle: KeyHandle<T>
    var filled: Boolean = false
        private set
    var cachedValue: T? = null
        private set
    fun populate(with: T) {
        filled = true
        cachedValue = with
    }
}
