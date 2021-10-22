package com.lightningkite.exposedplus

import org.jetbrains.exposed.sql.Table

fun Table.copy(): Table {
    return object: Table(this.tableName) {
        init {

        }
    }
}
