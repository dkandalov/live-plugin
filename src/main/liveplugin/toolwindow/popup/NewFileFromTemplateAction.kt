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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileChooserAction
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.ui.Messages
import com.intellij.ui.UIBundle
import liveplugin.LivePluginAppComponent
import javax.swing.Icon

open class NewFileFromTemplateAction(text: String, private val newFileName: String, private val fileContent: String, icon: Icon?, private val fileType: FileType): FileChooserAction(text, text, icon) {

    override fun update(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        val parentFile = fileSystemTree.newFileParent

        val isAtPluginRoot = LivePluginAppComponent.pluginIdToPathMap().containsValue(parentFile?.canonicalPath)
        val fileDoesNotExist = if (isAtPluginRoot) parentFile?.findChild(newFileName) == null else false

        event.presentation.apply {
            isEnabled = isAtPluginRoot && fileDoesNotExist
            isVisible = isAtPluginRoot && fileDoesNotExist
            icon = fileType.icon
        }
    }

    override fun actionPerformed(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        val parentFile = fileSystemTree.newFileParent

        val failReason = (fileSystemTree as FileSystemTreeImpl).createNewFile(parentFile, newFileName, fileType, fileContent)
        if (failReason != null) {
            val message = UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName)
            val title = UIBundle.message("error.dialog.title")
            Messages.showMessageDialog(message, title, Messages.getErrorIcon())
        }
    }
}