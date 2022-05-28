package liveplugin.implementation.pluginrunner

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.Result
import liveplugin.implementation.common.Result.Failure
import liveplugin.implementation.common.Result.Success
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.groovyScriptFile
import org.junit.After
import org.junit.Test
import java.io.File

class GroovyPluginRunnerTests {
    private val pluginRunner = GroovyPluginRunner(groovyScriptFile, emptyMap())
    private val pluginDir = FileUtil.createTempDirectory("", "")

    @After fun teardown() {
        pluginDir.deleteRecursively()
    }

    @Test fun `groovy script`() {
        pluginDir.createFile(
            "plugin.groovy",
            text = """
                // import to ensure that script has access to parent classloader from which test is run
                import com.intellij.openapi.util.io.FileUtil
    
                // some groovy code
                def a = 1
                def b = 2
                a + b
            """.trimIndent()
        )
        runPluginIn(pluginDir).expectSuccess()
    }

    @Test fun `groovy script which uses groovy class from another file`() {
        pluginDir.createFile(
            "plugin.groovy",
            text = """
                import myPackage.Util
                Util.myFunction()
            """.trimIndent()
        )
        pluginDir.createDir("myPackage").createFile(
            "Util.groovy",
            text = """
                package myPackage
                class Util {
                    static myFunction() { 42 }
                }
            """.trimIndent()
        )

        runPluginIn(pluginDir).expectSuccess()
    }

    @Test fun `groovy script with errors`() {
        val scriptCode = "invalid code + 1"
        pluginDir.createFile("plugin.groovy", scriptCode)
        val failureReason = runPluginIn(pluginDir).expectFailure()

        assert(failureReason.throwable.toString().startsWith("groovy.lang.MissingPropertyException"))
    }

    private fun runPluginIn(dir: File): Result<Unit, RunningError> {
        val plugin = pluginRunner.setup(LivePlugin(dir.toFilePath()), null).expectSuccess()
        return pluginRunner.run(plugin, Binding(null, false, "", Disposer.newDisposable()))
    }
}

fun File.createFile(fileName: String, text: String): File {
    val file = File(this, fileName)
    file.writeText(text)
    return file
}

fun File.createDir(name: String) =
    File(this, name).also { it.mkdirs() }

fun <Value, Reason> Result<Value, Reason>.expectSuccess(): Value {
    if (this is Success) return value
    throw AssertionError("Expected Success but was $this")
}

fun <Value, Reason> Result<Value, Reason>.expectFailure(): Reason {
    if (this is Failure) return reason
    throw AssertionError("Expected Failure but was $this")
}
