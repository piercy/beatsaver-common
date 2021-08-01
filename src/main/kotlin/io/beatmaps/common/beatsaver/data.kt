package io.beatmaps.common.beatsaver

import java.io.File
import java.time.Instant

fun localFolder(hash: String) = File(System.getenv("ZIP_DIR") ?: "K:\\BeatSaver", hash.substring(0, 1))
fun localCoverFolder(hash: String) = File(System.getenv("COVER_DIR") ?: "K:\\BeatSaverCover", hash.substring(0, 1))
fun localAudioFolder(hash: String) = File(System.getenv("AUDIO_DIR") ?: "K:\\BeatSaverAudio", hash.substring(0, 1))

data class BeatsaverList(val docs: List<BeatsaverMap>, val totalDocs: Int, val lastPage: Int, val prevPage: Int, val nextPage: Int)
data class BeatsaverMap(
    val metadata: BeatsaverMetadata,
    val stats: BeatsaverStats,
    val description: String,
    val deletedAt: Instant?,
    val _id: String,
    val key: String,
    val name: String,
    val uploader: BeatsaverUploader,
    val hash: String,
    val uploaded: Instant,
    val directDownload: String,
    val downloadURL: String,
    val coverURL: String
)
data class BeatsaverStats(val downloads: Int, val plays: Int, val downVotes: Int, val upVotes: Int, val heat: Float, val rating: Float)
data class BeatsaverUploader(val _id: String, val username: String)
data class BeatsaverMetadata(
    val difficulties: BeatsaverDifficulties,
    val duration: Int,
    val automapper: String?,
    val characteristics: List<BeatsaverCharacteristics>,
    val levelAuthorName: String,
    val songAuthorName: String,
    val songName: String,
    val songSubName: String,
    val bpm: Float
)
data class BeatsaverDifficulties(val easy: Boolean, val normal: Boolean, val hard: Boolean, val expert: Boolean, val expertPlus: Boolean)
data class BeatsaverCharacteristics(val difficulties: BeatsaverCharacteristicDifficulties, val name: String)
data class BeatsaverCharacteristicDifficulties(
    val easy: BeatsaverCharacteristicDifficulty?,
    val normal: BeatsaverCharacteristicDifficulty?,
    val hard: BeatsaverCharacteristicDifficulty?,
    val expert: BeatsaverCharacteristicDifficulty?,
    val expertPlus: BeatsaverCharacteristicDifficulty?
)
data class BeatsaverCharacteristicDifficulty(val duration: Float, val length: Int, val njs: Float, val njsOffset: Float, val bombs: Int, val notes: Int, val obstacles: Int)
