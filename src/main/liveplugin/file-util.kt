package liveplugin

import java.io.File
import java.net.URL
import java.util.Collections.emptyList

fun File.filesList(): List<File> = listFiles()?.toList() ?: emptyList()

fun File.toUrlString(): String = toURI().toURL().toString()

fun File.toUrl(): URL = this.toURI().toURL()

fun findScriptFileIn(path: String?, fileName: String): File? {
    if (path == null) return null

    val result = findScriptFilesIn(path, fileName)
    return when {
        result.isEmpty() -> null
        result.size == 1 -> result[0]
        else             -> error("Found several scripts files under " + path + ":\n" + result.joinToString(";\n") { it.absolutePath })
    }
}

fun findScriptFilesIn(path: String, fileName: String): List<File> {
    val rootScriptFile = File(path + File.separator + fileName)
    return if (rootScriptFile.exists()) listOf(rootScriptFile)
    else File(path).allFiles().filter { fileName == it.name }.toList()
}

fun File.allFiles(): Sequence<File> =
    walkTopDown().asSequence().filter { it.isFile }

fun readLines(url: String) =
    URL(url).openStream().bufferedReader().readLines()
