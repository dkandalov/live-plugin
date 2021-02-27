package liveplugin

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File
import java.net.URL
import java.util.Collections.emptyList

fun File.toUrlString(): String = toURI().toURL().toString()

fun File.toUrl(): URL = this.toURI().toURL()

@Suppress("DEPRECATION")
fun String.toFilePath() =
    FilePath(FileUtilRt.toSystemIndependentName(this).toLowerCase())

@Suppress("DEPRECATION")
fun File.toFilePath() =
    FilePath(FileUtilRt.toSystemIndependentName(this.absolutePath).toLowerCase())

@Suppress("DEPRECATION")
fun VirtualFile.toFilePath() =
    FilePath(this.path.toLowerCase())

/**
 * Full path with system-independent separator '/' (as it's use in IJ API)
 */
@Suppress("DEPRECATION")
data class FilePath @Deprecated("Use extension functions instead") constructor(val value: String) {
    private val file = File(value)

    fun toFile() = file

    fun allFiles() = file.walkTopDown().asSequence().filter { it.isFile }

    fun listFiles() = file.listFiles()?.toList() ?: emptyList()

    fun listFiles(predicate: (File) -> Boolean) = listFiles().filter(predicate)

    fun exists() = file.exists()

    fun toVirtualFile(): VirtualFile? = VirtualFileManager.getInstance().findFileByUrl("file:///$this")

    operator fun plus(that: String): FilePath = FilePath("$value/$that")

    override fun toString() = value
}

fun FilePath.find(fileName: String): File? {
    val result = findAll(fileName)
    return when {
        result.isEmpty() -> null
        result.size == 1 -> result.first().toFile()
        else             -> error("Found several scripts files under " + this + ":\n" + result.joinToString(";\n") { it.toFile().absolutePath })
    }
}

fun FilePath.findAll(fileName: String): List<FilePath> {
    val targetFilePath = this + fileName
    return if (targetFilePath.exists()) listOf(targetFilePath)
    else allFiles().filter { fileName == it.name }.map { it.toFilePath() }.toList()
}
