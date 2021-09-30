package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*

@TableName
data class Employee(
    @PrimaryKey @AutoIncrement val id: Long,
    val name: String,
    val company: FK<Company>,
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
    @PrimaryKey val employee: FK<Employee>,
    @PrimaryKey val company: FK<Company>
) { companion object }

data class LatLong(
    val latitude: Double,
    val longitude: Double
)