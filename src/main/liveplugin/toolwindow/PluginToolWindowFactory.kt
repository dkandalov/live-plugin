package liveplugin.toolwindow

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.DeleteProvider
import com.intellij.ide.actions.CollapseAllAction
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.util.treeView.AbstractTreeBuilder
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider
import com.intellij.openapi.fileChooser.ex.FileChooserKeys
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.fileChooser.ex.RootFileElement
import com.intellij.openapi.fileChooser.impl.FileTreeBuilder
import com.intellij.openapi.fileChooser.tree.FileNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.tree.TreeUtil
import liveplugin.Icons
import liveplugin.Icons.addPluginIcon
import liveplugin.Icons.collapseAllIcon
import liveplugin.Icons.helpIcon
import liveplugin.Icons.settingsIcon
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.pluginIdToPathMap
import liveplugin.LivePluginPaths
import liveplugin.pluginrunner.RunPluginAction
import liveplugin.pluginrunner.RunPluginTestsAction
import liveplugin.toolwindow.addplugin.*
import liveplugin.toolwindow.popup.NewElementPopupAction
import liveplugin.toolwindow.settingsmenu.AddLivePluginAndIdeJarsAsDependencies
import liveplugin.toolwindow.settingsmenu.RunAllPluginsOnIDEStartAction
import org.jetbrains.annotations.NonNls
import java.awt.GridLayout
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

class LivePluginToolWindowFactory: ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val pluginToolWindow = PluginToolWindow(project)
        toolWindow.contentManager.addContent(pluginToolWindow.createContent(project))
        add(pluginToolWindow)

        Disposer.register(project, Disposable {
            remove(pluginToolWindow)
        })
    }

    companion object {
        private val toolWindows = HashSet<PluginToolWindow>()

        private fun add(pluginToolWindow: PluginToolWindow) {
            toolWindows.add(pluginToolWindow)
        }

        private fun remove(pluginToolWindow: PluginToolWindow) {
            toolWindows.remove(pluginToolWindow)
        }

        fun reloadPluginTreesInAllProjects() {
            toolWindows.forEach { it.updateTree() }
        }
    }
}

class PluginToolWindow(val project: Project) {
    private var fsTreeRef = Ref<FileSystemTree>()

    fun createContent(project: Project): Content {
        val fsTree = createFsTree(project)
        fsTreeRef = Ref.create(fsTree)
        fsTree.installPopupMenu()

        val panel = MySimpleToolWindowPanel(true, fsTreeRef).also {
            it.add(ScrollPaneFactory.createScrollPane(fsTree.tree))
            it.toolbar = createToolBar()
        }
        return ContentFactory.SERVICE.getInstance().createContent(panel, "", false)
    }

    fun updateTree() {
        fsTreeRef.get().updateTree()
    }

    private fun createToolBar(): JComponent {
        fun AnAction.withIcon(icon: Icon) = apply { templatePresentation.icon = icon }

        val actionGroup = DefaultActionGroup().also {
            it.add(createAddPluginsGroup().withIcon(addPluginIcon))
            it.add(DeletePluginAction())
            it.add(RunPluginAction())
            it.add(RunPluginTestsAction())
            it.addSeparator()
            it.add(RefreshPluginsPanelAction())
            it.add(CollapseAllAction().withIcon(collapseAllIcon))
            it.addSeparator()
            it.add(createSettingsGroup().withIcon(settingsIcon))
            it.add(ShowHelpAction().withIcon(helpIcon))
        }

        return JPanel(GridLayout()).also {
            // this is a "hack" to force drop-down box appear below button
            // (see com.intellij.openapi.actionSystem.ActionPlaces#isToolbarPlace implementation for details)
            val place = ActionPlaces.EDITOR_TOOLBAR
            it.add(ActionManager.getInstance().createActionToolbar(place, actionGroup, true).component)
        }
    }

    private fun createAddPluginsGroup() =
        DefaultActionGroup("Add Plugin", true).also {
            it.add(AddNewGroovyPluginAction())
            it.add(AddNewKotlinPluginAction())
            it.add(AddPluginFromGistDelegateAction())
            it.add(AddPluginFromGitHubDelegateAction())
            it.add(AddExamplePluginAction.addGroovyExamplesActionGroup)
            it.add(AddExamplePluginAction.addKotlinExamplesActionGroup)
        }

    private fun createSettingsGroup() =
        object: DefaultActionGroup("Settings", true) {
            // Without this IntelliJ calls update() on first action in the group even if the action group is collapsed
            override fun disableIfNoVisibleChildren() = false
        }.also {
            it.add(RunAllPluginsOnIDEStartAction())
            it.add(AddLivePluginAndIdeJarsAsDependencies())
        }


    private class MySimpleToolWindowPanel(vertical: Boolean, private val fileSystemTree: Ref<FileSystemTree>): SimpleToolWindowPanel(vertical) {
        /**
         * Provides context for actions in plugin tree popup popup menu.
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
                    fileSystemTree.get()
                }
                FileChooserKeys.NEW_FILE_TYPE.name           -> IdeUtil.groovyFileType
                FileChooserKeys.DELETE_ACTION_AVAILABLE.name -> true
                PlatformDataKeys.VIRTUAL_FILE_ARRAY.name     -> fileSystemTree.get().selectedFiles
                PlatformDataKeys.TREE_EXPANDER.name          -> DefaultTreeExpander(fileSystemTree.get().tree)
                else                                         -> super.getData(dataId)
            }
    }

    companion object {

        private fun FileSystemTree.installPopupMenu() {
            fun shortcutsOf(actionId: String) = KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)

            val action = NewElementPopupAction()
            action.registerCustomShortcutSet(CustomShortcutSet(*shortcutsOf("NewElement")), tree)

            CustomizationUtil.installPopupHandler(tree, "LivePlugin.Popup", ActionPlaces.UNKNOWN)
        }

        private fun createFsTree(project: Project): FileSystemTree {
            val myTree = MyTree(project)
            EditSourceOnDoubleClickHandler.install(myTree)
            EditSourceOnEnterKeyHandler.install(myTree)

            return object: FileSystemTreeImpl(project, createFileChooserDescriptor(), myTree, null, null, null) {
                override fun createTreeBuilder(
                    tree: JTree,
                    treeModel: DefaultTreeModel,
                    treeStructure: AbstractTreeStructure,
                    comparator: Comparator<NodeDescriptor<*>>,
                    descriptor: FileChooserDescriptor,
                    onInitialized: Runnable?
                ): AbstractTreeBuilder {
                    return object: FileTreeBuilder(tree, treeModel, treeStructure, comparator, descriptor, onInitialized) {
                        override fun isAutoExpandNode(nodeDescriptor: NodeDescriptor<*>) = nodeDescriptor.element is RootFileElement
                    }
                }
            }
        }

        private fun createFileChooserDescriptor(): FileChooserDescriptor {
            val descriptor = object: FileChooserDescriptor(true, true, true, false, true, true) {
                override fun getIcon(file: VirtualFile): Icon {
                    val isPlugin = pluginIdToPathMap().values.any { file.path.equals(it, ignoreCase = true) }
                    return if (isPlugin) Icons.pluginIcon else super.getIcon(file)
                }

                override fun getName(virtualFile: VirtualFile) = virtualFile.name
                override fun getComment(virtualFile: VirtualFile?) = ""
            }.also {
                it.withShowFileSystemRoots(false)
                it.withTreeRootVisible(false)
            }

            ApplicationManager.getApplication().runWriteAction {
                descriptor.setRoots(VfsUtil.createDirectoryIfMissing(LivePluginPaths.livePluginsPath))
            }

            return descriptor
        }
    }
}

private class MyTree constructor(private val project: Project): Tree(), DataProvider {
    private val deleteProvider = FileDeleteProviderWithRefresh()

    init {
        emptyText.text = "No plugins to show"
        isRootVisible = false
    }

    override fun getData(@NonNls dataId: String): Any? =
        when (dataId) {
            // NAVIGATABLE_ARRAY is used to open files in toolwindow on double-click/enter.
            PlatformDataKeys.NAVIGATABLE_ARRAY.name       -> (
                TreeUtil.collectSelectedObjectsOfType(this, FileNodeDescriptor::class.java).map { it.element.file } + // This worked until 2020.3. Keeping it here for backward compatibility.
                TreeUtil.collectSelectedObjectsOfType(this, FileNode::class.java).map { it.file }
            ).map { file -> OpenFileDescriptor(project, file) }.toTypedArray()
            PlatformDataKeys.DELETE_ELEMENT_PROVIDER.name -> deleteProvider
            else                                          -> null
        }

    private class FileDeleteProviderWithRefresh: DeleteProvider {
        private val fileDeleteProvider = VirtualFileDeleteProvider()

        override fun deleteElement(dataContext: DataContext) {
            fileDeleteProvider.deleteElement(dataContext)
            RefreshPluginsPanelAction.refreshPluginTree()
        }

        override fun canDeleteElement(dataContext: DataContext): Boolean {
            return fileDeleteProvider.canDeleteElement(dataContext)
        }
    }
}
