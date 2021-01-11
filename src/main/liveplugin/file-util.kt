package liveplugin

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.net.URL
import java.util.Collections.emptyList

fun File.filesList(): List<File> = listFiles()?.toList() ?: emptyList()

fun File.toUrlString(): String = toURI().toURL().toString()

fun File.toUrl(): URL = this.toURI().toURL()

@Suppress("DEPRECATION")
fun File.toFilePath() =
    FilePath(FileUtilRt.toSystemIndependentName(this.absolutePath).toLowerCase())

@Suppress("DEPRECATION")
fun VirtualFile.toFilePath() =
    FilePath(this.path.toLowerCase())

/**
 * Full path with system-independent separator '/' (as it's use in IJ API)
 */
data class FilePath @Deprecated("Use extension functions instead") constructor(val value: String) {
    fun toFile() = File(value)

    fun listFiles(): List<File> = toFile().filesList()

    fun parent(): FilePath = File(value).parentFile.toFilePath()
}

fun findScriptFileIn(path: FilePath, fileName: String): File? {
    val result = findScriptFilesIn(path, fileName)
    return when {
        result.isEmpty() -> null
        result.size == 1 -> result[0]
        else             -> error("Found several scripts files under " + path + ":\n" + result.joinToString(";\n") { it.absolutePath })
    }
}

fun findScriptFilesIn(path: FilePath, fileName: String): List<File> {
    val rootScriptFile = File("$path/$fileName")
    return if (rootScriptFile.exists()) listOf(rootScriptFile)
    else path.toFile().allFiles().filter { fileName == it.name }.toList()
}

fun File.allFiles(): Sequence<File> =
    walkTopDown().asSequence().filter { it.isFile }

fun readLines(url: String) =
    URL(url).openStream().bufferedReader().readLines()
