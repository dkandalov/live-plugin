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
package liveplugin.toolwindow.popup

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.actions.NewFolderAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.IconLoader
import liveplugin.IDEUtil
import liveplugin.LivePluginAppComponent.defaultPluginScript
import liveplugin.LivePluginAppComponent.defaultPluginTestScript
import liveplugin.pluginrunner.GroovyPluginRunner.mainScript
import liveplugin.pluginrunner.GroovyPluginRunner.testScript
import liveplugin.toolwindow.PluginToolWindowManager
import liveplugin.toolwindow.addplugin.AddNewGroovyPluginAction
import liveplugin.toolwindow.addplugin.AddNewKotlinPluginAction
import java.util.*
import javax.swing.Icon

class NewElementPopupAction: AnAction(), DumbAware, PopupAction {

    override fun actionPerformed(event: AnActionEvent) {
        showPopup(event.dataContext)
    }

    private fun showPopup(context: DataContext) {
        createPopup(context).showInBestPositionFor(context)
    }

    private fun createPopup(dataContext: DataContext): ListPopup {
        return JBPopupFactory.getInstance().createActionGroupPopup(
            IdeBundle.message("title.popup.new.element"),
            livePluginNewElementPopup,
            dataContext, false, true, false, null, -1,
            LangDataKeys.PRESELECT_NEW_ACTION_CONDITION.getData(dataContext)
        )
    }

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        presentation.isEnabled = true
    }

    companion object {
        private val folder = IconLoader.getIcon("/nodes/folder.png") // 16x16
        val inferIconFromFileType: Icon? = null

        private val livePluginNewElementPopup by lazy {
            ActionManager.getInstance().getAction("LivePlugin.NewElementPopup") as ActionGroup
        }

        private fun createActionGroup() = object: ActionGroup() {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                val actions = ArrayList<AnAction>()
                actions.addAll(listOf(
                    NewGroovyFileAction(),
                    NewKotlinFileAction(),
                    NewTextFileAction(),
                    NewFolderAction("Directory", "Directory", folder),
                    NewFileFromTemplateAction(mainScript, mainScript, defaultPluginScript(), inferIconFromFileType, IDEUtil.groovyFileType),
                    NewFileFromTemplateAction(testScript, testScript, defaultPluginTestScript(), inferIconFromFileType, IDEUtil.groovyFileType),
                    Separator(),
                    AddNewGroovyPluginAction(),
                    AddNewKotlinPluginAction()
                ))
                if (PluginToolWindowManager.addFromGistAction != null) {
                    actions.add(PluginToolWindowManager.addFromGistAction)
                }
                if (PluginToolWindowManager.addFromGitHubAction != null) {
                    actions.add(PluginToolWindowManager.addFromGitHubAction)
                }
                return actions.toTypedArray()
            }
        }
    }
}
