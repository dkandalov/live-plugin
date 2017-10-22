package liveplugin.pluginrunner

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PathManager.getLibPath
import com.intellij.openapi.application.PathManager.getPluginsPath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil.join
import com.intellij.util.Function
import com.intellij.util.PathUtil
import com.intellij.util.containers.ContainerUtil.flatten
import com.intellij.util.containers.ContainerUtil.map
import liveplugin.MyFileUtil
import liveplugin.MyFileUtil.*
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createParentClassLoader
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDependencies
import scala.Some
import scala.`package$`
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.Results
import scala.tools.nsc.settings.MutableSettings
import scala.xml.Null
import java.io.File
import java.io.File.pathSeparator
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Arrays.asList

/**
 * This class should not be loaded unless scala libs are on classpath.
 */
class ScalaPluginRunner(private val errorReporter: ErrorReporter, private val environment: MutableMap<String, String>): PluginRunner {

    override fun canRunPlugin(pathToPluginFolder: String): Boolean {
        return findScriptFileIn(pathToPluginFolder, mainScript) != null
    }

    override fun runPlugin(pathToPluginFolder: String, pluginId: String,
                           binding: Map<String, *>, runOnEDTCallback: Function<Runnable, Void>) {
        val scriptFile = MyFileUtil.findScriptFileIn(pathToPluginFolder, ScalaPluginRunner.mainScript)!!

        var interpreter: IMain? = null
        synchronized(interpreterLock) {
            try {
                environment.put("PLUGIN_PATH", pathToPluginFolder)

                val dependentPlugins = findPluginDependencies(readLines(asUrl(scriptFile)), scalaDependsOnPluginKeyword)
                val additionalPaths = findClasspathAdditions(readLines(asUrl(scriptFile)), scalaAddToClasspathKeyword, environment, onError = { path ->
                    errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
                })
                val classpath = createInterpreterClasspath(additionalPaths)
                val parentClassLoader = createParentClassLoader(dependentPlugins, pluginId, errorReporter)
                interpreter = initInterpreter(classpath, parentClassLoader)

            } catch (e: Exception) {
                errorReporter.addLoadingError("Failed to init scala interpreter", e)
                return
            } catch (e: LinkageError) {
                errorReporter.addLoadingError("Failed to init scala interpreter", e)
                return
            }

            interpreterOutput.buffer.delete(0, interpreterOutput.buffer.length)
            for ((key, value) in binding) {
                val valueClassName = if (value == null) Null::class.java.canonicalName else value.javaClass.canonicalName
                interpreter!!.bind(key, valueClassName, value, `package$`.`MODULE$`.List().empty())
            }
        }

        runOnEDTCallback.`fun`(Runnable {
            synchronized(interpreterLock) {
                val result: Results.Result
                try {
                    result = interpreter!!.interpret(FileUtil.loadFile(scriptFile))
                    if (result !is Results.`Success$`) {
                        errorReporter.addRunningError(pluginId, interpreterOutput.toString())
                    }
                } catch (e: LinkageError) {
                    errorReporter.addLoadingError(pluginId, "Error linking script file: " + scriptFile)
                } catch (e: Exception) {
                    errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile)
                }
            }
        })
    }

    override fun scriptName(): String {
        return mainScript
    }

    companion object {
        private val scalaDependsOnPluginKeyword = "// " + PluginRunner.dependsOnPluginKeyword
        @JvmField val mainScript = "plugin.scala"
        private val scalaAddToClasspathKeyword = "// " + PluginRunner.addToClasspathKeyword
        private val interpreterOutput = StringWriter()
        private val interpreterLock = Any()

        @Throws(ClassNotFoundException::class)
        private fun initInterpreter(interpreterClasspath: String, parentClassLoader: ClassLoader): IMain {
            val settings = Settings()
            val bootClasspath = settings.bootclasspath() as MutableSettings.PathSetting
            bootClasspath.append(interpreterClasspath)

            settings.`explicitParentLoader_$eq`(Some(parentClassLoader))

            (settings.usejavacp() as MutableSettings.BooleanSetting).tryToSetFromPropertyValue("true")

            return IMain(settings, PrintWriter(interpreterOutput))
        }

        @Throws(ClassNotFoundException::class)
        private fun createInterpreterClasspath(additionalPaths: List<String>): String {
            val findPluginJars = { pluginPath: File ->
                if (pluginPath.isFile) {
                    listOf(pluginPath)
                } else {
                    File(pluginPath, "lib").listFiles({ _, fileName -> fileName.endsWith(".jar") || fileName.endsWith(".zip") })?.toList() ?: emptyList()
                }
            }

            val compilerPath = PathUtil.getJarPathForClass(Class.forName("scala.tools.nsc.Interpreter"))
            val scalaLibPath = PathUtil.getJarPathForClass(Class.forName("scala.Some"))
            val intellijLibPath = join(map(withDefault(arrayOfNulls<File>(0), File(getLibPath()).listFiles())) { it.absolutePath }, pathSeparator)
            val allNonCorePluginsPath = join(map(flatten(map(withDefault(arrayOfNulls<File>(0), File(getPluginsPath()).listFiles()), findPluginJars)), { it.absolutePath }), pathSeparator)
            val livePluginPath = PathManager.getResourceRoot(ScalaPluginRunner::class.java, "/liveplugin/") // this is only useful when running liveplugin from IDE (it's not packed into jar)
            return join(asList<String>(compilerPath, scalaLibPath, livePluginPath, intellijLibPath, allNonCorePluginsPath), pathSeparator) +
                pathSeparator + join(additionalPaths, pathSeparator)
        }

        private fun <T> withDefault(defaultValue: T, value: T?): T {
            return value ?: defaultValue
        }
    }
}
