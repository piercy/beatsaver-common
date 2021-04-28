package io.beatmaps.common.dbo

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.alias

val curatorAlias = User.alias("curator")
object User: IntIdTable("uploader", "id") {
    val hash = char("hash", 24).uniqueIndex("hash").nullable()
    val name = text("name")
    val avatar = text("avatar").nullable()
    val email = text("email").uniqueIndex("email_idx").nullable()
    val steamId = long("steamId").nullable()
    val oculusId = long("oculusId").nullable()
    val discordId = long("discordId").nullable()
    val testplay = bool("testplay")
    val admin = bool("admin")
}

data class UserDao(val key: EntityID<Int>): IntEntity(key) {
    companion object : IntEntityClass<UserDao>(User)
    val name: String by User.name
    val hash: String? by User.hash
    val avatar: String? by User.avatar
    val email: String? by User.email
    val steamId: Long? by User.steamId
    val oculusId: Long? by User.oculusId
    val discordId: Long? by User.discordId
    val testplay: Boolean by User.testplay
    val admin: Boolean by User.admin
}