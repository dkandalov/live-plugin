package liveplugin.implementation.common

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File
import java.util.Collections.emptyList

@Suppress("DEPRECATION")
fun String.toFilePath() =
    FilePath(FileUtilRt.toSystemIndependentName(this))

@Suppress("DEPRECATION")
fun VirtualFile.toFilePath() =
    FilePath(this.path)

@Suppress("DEPRECATION")
fun File.toFilePath() =
    FilePath(FileUtilRt.toSystemIndependentName(absolutePath))

/**
 * File path with system-independent separator '/' (as it's used in IJ API)
 */
data class FilePath @Deprecated("Use the extension functions declared above") constructor(val value: String) {
    private val file = File(value)

    val name: String = file.name
    val isDirectory: Boolean = file.isDirectory
    val extension: String = file.extension
    val parent: FilePath? get() = file.parent?.toFilePath()

    fun allFiles(): Sequence<FilePath> = file.walkTopDown().filter { it.isFile }.map { it.toFilePath() }

    fun listFiles(): List<FilePath> = file.listFiles()?.map { it.toFilePath() } ?: emptyList()

    fun listFiles(predicate: (FilePath) -> Boolean) = listFiles().filter(predicate)

    fun exists() = file.exists()

    fun readText() = file.readText()

    operator fun plus(that: String) = "$value/$that".toFilePath()

    fun toFile() = file

    fun toVirtualFile(): VirtualFile? = VirtualFileManager.getInstance().findFileByUrl("file:///$this")

    fun find(fileName: String): File? {
        val targetFilePath = this + fileName
        val filePaths =
            if (targetFilePath.exists()) listOf(targetFilePath)
            else allFiles().filter { fileName == it.name }.toList()

        return when {
            filePaths.isEmpty() -> null
            filePaths.size == 1 -> filePaths.first().toFile()
            else                -> error("Found several scripts files under " + this + ":\n" + filePaths.joinToString(";\n") { it.toFile().absolutePath })
        }
    }

    fun delete() {
        file.delete()
    }

    override fun toString() = value
}
