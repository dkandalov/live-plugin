package liveplugin.toolwindow

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.DeleteProvider
import com.intellij.ide.actions.CollapseAllAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.PopupMenuPreloader
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileDeleteAction
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider
import com.intellij.openapi.fileChooser.ex.FileChooserKeys
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.fileChooser.tree.FileNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.tree.TreeUtil
import liveplugin.common.Icons
import liveplugin.common.Icons.addPluginIcon
import liveplugin.common.Icons.collapseAllIcon
import liveplugin.common.Icons.helpIcon
import liveplugin.common.Icons.settingsIcon
import liveplugin.common.Icons.sharePluginIcon
import liveplugin.common.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.isInvalidPluginFolder
import liveplugin.LivePluginPaths
import liveplugin.pluginrunner.RunPluginAction
import liveplugin.pluginrunner.RunPluginTestsAction
import liveplugin.pluginrunner.UnloadPluginAction
import liveplugin.toolwindow.addplugin.*
import liveplugin.toolwindow.popup.*
import liveplugin.toolwindow.popup.NewElementPopupAction.Companion.livePluginNewElementPopup
import liveplugin.toolwindow.settingsmenu.AddLivePluginAndIdeJarsAsDependencies
import liveplugin.toolwindow.settingsmenu.RunPluginsOnIDEStartAction
import liveplugin.toolwindow.settingsmenu.RunProjectSpecificPluginsAction
import org.jetbrains.annotations.NonNls
import java.awt.GridLayout
import java.awt.event.MouseListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree

class LivePluginToolWindowFactory: ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val pluginToolWindow = PluginToolWindow(project)
        toolWindow.contentManager.addContent(pluginToolWindow.createContent())
        add(pluginToolWindow)

        Disposer.register(toolWindow.disposable) {
            remove(pluginToolWindow)
        }
    }

    companion object {
        private val toolWindows = HashSet<PluginToolWindow>()

        private fun add(pluginToolWindow: PluginToolWindow) = toolWindows.add(pluginToolWindow)

        private fun remove(pluginToolWindow: PluginToolWindow) = toolWindows.remove(pluginToolWindow)

        fun reloadPluginTreesInAllProjects() = toolWindows.forEach { it.updateTree() }
    }
}

class PluginToolWindow(project: Project) {
    private val fileSystemTree = createFileSystemTree(project)

    fun createContent(): Content {
        val panel = MySimpleToolWindowPanel(true, fileSystemTree).also {
            it.add(ScrollPaneFactory.createScrollPane(fileSystemTree.tree))
            it.toolbar = createToolBar()
        }
        return ContentFactory.SERVICE.getInstance().createContent(panel, "", false)
    }

    fun updateTree() {
        fileSystemTree.updateTree()
    }

    private fun createToolBar(): JComponent {
        fun AnAction.with(icon: Icon) = also { it.templatePresentation.icon = icon }

        val actionGroup = DefaultActionGroup().apply {
            add(DefaultActionGroup("Add Plugin", true).apply {
                add(AddNewGroovyPluginAction())
                add(AddNewKotlinPluginAction())
                add(AddPluginFromGistDelegateAction())
                add(AddPluginFromGitHubDelegateAction())
                add(AddGroovyExamplesActionGroup())
                add(AddKotlinExamplesActionGroup())
            }.with(addPluginIcon))
            add(DeletePluginAction())
            add(RunPluginAction())
            add(UnloadPluginAction())
            add(RunPluginTestsAction())
            add(DefaultActionGroup("Share Plugin", true).apply {
                add(SharePluginAsGistDelegateAction())
//                add(CreatePluginZipAction())
            }.with(sharePluginIcon))
            addSeparator()
            add(CollapseAllAction().with(collapseAllIcon))
            addSeparator()
            add(createSettingsGroup().with(settingsIcon))
            add(ShowHelpAction().with(helpIcon))
        }

        return JPanel(GridLayout()).also {
            // this is a "hack" to force drop-down box appear below button
            // (see com.intellij.openapi.actionSystem.ActionPlaces#isToolbarPlace implementation for details)
            val place = ActionPlaces.EDITOR_TOOLBAR
            val toolbar = ActionManager.getInstance().createActionToolbar(place, actionGroup, true)
            // Set target component to avoid this error:
            // 'EditorToolbar' toolbar by default uses any focused component to update its actions. Toolbar actions that need local UI context would be incorrectly disabled. Please call toolbar.setTargetComponent() explicitly. java.lang.Throwable: toolbar creation trace
            toolbar.targetComponent = it
            it.add(toolbar.component)
        }
    }

    private fun createSettingsGroup() =
        object: DefaultActionGroup("Settings", true) {
            // Without this IntelliJ calls update() on first action in the group even if the action group is collapsed
            override fun disableIfNoVisibleChildren() = false
        }.also {
            it.add(RunPluginsOnIDEStartAction())
            it.add(RunProjectSpecificPluginsAction())
            it.add(AddLivePluginAndIdeJarsAsDependencies())
        }

    private class MySimpleToolWindowPanel(vertical: Boolean, private val fileSystemTree: FileSystemTree): SimpleToolWindowPanel(vertical) {
        /**
         * Provides context for actions in plugin tree popup menu.
         * Without it the actions will be disabled or won't work.
         *
         * Implicitly used by
         * [com.intellij.openapi.fileChooser.actions.NewFileAction],
         * [com.intellij.openapi.fileChooser.actions.NewFolderAction],
         * [com.intellij.openapi.fileChooser.actions.FileDeleteAction]
         */
        override fun getData(@NonNls dataId: String): Any? =
            when (dataId) {
                FileSystemTree.DATA_KEY.name                 -> {
                    // This is used by "create directory/file" actions to get execution context
                    // (without it they will be disabled or won't work).
                    fileSystemTree
                }
                FileChooserKeys.NEW_FILE_TYPE.name           -> IdeUtil.groovyFileType
                FileChooserKeys.DELETE_ACTION_AVAILABLE.name -> true
                PlatformDataKeys.VIRTUAL_FILE_ARRAY.name     -> fileSystemTree.selectedFiles
                PlatformDataKeys.TREE_EXPANDER.name          -> DefaultTreeExpander(fileSystemTree.tree)
                else                                         -> super.getData(dataId)
            }
    }

    companion object {

        private fun createFileSystemTree(project: Project): FileSystemTree {
            val myTree = MyTree(project)
            EditSourceOnDoubleClickHandler.install(myTree) // This handler only seems to work before creating FileSystemTreeImpl.

            val fileSystemTree = FileSystemTreeImpl(project, createFileChooserDescriptor(), myTree, null, null, null)
            Disposer.register(project, fileSystemTree)
            fileSystemTree.tree.let {
                EditSourceOnEnterKeyHandler.install(it) // This handler only seems to work after creating FileSystemTreeImpl.
                it.installPopupMenu()
            }
            return fileSystemTree
        }

        private fun createFileChooserDescriptor(): FileChooserDescriptor {
            val descriptor = object: FileChooserDescriptor(true, true, true, false, true, true) {
                override fun getIcon(file: VirtualFile) = if (!file.isDirectory || isInvalidPluginFolder(file)) super.getIcon(file) else Icons.pluginIcon
                override fun getName(virtualFile: VirtualFile) = virtualFile.name
                override fun getComment(virtualFile: VirtualFile?) = ""
            }.also {
                it.withShowFileSystemRoots(false)
                it.withTreeRootVisible(false)
            }

            runWriteAction {
                descriptor.setRoots(VfsUtil.createDirectoryIfMissing(LivePluginPaths.livePluginsPath.value))
            }

            return descriptor
        }

        private fun JTree.installPopupMenu() {
            fun shortcutsOf(actionId: String) = KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)

            NewElementPopupAction().registerCustomShortcutSet(CustomShortcutSet(*shortcutsOf("NewElement")), this)

            val popupActionGroup = DefaultActionGroup(
                livePluginNewElementPopup,
                RunPluginAction(),
                UnloadPluginAction(),
                RenameFileAction().also { it.registerCustomShortcutSet(CustomShortcutSet(*shortcutsOf("RenameElement")), this) },
                FileDeleteAction().also { it.registerCustomShortcutSet(CustomShortcutSet(*shortcutsOf("SafeDelete")), this) },
            ).also { it.isPopup = true }

            installPopupHandler(this, popupActionGroup)
        }

        private fun installPopupHandler(component: JComponent, actionGroup: ActionGroup): MouseListener {
            val place = IdeUtil.livePluginActionPlace
            val popupHandler = PopupHandler.installPopupMenu(component, actionGroup, place)
            PopupMenuPreloader.install(component, place, popupHandler) { actionGroup }
            return popupHandler
        }

        private class MyTree(private val project: Project): Tree(), DataProvider {
            init {
                emptyText.text = "No plugins to show"
                isRootVisible = false
            }

            override fun getData(@NonNls dataId: String): Any? =
                when (dataId) {
                    // NAVIGATABLE_ARRAY is used to open files in tool window on double-click/enter.
                    PlatformDataKeys.NAVIGATABLE_ARRAY.name       -> {
                        val files1 = TreeUtil.collectSelectedObjectsOfType(this, FileNodeDescriptor::class.java)
                            .map { it.element.file } // This worked until 2020.3. Keeping it here for backward compatibility.
                        val files2 = TreeUtil.collectSelectedObjectsOfType(this, FileNode::class.java).map { it.file }
                        (files1 + files2)
                            .filterNot { it.isDirectory } // Exclude directories so that they're not navigatable from the tree and EditSourceOnEnterKeyHandler expands/collapses tree nodes.
                            .map { file -> OpenFileDescriptor(project, file) }
                            .toTypedArray()
                    }
                    PlatformDataKeys.DELETE_ELEMENT_PROVIDER.name -> FileDeleteProviderWithRefresh
                    else                                          -> null
                }

            private object FileDeleteProviderWithRefresh: DeleteProvider {
                private val fileDeleteProvider = VirtualFileDeleteProvider()

                override fun deleteElement(dataContext: DataContext) {
                    fileDeleteProvider.deleteElement(dataContext)
                    RefreshPluginsPanelAction.refreshPluginTree()
                }

                override fun canDeleteElement(dataContext: DataContext) =
                    fileDeleteProvider.canDeleteElement(dataContext)
            }
        }
    }
}
