package liveplugin.implementation.pluginrunner

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.Result
import liveplugin.implementation.common.Result.Failure
import liveplugin.implementation.common.Result.Success
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.AnError.RunningError
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner
import org.junit.After
import org.junit.Test
import java.io.File

class GroovyPluginRunnerTests {
    private val pluginRunner = GroovyPluginRunner(GroovyPluginRunner.groovyScriptFile, emptyMap())
    private val rootFolder = FileUtil.createTempDirectory("", "")
    private var myPackageFolder = File(rootFolder, "myPackage").also { it.mkdirs() }

    @After fun teardown() {
        rootFolder.deleteRecursively()
    }

    @Test fun `groovy script`() {
        val scriptCode = """
			// import to ensure that script has access to parent classloader from which test is run
			import com.intellij.openapi.util.io.FileUtil

			// some groovy code
			def a = 1
			def b = 2
			a + b
		"""
        createFile("plugin.groovy", scriptCode, rootFolder)
        runPlugin().expectSuccess()
    }

    @Test fun `groovy script which uses groovy class from another file`() {
        val scriptCode = """
			import myPackage.Util
			Util.myFunction()
		"""
        val scriptCode2 = """
			package myPackage
			class Util {
				static myFunction() { 42 }
			}
		"""
        createFile("plugin.groovy", scriptCode, rootFolder)
        createFile("Util.groovy", scriptCode2, myPackageFolder)

        runPlugin().expectSuccess()
    }

    @Test fun `groovy script with errors`() {
        val scriptCode = "invalid code + 1"
        createFile("plugin.groovy", scriptCode, rootFolder)
        val failureReason = runPlugin().expectFailure() as RunningError

        assert(failureReason.throwable.toString().startsWith("groovy.lang.MissingPropertyException"))
    }

    private fun runPlugin(): Result<Unit, AnError> {
        val plugin = pluginRunner.setup(LivePlugin(rootFolder.toFilePath()), null).expectSuccess()
        return pluginRunner.run(plugin, Binding(null, false, "", Disposer.newDisposable()))
    }
}

fun createFile(fileName: String, fileContent: String, directory: File): File {
    val file = File(directory, fileName)
    file.writeText(fileContent)
    return file
}

fun <Value, Reason> Result<Value, Reason>.expectSuccess(): Value {
    if (this is Success) return value
    throw AssertionError("Expected Success but was $this")
}

fun <Value, Reason> Result<Value, Reason>.expectFailure(): Reason {
    if (this is Failure) return reason
    throw AssertionError("Expected Failure but was $this")
}
