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

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.ContainerUtil.map
import com.intellij.util.ui.UIUtil
import liveplugin.IDEUtil
import liveplugin.IDEUtil.SingleThreadBackgroundRunner
import liveplugin.Icons
import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.Companion.checkThatGroovyIsOnClasspath
import liveplugin.LivePluginAppComponent.Companion.clojureIsOnClassPath
import liveplugin.LivePluginAppComponent.Companion.scalaIsOnClassPath
import liveplugin.Settings
import liveplugin.pluginrunner.GroovyPluginRunner.Companion.mainScript
import liveplugin.pluginrunner.PluginRunner.Companion.ideStartup
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.toolwindow.PluginToolWindowManager
import java.io.File
import java.util.*
import java.util.Collections.emptyList


class RunPluginAction: AnAction("Run Plugin", "Run selected plugins", Icons.runPluginIcon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        runCurrentPlugin(event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = !findCurrentPluginIds(event).isEmpty()
    }

    private fun runCurrentPlugin(event: AnActionEvent) {
        IDEUtil.saveAllFiles()
        val pluginIds = findCurrentPluginIds(event)
        val errorReporter = ErrorReporter()
        runPlugins(pluginIds, event, errorReporter, createPluginRunners(errorReporter))
    }

    companion object {
        private val backgroundRunner = SingleThreadBackgroundRunner("LivePlugin thread")
        private val runOnEdt = { f: () -> Unit ->
            UIUtil.invokeAndWaitIfNeeded(Runnable{ f() })
        }
        private val disposableKey = "pluginDisposable"
        private val bindingByPluginId = WeakHashMap<String, Map<String, Any?>>()

        fun runPlugins(pluginIds: Collection<String>, event: AnActionEvent,
                       errorReporter: ErrorReporter, pluginRunners: List<PluginRunner>) {
            checkThatGroovyIsOnClasspath()

            val project = event.project
            val isIdeStartup = event.place == ideStartup

            if (!isIdeStartup) {
                Settings.countPluginsUsage(pluginIds)
            }

            val runPlugins = {
                for (pluginId in pluginIds) {
                    try {
                        val pathToPluginFolder = LivePluginAppComponent.pluginIdToPathMap()[pluginId] // TODO not thread-safe
                        val pluginRunner = pluginRunners.find { pathToPluginFolder != null && it.canRunPlugin(pathToPluginFolder) }
                        if (pluginRunner == null) {
                            val scriptNames = map(pluginRunners) { it -> it.scriptName() }
                            errorReporter.addNoScriptError(pluginId, scriptNames)
                        } else {
                            val oldBinding = bindingByPluginId[pluginId]
                            if (oldBinding != null) {
                                ApplicationManager.getApplication().invokeAndWait({
                                    try {
                                        Disposer.dispose(oldBinding[disposableKey] as Disposable)
                                    } catch (e: Exception) {
                                        errorReporter.addRunningError(pluginId, e)
                                    }
                                }, ModalityState.NON_MODAL)
                            }
                            val binding = createBinding(pathToPluginFolder!!, project, isIdeStartup)
                            bindingByPluginId.put(pluginId, binding)

                            pluginRunner.runPlugin(pathToPluginFolder, pluginId, binding, runOnEdt)
                        }
                    } catch (e: Error) {
                        errorReporter.addLoadingError(pluginId, e)
                    } finally {
                        errorReporter.reportAllErrors { title, message -> IDEUtil.displayError(title, message, project) }
                    }
                }
            }

            backgroundRunner.run(project, "Loading plugin", runPlugins)
        }

        fun createPluginRunners(errorReporter: ErrorReporter): List<PluginRunner> {
            val result = ArrayList<PluginRunner>()
            result.add(GroovyPluginRunner(mainScript, errorReporter, environment()))
            result.add(KotlinPluginRunner(errorReporter, environment()))
            if (scalaIsOnClassPath()) result.add(ScalaPluginRunner(errorReporter, environment()))
            if (clojureIsOnClassPath()) result.add(ClojurePluginRunner(errorReporter, environment()))
            return result
        }

        private fun createBinding(pathToPluginFolder: String, project: Project?, isIdeStartup: Boolean): Map<String, Any?> {
            val disposable = object: Disposable {
                override fun dispose() {}
                override fun toString() = "LivePlugin: $pathToPluginFolder"
            }
            Disposer.register(ApplicationManager.getApplication(), disposable)

            return mapOf(
                "project" to project,
                "isIdeStartup" to isIdeStartup,
                "pluginPath" to pathToPluginFolder,
                disposableKey to disposable
            )
        }

        internal fun environment(): MutableMap<String, String> = HashMap(System.getenv())

        internal fun findCurrentPluginIds(event: AnActionEvent): List<String> {
            val pluginIds = pluginsSelectedInToolWindow(event)
            return if (!pluginIds.isEmpty() && pluginToolWindowHasFocus(event)) {
                pluginIds
            } else {
                pluginForCurrentlyOpenFile(event)
            }
        }

        private fun pluginToolWindowHasFocus(event: AnActionEvent): Boolean {
            val pluginToolWindow = PluginToolWindowManager.getToolWindowFor(event.project)
            return pluginToolWindow != null && pluginToolWindow.isActive
        }

        private fun pluginsSelectedInToolWindow(event: AnActionEvent): List<String> { // TODO get selected plugins through DataContext
            val pluginToolWindow = PluginToolWindowManager.getToolWindowFor(event.project) ?: return emptyList()
            return pluginToolWindow.selectedPluginIds()
        }

        private fun pluginForCurrentlyOpenFile(event: AnActionEvent): List<String> {
            val project = event.project ?: return emptyList()
            val selectedTextEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: return emptyList()

            val virtualFile = FileDocumentManager.getInstance().getFile(selectedTextEditor.document) ?: return emptyList()

            val file = File(virtualFile.path)
            val entry = LivePluginAppComponent.pluginIdToPathMap().entries.find {
                val pluginPath = it.value
                FileUtil.isAncestor(File(pluginPath), file, false)
            } ?: return emptyList()
            return listOf(entry.key)
        }
    }
}
