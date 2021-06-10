package io.beatmaps.common.dbo

import io.beatmaps.common.jackson
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.timestamp
import org.jetbrains.exposed.sql.insert
import java.lang.RuntimeException

object ModLog: IntIdTable("modlog", "logId") {
    val opBy = reference("userId", User)
    val opOn = reference("mapId", Beatmap)
    val opAt = timestamp("when")
    val type = integer("type")
    val action = text("action")

    fun insert(userId: Int, mapId: Int, t: ModLogOpType, a: Any) =
        if (a.javaClass != t.actionClass) {
            throw RuntimeException("Action is invalid for log type")
        } else {
            insert {
                it[opBy] = userId
                it[opOn] = mapId
                it[type] = t.ordinal
                it[action] = jackson.writeValueAsString(a)
            }
        }
}

data class ModLogDao(val key: EntityID<Int>): IntEntity(key) {
    companion object : IntEntityClass<ModLogDao>(ModLog)
    val opBy by ModLog.opBy
    val opOn by ModLog.opOn
    val opAt by ModLog.opAt
    val type by ModLog.type
    private val action by ModLog.action

    val realAction = jackson.readValue(action, ModLogOpType.values()[type].actionClass)
}

enum class ModLogOpType(val actionClass: Class<*>) {
    InfoEdit(InfoEditData::class.java), Delete(EmptyData::class.java)
}

data class InfoEditData(val oldTitle: String, val oldDescription: String, val newTitle: String, val newDescription: String)
class EmptyData