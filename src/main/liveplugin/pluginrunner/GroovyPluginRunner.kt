/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package liveplugin.pluginrunner

import com.intellij.util.Function
import groovy.lang.Binding
import groovy.util.GroovyScriptEngine
import liveplugin.MyFileUtil.*
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDependencies
import org.codehaus.groovy.control.CompilationFailedException
import java.io.IOException
import java.util.*

class GroovyPluginRunner(
    private val scriptName: String,
    private val errorReporter: ErrorReporter,
    environment: Map<String, String>
): PluginRunner {
    private val environment = HashMap(environment)

    override fun canRunPlugin(pathToPluginFolder: String): Boolean {
        return findScriptFileIn(pathToPluginFolder, scriptName) != null
    }

    override fun runPlugin(
        pathToPluginFolder: String,
        pluginId: String,
        binding: Map<String, *>,
        runOnEDTCallback: Function<Runnable, Void>
    ) {
        val mainScript = findScriptFileIn(pathToPluginFolder, scriptName)
        runGroovyScript(asUrl(mainScript), pathToPluginFolder, pluginId, binding, runOnEDTCallback)
    }

    override fun scriptName() = scriptName

    private fun runGroovyScript(
        mainScriptUrl: String,
        pathToPluginFolder: String,
        pluginId: String,
        binding: Map<String, *>,
        runPluginCallback: Function<Runnable, Void>
    ) {
        try {
            environment.put("PLUGIN_PATH", pathToPluginFolder)

            val dependentPlugins = findPluginDependencies(readLines(mainScriptUrl), groovyDependsOnPluginKeyword)
            val pathsToAdd = findClasspathAdditions(readLines(mainScriptUrl), groovyAddToClasspathKeyword, environment, onError = { path ->
                errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
            }).toMutableList()
            val pluginFolderUrl = "file:///$pathToPluginFolder/" // prefix with "file:///" so that unix-like path works on windows
            pathsToAdd.add(pluginFolderUrl)
            val classLoader = createClassLoaderWithDependencies(pathsToAdd, dependentPlugins, mainScriptUrl, pluginId, errorReporter)

            // assume that GroovyScriptEngine is thread-safe
            // (according to this http://groovy.329449.n5.nabble.com/Is-the-GroovyScriptEngine-thread-safe-td331407.html)
            val scriptEngine = GroovyScriptEngine(pluginFolderUrl, classLoader)
            try {
                scriptEngine.loadScriptByName(mainScriptUrl)
            } catch (e: Exception) {
                errorReporter.addLoadingError(pluginId, e)
                return
            }

            runPluginCallback.`fun`(Runnable {
                try {
                    scriptEngine.run(mainScriptUrl, createGroovyBinding(binding))
                } catch (e: Exception) {
                    errorReporter.addRunningError(pluginId, e)
                }
            })

        } catch (e: IOException) {
            errorReporter.addLoadingError(pluginId, "Error creating scripting engine. " + e.message)
        } catch (e: CompilationFailedException) {
            errorReporter.addLoadingError(pluginId, "Error compiling script. " + e.message)
        } catch (e: LinkageError) {
            errorReporter.addLoadingError(pluginId, "Error linking script. " + e.message)
        } catch (e: Error) {
            errorReporter.addLoadingError(pluginId, e)
        } catch (e: Exception) {
            errorReporter.addLoadingError(pluginId, e)
        }

    }

    companion object {
        @JvmField val mainScript = "plugin.groovy"
        @JvmField val testScript = "plugin-test.groovy"
        val groovyAddToClasspathKeyword = "// " + PluginRunner.addToClasspathKeyword
        val groovyDependsOnPluginKeyword = "// " + PluginRunner.dependsOnPluginKeyword

        private fun createGroovyBinding(binding: Map<String, *>): Binding {
            val result = Binding()
            for ((key, value) in binding) {
                result.setVariable(key, value)
            }
            return result
        }
    }
}
