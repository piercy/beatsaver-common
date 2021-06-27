package io.beatmaps.zip

import io.beatmaps.common.beatsaber.DifficultyBeatmap
import io.beatmaps.common.beatsaber.DifficultyBeatmapSet
import io.beatmaps.common.beatsaber.MapInfo
import io.beatmaps.common.checkParity
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.dbo.maxAllowedNps
import io.beatmaps.common.zip.ZipHelper
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import java.math.BigDecimal

data class DiffStats(val chroma: Boolean, val noodle: Boolean, val nps: BigDecimal)

fun ZipHelper.parseDifficulty(hash: String, diff: DifficultyBeatmap, char: DifficultyBeatmapSet, map: MapInfo, ver: VersionsDao? = null): DiffStats {
    val version = ver ?: VersionsDao.wrapRow(Versions.select {
        Versions.hash eq hash
    }.first())

    var npsLocal = BigDecimal.ZERO
    var chromaLocal = false
    var noodleLocal = false

    Difficulty.insertIgnore {
        it[mapId] = version.mapId
        it[versionId] = version.id
        it[createdAt] = version.uploaded

        it[njs] = diff._noteJumpMovementSpeed
        it[offset] = diff._noteJumpStartBeatOffset
        it[characteristic] = char.enumValue()
        it[difficulty] = diff.enumValue()

        val bsdiff = diff(diff._beatmapFilename)

        checkParity(bsdiff).also { pr ->
            it[pReset] = pr.info
            it[pError] = pr.errors
            it[pWarn] = pr.warnings
        }

        val sorted = bsdiff._notes.sortedBy { note -> note._time }
        val partitioned = bsdiff._notes.partition { note -> note._type != 3 }
        val len = if (sorted.isNotEmpty()) { sorted.last()._time - sorted.first()._time } else 0f

        it[notes] = partitioned.first.size
        it[bombs] = partitioned.second.size
        it[obstacles] = bsdiff._obstacles.size
        it[events] = bsdiff._events.size
        it[length] = len.toBigDecimal()
        it[seconds] = BigDecimal.valueOf((if (map._beatsPerMinute == 0f) 0 else (60 / map._beatsPerMinute) * len).toDouble())

        npsLocal = BigDecimal.valueOf(if (len == 0f) 0.0 else ((partitioned.first.size / len) * (map._beatsPerMinute / 60)).toDouble()).min(maxAllowedNps)
        chromaLocal = diff._customData?._requirements?.contains("Chroma") ?: false || diff._customData?._suggestions?.contains("Chroma") ?: false
        noodleLocal = diff._customData?._requirements?.contains("Noodle Extensions") ?: false
        it[nps] = npsLocal
        it[chroma] = chromaLocal
        it[ne] = noodleLocal
        it[me] = diff._customData?._requirements?.contains("Mapping Extensions") ?: false

        it[requirements] = diff._customData?._requirements?.toTypedArray()
        it[suggestions] = diff._customData?._suggestions?.toTypedArray()
        it[information] = diff._customData?._information?.toTypedArray()
        it[warnings] = diff._customData?._warnings?.toTypedArray()
    }

    return DiffStats(chromaLocal, noodleLocal, npsLocal)
}