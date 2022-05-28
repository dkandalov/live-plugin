package liveplugin.implementation.pluginrunner

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.Result
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.PluginError.SetupError
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinScriptFile
import org.junit.After
import org.junit.Test
import java.io.File

class KotlinPluginRunnerTests {
    private val pluginRunner = KotlinPluginRunner(kotlinScriptFile, emptyMap())
    private val pluginDir = FileUtil.createTempDirectory("", "")

    @After fun teardown() {
        pluginDir.deleteRecursively()
    }

    @Test fun `minimal kotlin script`() {
        pluginDir.createFile("plugin.kts", text = "println(123)")
        runPluginIn(pluginDir).expectSuccess()
    }

    @Test fun `kotlin script which uses IntelliJ API`() {
        pluginDir.createFile("plugin.kts", text = "println(com.intellij.openapi.project.Project::class.java)")
        runPluginIn(pluginDir).expectSuccess()
    }

    @Test fun `kotlin script which uses function from another file`() {
        pluginDir.createFile(
            "plugin.kts",
            text = """
                import lib.libFunction
                println(libFunction())
            """
        )
        pluginDir.createDir("lib").createFile(
            "lib.kt",
            text = """
                package lib
                fun libFunction(): Long = 42
            """
        )

        runPluginIn(pluginDir).expectSuccess()
    }

    @Test fun `kotlin script with errors`() {
        pluginDir.createFile("plugin.kts", text = "abc")

        val reason = runPluginIn(pluginDir).expectFailure() as SetupError

        assert(reason.throwable.toString().contains("unresolved reference: abc"))
    }

    private fun runPluginIn(dir: File): Result<Unit, PluginError> {
        val plugin = pluginRunner.setup(LivePlugin(dir.toFilePath()), null).expectSuccess()
        return pluginRunner.run(plugin, Binding(null, false, "", Disposer.newDisposable()))
    }
}