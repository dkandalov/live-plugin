package liveplugin

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.Collections.emptyList
import java.util.regex.Pattern


object MyFileUtil {
    fun listFilesIn(folder: File): List<File> {
        return folder.listFiles()?.toList() ?: emptyList()
    }

    fun fileNamesMatching(regexp: String, path: String): List<String> {
        return fileNamesMatching(regexp, File(path))
    }

    fun fileNamesMatching(regexp: String, path: File): List<String> {
        return FileUtil.findFilesByMask(Pattern.compile(regexp), path).map { it.name }
    }

    fun asUrl(file: File): String {
        return try {
            file.toURI().toURL().toString()
        } catch (e: MalformedURLException) {
            throw RuntimeException(e)
        }
    }

    fun File.toUrl() = this.toURI().toURL()!!

    fun findScriptFileIn(path: String, fileName: String): File? {
        val result = findScriptFilesIn(path, fileName)
        return when {
            result.isEmpty() -> null
            result.size == 1 -> result[0]
            else -> {
                val filePaths = result.map { it.absolutePath }
                throw IllegalStateException("Found several scripts files under " + path + ":\n" + StringUtil.join(filePaths, ";\n"))
            }
        }
    }

    fun findScriptFilesIn(path: String, fileName: String): List<File> {
        val rootScriptFile = File(path + File.separator + fileName)
        return if (rootScriptFile.exists()) listOf(rootScriptFile)
        else allFilesInDirectory(File(path)).filter { fileName == it.name }
    }

    private fun allFilesInDirectory(dir: File): List<File> {
        val result = LinkedList<File>()
        val files = dir.listFiles() ?: return result

        for (file in files) {
            if (file.isFile) {
                result.add(file)
            } else if (file.isDirectory) {
                result.addAll(allFilesInDirectory(file))
            }
        }
        return result
    }

    fun readLines(url: String): Array<String> {
        val reader = BufferedReader(InputStreamReader(URL(url).openStream()))
        return FileUtil.loadTextAndClose(reader)
            .split("\n".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
    }
}
