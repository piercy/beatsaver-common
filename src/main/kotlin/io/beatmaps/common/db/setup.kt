package io.beatmaps.common.db

import io.beatmaps.common.api.searchEnum
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.postgresql.util.PGobject

fun setupDB() {
    val dbHost = System.getenv("DB_HOST") ?: ""
    val dbPort = System.getenv("DB_PORT") ?: "5432"
    val dbUser = System.getenv("DB_USER") ?: "beatmaps"
    val dbName = System.getenv("DB_NAME") ?: "beatmaps"
    val dbPass = System.getenv("DB_PASSWORD") ?: ""

    Database.connect("jdbc:postgresql://$dbHost:$dbPort/$dbName?reWriteBatchedInserts=true", user = dbUser, password = dbPass, driver = "org.postgresql.Driver")
}

inline fun <reified T : Enum<T>> Table.postgresEnumeration(
    columnName: String,
    postgresEnumName: String
) = customEnumeration(
    columnName, postgresEnumName,
    { value -> searchEnum<T>(value as String) }, { PGEnum(postgresEnumName, it) }
)

class PGEnum<T : Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
    init {
        value = enumValue?.name?.removePrefix("_")
        type = enumTypeName
    }
}
