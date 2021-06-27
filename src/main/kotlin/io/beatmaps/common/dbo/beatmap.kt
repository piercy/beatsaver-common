package io.beatmaps.common.dbo

import io.beatmaps.common.api.ECharacteristic
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.db.array
import io.beatmaps.common.db.postgresEnumeration
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.timestamp
import java.math.BigDecimal
import java.time.Instant

object Beatmap: IntIdTable("beatmap", "mapId") {
    fun joinVersions(stats: Boolean = false, state: (SqlExpressionBuilder.() -> Op<Boolean>)? = { Versions.state eq EMapState.Published }) =
        join(Versions, JoinType.INNER, onColumn = Beatmap.id, otherColumn = Versions.mapId, additionalConstraint = state).run {
            if (stats) {
                join(Difficulty, JoinType.INNER, onColumn = Versions.id, otherColumn = Difficulty.versionId)
            } else {
                this
            }
        }
    fun ColumnSet.joinUploader() = join(User, JoinType.INNER, onColumn = uploader, otherColumn = User.id)
    fun ColumnSet.joinCurator() = join(curatorAlias, JoinType.LEFT, onColumn = curator, otherColumn = curatorAlias[User.id])

    val name = text("name")
    val description = text("description")
    val uploader = reference("uploader", User)
    val bpm = float("bpm")
    val duration = integer("duration")
    val songName = text("songName")
    val songSubName = text("songSubName")
    val songAuthorName = text("songAuthorName")
    val levelAuthorName = text("levelAuthorName")
    val uploaded = timestamp("uploaded").nullable()
    val automapper = bool("automapper")
    val ai = bool("ai")
    val plays = integer("plays")
    val downloads = integer("downloads")
    val deletedAt = timestamp("deletedAt").nullable()

    val curator = optReference("curatedBy", User)
    val curatedAt = timestamp("curatedAt").nullable()

    val beatsaverDownloads = integer("bsdownload")
    val upVotes = integer("bsupvote")
    val downVotes = integer("bsdownvote")
    val score = decimal("score", 4, 4)
    val upVotesInt = integer("upvote")
    val downVotesInt = integer("downvote")
    val lastVoteAt = timestamp("lastVoteAt").nullable()

    val chroma = bool("chroma")
    val noodle = bool("noodle")
    val ranked = bool("ranked")
    val qualified = bool("qualified")
    val rankedAt = timestamp("rankedAt").nullable()
    val qualifiedAt = timestamp("qualifiedAt").nullable()

    val minNps = decimal("minNps", 8, 3)
    val maxNps = decimal("maxNps", 8, 3)
    val fullSpread = bool("fullSpread")
}

data class BeatmapDao(val key: EntityID<Int>): IntEntity(key) {
    companion object : IntEntityClass<BeatmapDao>(Beatmap)
    val name: String by Beatmap.name
    val description: String by Beatmap.description
    val bpm: Float by Beatmap.bpm
    val duration: Int by Beatmap.duration
    val songName: String by Beatmap.songName
    val songSubName: String by Beatmap.songSubName
    val songAuthorName: String by Beatmap.songAuthorName
    val levelAuthorName: String by Beatmap.levelAuthorName
    val uploaded: Instant? by Beatmap.uploaded
    val automapper: Boolean by Beatmap.automapper
    val plays: Int by Beatmap.plays
    val downloads: Int by Beatmap.downloads
    val curatedAt by Beatmap.curatedAt
    val curator by UserDao optionalReferencedOn Beatmap.curator

    val uploaderId by Beatmap.uploader
    val uploader by UserDao referencedOn Beatmap.uploader
    val versions = mutableMapOf<EntityID<Int>, VersionsDao>()

    val upVotes by Beatmap.upVotes
    val downVotes by Beatmap.downVotes
    val score by Beatmap.score
    val upVotesInt by Beatmap.upVotesInt
    val downVotesInt by Beatmap.downVotesInt

    fun enrichTestplays() = this.also {
        val v = versions.filter { it.value.state != EMapState.Published }
        if (v.isNotEmpty()) {
            val feedback = TestplayDao.wrapRows(
                Testplay
                    .joinUploader()
                    .select {
                        Testplay.versionId inList v.map { it.key.value }.toList()
                    }
            ).toList().groupBy { it.versionId }

            v.forEach {
                feedback[it.key]?.associateBy { inner -> inner.id }?.let { m ->
                    it.value.testplays.putAll(m)
                }
            }
        }
    }
}

fun Query.complexToBeatmap(alias: QueryAlias? = null) = this.fold(mutableMapOf<EntityID<Int>, BeatmapDao>()) { map, row ->
    map.also {
        map.getOrPut(row[alias?.get(Beatmap.id) ?: Beatmap.id]) {
            if (row.hasValue(User.id)) {
                UserDao.wrapRow(row)
            }
            if (row.hasValue(curatorAlias[User.id]) && row[Beatmap.curator] != null) {
                UserDao.wrapRow(row, curatorAlias)
            }
            if (alias != null) {
                BeatmapDao.wrapRow(row, alias)
            } else {
                BeatmapDao.wrapRow(row)
            }
        }.run {
            if (row.hasValue(Versions.id)) {
                versions.getOrPut(row[Versions.id]) {
                    VersionsDao.wrapRow(row)
                }.apply {
                    if (row.hasValue(Difficulty.id)) {
                        difficulties.getOrPut(row[Difficulty.id]) {
                            DifficultyDao.wrapRow(row)
                        }
                    }
                }.run {
                    if (row.hasValue(Testplay.id)) {
                        testplays.getOrPut(row[Testplay.id]) {
                            TestplayDao.wrapRow(row)
                        }
                    }
                }
            }
        }
    }
}.values.toList()

object Versions: IntIdTable("versions", "versionId") {
    val mapId = reference("mapId", Beatmap)
    val hash = char("hash", 40)
    val uploaded = timestamp("createdAt")
    val state = postgresEnumeration<EMapState>("state", "mapstate")
    val feedback = text("feedback").nullable()
    val testplayAt = timestamp("testplayAt").nullable()
    val key64 = varchar("key64", 8).nullable()
    val sageScore = short("sageScore").nullable()
}

data class VersionsDao(val key: EntityID<Int>): IntEntity(key) {
    companion object : IntEntityClass<VersionsDao>(Versions)
    val mapId: EntityID<Int> by Versions.mapId
    val map: BeatmapDao by BeatmapDao referencedOn Versions.mapId
    val hash: String by Versions.hash
    val uploaded: Instant by Versions.uploaded
    val state: EMapState by Versions.state
    val feedback: String? by Versions.feedback
    val testplayAt: Instant? by Versions.testplayAt
    val key64: String? by Versions.key64
    val sageScore by Versions.sageScore

    val testplays = mutableMapOf<EntityID<Int>, TestplayDao>()
    val difficulties = mutableMapOf<EntityID<Int>, DifficultyDao>()
}

val maxAllowedNps = BigDecimal.valueOf(99999L)
object Difficulty: IntIdTable("difficulty", "difficultyId") {
    val versionId = reference("versionId", Versions)
    val njs = float("njs")
    val offset = float("offsetTime")
    val notes = integer("notes")
    val bombs = integer("bombs")
    val obstacles = integer("obstacles")
    val nps = decimal("nps", 8, 3)
    val length = decimal("length", 10, 3)
    val seconds = decimal("seconds", 10, 3)
    val mapId = reference("mapId", Beatmap)
    val characteristic = postgresEnumeration<ECharacteristic>("characteristic", "characteristic")
    val difficulty = postgresEnumeration<EDifficulty>("difficulty", "diff")
    val events = integer("events")
    val chroma = bool("chroma")
    val ne = bool("ne")
    val me = bool("me")
    val pReset = integer("pReset")
    val pWarn = integer("pWarn")
    val pError = integer("pError")
    val createdAt = timestamp("createdAt")
    val stars = decimal("stars", 4, 2).nullable()
    val requirements = array<String>("requirements", VarCharColumnType(64)).nullable()
    val suggestions = array<String>("suggestions", VarCharColumnType(64)).nullable()
    val information = array<String>("information", VarCharColumnType(255)).nullable()
    val warnings = array<String>("warnings", VarCharColumnType(255)).nullable()

    val uniqueDiff = Index(listOf(versionId, characteristic, difficulty), true, "diff_unique")
}

data class DifficultyDao(val key: EntityID<Int>): IntEntity(key) {
    companion object : IntEntityClass<DifficultyDao>(Difficulty)
    val version: VersionsDao by VersionsDao referencedOn Difficulty.versionId
    val njs: Float by Difficulty.njs
    val offset: Float by Difficulty.offset
    val notes: Int by Difficulty.notes
    val bombs: Int by Difficulty.bombs
    val obstacles: Int by Difficulty.obstacles
    val nps: BigDecimal by Difficulty.nps
    val length: BigDecimal by Difficulty.length
    val seconds: BigDecimal by Difficulty.seconds
    val map: BeatmapDao by BeatmapDao referencedOn Difficulty.mapId
    val characteristic: ECharacteristic by Difficulty.characteristic
    val difficulty: EDifficulty by Difficulty.difficulty
    val events: Int by Difficulty.events
    val chroma: Boolean by Difficulty.chroma
    val ne: Boolean by Difficulty.ne
    val me: Boolean by Difficulty.me
    val pReset: Int by Difficulty.pReset
    val pWarn: Int by Difficulty.pWarn
    val pError: Int by Difficulty.pError
    val createdAt: Instant by Difficulty.createdAt
    var stars: BigDecimal? by Difficulty.stars
    val requirements by Difficulty.requirements
    val suggestions by Difficulty.suggestions
    val information by Difficulty.information
    val warnings by Difficulty.warnings
}
