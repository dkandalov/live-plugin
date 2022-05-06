package liveplugin.implementation.pluginrunner

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.Result
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.AnError.LoadingError
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner
import org.junit.After
import org.junit.Test
import java.io.File

class KotlinPluginRunnerTests {
    private val pluginRunner = KotlinPluginRunner("plugin.kts", emptyMap())
    private val rootFolder = FileUtil.createTempDirectory("", "")
    private val libPackageFolder = File(rootFolder, "lib").also { it.mkdir() }

    @After fun teardown() {
        rootFolder.deleteRecursively()
    }

    @Test fun `minimal kotlin script`() {
        val scriptCode = "println(123)"
        createFile("plugin.kts", scriptCode, rootFolder)
        runPlugin().expectSuccess()
    }

    @Test fun `kotlin script which uses IJ API`() {
        val scriptCode = "println(com.intellij.openapi.project.Project::class.java)"
        createFile("plugin.kts", scriptCode, rootFolder)

        runPlugin().expectSuccess()
    }

    @Test fun `kotlin script which uses function from another file`() {
        val scriptCode = """
			import lib.libFunction
			println(libFunction())
		"""
        val libScriptCode = """
			package lib
			fun libFunction(): Long = 42
		"""
        createFile("plugin.kts", scriptCode, rootFolder)
        createFile("lib.kt", libScriptCode, libPackageFolder)

        runPlugin().expectSuccess()
    }

    @Test fun `kotlin script with errors`() {
        val scriptCode = "abc"
        createFile("plugin.kts", scriptCode, rootFolder)

        val reason = runPlugin().expectFailure() as LoadingError

        assert(reason.throwable.toString().contains("unresolved reference: abc"))
    }

    private fun runPlugin(): Result<Unit, AnError> {
        val plugin = pluginRunner.setup(LivePlugin(rootFolder.toFilePath()), null).expectSuccess()
        return pluginRunner.run(plugin, Binding(null, false, "", Disposer.newDisposable()))
    }
}