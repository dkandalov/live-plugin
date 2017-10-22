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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.IDEUtil
import liveplugin.Icons
import liveplugin.pluginrunner.GroovyPluginRunner.Companion.testScript
import java.util.*

class TestPluginAction: AnAction("Run Plugin Tests", "Run Plugin Integration Tests", Icons.testPluginIcon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        testCurrentPlugin(event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = !RunPluginAction.findCurrentPluginIds(event).isEmpty()
    }

    private fun testCurrentPlugin(event: AnActionEvent) {
        IDEUtil.saveAllFiles()
        val pluginIds = RunPluginAction.findCurrentPluginIds(event)
        val errorReporter = ErrorReporter()
        RunPluginAction.runPlugins(pluginIds, event, errorReporter, createPluginRunners(errorReporter))
    }

    companion object {
        fun createPluginRunners(errorReporter: ErrorReporter): List<PluginRunner> {
            val result = ArrayList<PluginRunner>()
            result.add(GroovyPluginRunner(testScript, errorReporter, RunPluginAction.environment()))
            return result
        }
    }
}
