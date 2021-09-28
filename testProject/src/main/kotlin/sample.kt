package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.*

@TableName("Employee")
data class Employee(
    @PrimaryKey @AutoIncrement val id: Long,
    val name: String,
    val company: FK<Company>
) {
    companion object
}

@TableName("Company")
data class Company(
    @PrimaryKey @AutoIncrement val id: Long,
    val name: String
) {
    companion object
}
