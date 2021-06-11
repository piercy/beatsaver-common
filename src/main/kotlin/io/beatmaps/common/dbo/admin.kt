package io.beatmaps.common.dbo

import io.beatmaps.common.jackson
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.timestamp
import org.jetbrains.exposed.sql.insert
import java.lang.RuntimeException
import kotlin.reflect.KClass

object ModLog: IntIdTable("modlog", "logId") {
    val opBy = reference("userId", User)
    val opOn = reference("mapId", Beatmap)
    val opAt = timestamp("when")
    val type = integer("type")
    val action = text("action")

    fun insert(userId: Int, mapId: Int, a: IModLogOpAction) =
        (ModLogOpType.fromAction(a) ?: throw RuntimeException("Action type not valid")).let { t ->
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

    val realAction: IModLogOpAction = jackson.readValue(action, ModLogOpType.values()[type].actionClass.java) as IModLogOpAction
}

enum class ModLogOpType(val actionClass: KClass<*>) {
    InfoEdit(InfoEditData::class), Delete(DeletedData::class);

    companion object {
        private val map = values().associateBy(ModLogOpType::actionClass)
        fun fromAction(action: IModLogOpAction) = map[action::class]
    }
}

interface IModLogOpAction
data class InfoEditData(val oldTitle: String, val oldDescription: String, val newTitle: String, val newDescription: String) : IModLogOpAction
class DeletedData : IModLogOpAction