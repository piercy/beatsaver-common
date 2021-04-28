package io.beatmaps.common.zip

import com.fasterxml.jackson.module.kotlin.readValue
import io.beatmaps.common.beatsaber.DifficultyBeatmap
import io.beatmaps.common.beatsaber.DifficultyBeatmapSet
import io.beatmaps.common.beatsaber.MapInfo
import io.beatmaps.common.beatsaber.BSDifficulty
import io.beatmaps.common.copyTo
import io.beatmaps.common.jackson
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestOutputStream
import java.util.*
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible
import kotlin.streams.toList

val KProperty0<*>.isLazyInitialized: Boolean
    get() {
        if (this !is Lazy<*>) return true

        // Prevent IllegalAccessException from JVM access check on private properties.
        val originalAccessLevel = isAccessible
        isAccessible = true
        val isLazyInitialized = (getDelegate() as Lazy<*>).isInitialized()
        // Reset access level.
        isAccessible = originalAccessLevel
        return isLazyInitialized
    }

data class ExtractedInfo(val allowedFiles: List<String>, val md: DigestOutputStream, var mapInfo: MapInfo, val score: Short, val diffs: MutableMap<DifficultyBeatmapSet, MutableMap<DifficultyBeatmap, BSDifficulty>> = mutableMapOf(), var duration: Float = 0f, var thumbnail: ByteArrayOutputStream? = null)

interface IMapScorer {
    fun scoreMap(infoFile: MapInfo, audio: File, block: (String) -> BSDifficulty): Short
}
interface IMapScorerProvider {
    fun create(): IMapScorer
}

class ZipHelper(private val fs: FileSystem, val filesOriginalCase: Set<String>, val files: Set<String>, val directories: Set<String>) : AutoCloseable {
    val infoPath: Path by lazy {
        fs.getPath(filesOriginalCase.firstOrNull { it.endsWith("/Info.dat", true) } ?: error("Missing Info.dat"))
    }

    val audioFile: File by lazy {
        val path = fromInfo(info._songFilename)
        File.createTempFile("audio", ".ogg").also { file ->
            file.deleteOnExit()

            path?.inputStream()?.use { iss ->
                file.outputStream().use {
                    iss.copyTo(it, sizeLimit = 50 * 1024 * 1024)
                }
            }
        }
    }

    private val audioInitialized = ::audioFile.isLazyInitialized

    val info by lazy {
        infoPath.inputStream().use {
            val byteArrayOutputStream = ByteArrayOutputStream()
            it.copyTo(byteArrayOutputStream, sizeLimit = 50 * 1024 * 1024)

            jackson.readValue<MapInfo>(byteArrayOutputStream.toByteArray())
        }
    }

    fun infoPrefix(): String = infoPath.parent.toString().removeSuffix("/") + "/"
    fun fromInfo(path: String) = getPath(infoPrefix() + path)

    private val diffs = mutableMapOf<String, BSDifficulty>()
    fun diff(path: String) = diffs.getOrPut(path) {
        (fromInfo(path) ?: error("Difficulty file missing")).inputStream().buffered().use { stream ->
            jackson.readValue(stream)
        }
    }

    fun getPath(path: String) =
        filesOriginalCase.find { it.equals(path, true) }?.let {
            fs.getPath(it)
        }

    fun newPath(path: String): Path = fs.getPath(path)

    fun scoreMap() =
        ServiceLoader.load(IMapScorerProvider::class.java)
            .findFirst()
            .map { s ->
                s.create().scoreMap(info, audioFile) {
                    diff(it)
                }
            }.orElse(0)

    override fun close() {
        if (audioInitialized) {
            Files.delete(audioFile.toPath())
        }
    }

    companion object {
        fun <T> openZip(file: File, block: ZipHelper.() -> T) =
            FileSystems.newFileSystem(file.toPath(), mapOf("create" to "false")).use { fs ->
                val lists = fs.rootDirectories.map {
                    Files.walk(it).toList().partition { p ->
                        p.isDirectory()
                    }
                }

                val files = lists.flatMap { it.second }.map { it.toString() }
                val directories = lists.flatMap { it.first }.map { it.toString() }

                ZipHelper(fs, files.toSet(), files.map { it.toLowerCase() }.toSet(), directories.toSet()).use(block)
            }
    }
}

operator fun <T> Lazy<T>.getValue(thisRef: Any?, property: KProperty<*>) = value