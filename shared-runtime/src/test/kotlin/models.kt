package com.lightningkite.exposedplus

import java.math.BigDecimal
import java.util.*

@TableName
data class PrimitiveTestModel(
    @PrimaryKey @AutoIncrement val id: Long,
    val byte: Byte = 0,
    val uByte: UByte = 0.toUByte(),
    val short: Short = 0,
    val uShort: UShort = 0.toUShort(),
    val int: Int = 0,
    val uInt: UInt = 0.toUInt(),
    val long: Long = 0,
    val uLong: ULong = 0.toULong(),
    val float: Float = 0f,
    val double: Double = 0.0,
    val bigDecimal: BigDecimal = BigDecimal(0),
    val char: Char = 'a',
    val string: String = "test",
    val uUID: UUID = UUID.fromString("adcc64e4-f564-4114-b778-952951b2c51c"),
    val boolean: Boolean = false,
    val byteNullable: Byte? = null,
    val uByteNullable: UByte? = null,
    val shortNullable: Short? = null,
    val uShortNullable: UShort? = null,
    val intNullable: Int? = null,
    val uIntNullable: UInt? = null,
    val longNullable: Long? = null,
    val uLongNullable: ULong? = null,
    val floatNullable: Float? = null,
    val doubleNullable: Double? = null,
    val bigDecimalNullable: BigDecimal? = null,
    val charNullable: Char? = null,
    val stringNullable: String? = null,
    val uUIDNullable: UUID? = null,
    val booleanNullable: Boolean? = null,
) {
    companion object
}


@TableName
data class Employee(
    @PrimaryKey @AutoIncrement val id: Long,
    val name: String,
    @ReverseName("directEmployees") val company: CompanyKey,
    @ReverseName("manages") val manager: EmployeeKey?,
    val location: LatLong
) {
    companion object
}

@TableName
data class Company(
    @PrimaryKey @AutoIncrement val id: Long,
    val name: String
) {
    companion object
}

@TableName
data class ContractsFor(
    @PrimaryKey val employee: EmployeeKey,
    @PrimaryKey @ReverseName("contracts") val company: CompanyKey
) { companion object }

data class LatLong(
    val latitude: Double,
    var longitude: Double
) {
    companion object
}

