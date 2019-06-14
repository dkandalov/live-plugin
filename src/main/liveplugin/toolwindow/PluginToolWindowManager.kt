package liveplugin.toolwindow

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.DeleteProvider
import com.intellij.ide.actions.CollapseAllAction
import com.intellij.ide.actions.ExpandAllAction
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
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
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
import liveplugin.Icons.expandAllIcon
import liveplugin.Icons.helpIcon
import liveplugin.Icons.settingsIcon
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.pluginIdToPathMap
import liveplugin.findFileByUrl
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

class PluginToolWindowManager {
    fun init() {
        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(ProjectManager.TOPIC, object: ProjectManagerListener {
            override fun projectOpened(project: Project) {
                toolWindowsByProject[project] = PluginToolWindow(project)
            }

            override fun projectClosed(project: Project) {
                toolWindowsByProject.remove(project)
            }
        })
    }

    companion object {
        private val toolWindowsByProject = HashMap<Project, PluginToolWindow>()

        fun reloadPluginTreesInAllProjects() {
            toolWindowsByProject.forEach { (project, toolWindow) ->
                toolWindow.reloadPluginRoots(project)
            }
        }
    }
}

private class PluginToolWindow(val project: Project) {
    private val pluginsToolWindowId = "Plugins"
    private var fsTreeRef = Ref<FileSystemTree>()
    private lateinit var panel: SimpleToolWindowPanel

    init {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.registerToolWindow(pluginsToolWindowId, false, ToolWindowAnchor.RIGHT, project, true).also {
            it.icon = Icons.pluginToolwindowIcon
            it.contentManager.addContent(createContent(project))
        }

        Disposer.register(project, Disposable {
            toolWindowManager.unregisterToolWindow(pluginsToolWindowId)
        })
    }

    private fun createContent(project: Project): Content {
        val fsTree = createFsTree(project)
        fsTreeRef = Ref.create(fsTree)
        fsTree.installPopupMenu()

        panel = MySimpleToolWindowPanel(true, fsTreeRef).also {
            it.add(ScrollPaneFactory.createScrollPane(fsTree.tree))
            it.toolbar = createToolBar()
        }
        return ContentFactory.SERVICE.getInstance().createContent(panel, "", false)
    }

    fun reloadPluginRoots(project: Project) {
        // The only reason to create new instance of tree here is that
        // I couldn't find a way to force tree to update it's roots.
        val fsTree = createFsTree(project)
        fsTreeRef.set(fsTree)
        fsTree.installPopupMenu()

        panel.remove(0)
        panel.add(ScrollPaneFactory.createScrollPane(fsTreeRef.get().tree), 0)

        panel.revalidate()
        panel.repaint()
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
            it.add(ExpandAllAction().withIcon(expandAllIcon))
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
            override fun disableIfNoVisibleChildren(): Boolean {
                // without this IntelliJ calls update() on first action in the group
                // even if the action group is collapsed
                return false
            }
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

            // must be installed before adding tree to FileSystemTreeImpl
            EditSourceOnDoubleClickHandler.install(myTree)

            val result = object: FileSystemTreeImpl(project, createFileChooserDescriptor(), myTree, null, null, null) {
                override fun createTreeBuilder(
                    tree: JTree,
                    treeModel: DefaultTreeModel,
                    treeStructure: AbstractTreeStructure,
                    comparator: Comparator<NodeDescriptor<*>>,
                    descriptor: FileChooserDescriptor,
                    onInitialized: Runnable?
                ): AbstractTreeBuilder {
                    return object: FileTreeBuilder(tree, treeModel, treeStructure, comparator, descriptor, onInitialized) {
                        override fun isAutoExpandNode(nodeDescriptor: NodeDescriptor<*>): Boolean {
                            return nodeDescriptor.element is RootFileElement
                        }
                    }
                }
            }

            // must be installed after adding tree to FileSystemTreeImpl
            EditSourceOnEnterKeyHandler.install(myTree)

            return result
        }

        private fun createFileChooserDescriptor(): FileChooserDescriptor {
            val descriptor = object: FileChooserDescriptor(true, true, true, false, true, true) {
                override fun getIcon(file: VirtualFile): Icon {
                    val isPlugin = pluginIdToPathMap().values.any { file.path.toLowerCase() == it.toLowerCase() }
                    return if (isPlugin) Icons.pluginIcon else super.getIcon(file)
                }
                override fun getName(virtualFile: VirtualFile) = virtualFile.name
                override fun getComment(virtualFile: VirtualFile?) = ""
            }.also {
                it.withShowFileSystemRoots(false)
                it.withTreeRootVisible(false)
            }

            val pluginPaths = pluginIdToPathMap().values
            val virtualFiles = pluginPaths.mapNotNull { it.findFileByUrl() }
            addRoots(descriptor, virtualFiles)

            return descriptor
        }

        private fun addRoots(descriptor: FileChooserDescriptor, virtualFiles: List<VirtualFile>) {
            // Adding file parent is a hack to suppress size == 1 checks in com.intellij.openapi.fileChooser.ex.RootFileElement.
            // Otherwise, if there is only one plugin, tree will show files in plugin directory instead of plugin folder.
            // (Note that this code is also used by "Copy from Path" action.)
            if (virtualFiles.size == 1) {
                val parent = virtualFiles[0].parent
                descriptor.roots = if (parent != null) listOf(parent) else virtualFiles
            } else {
                descriptor.roots = virtualFiles
            }
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
            PlatformDataKeys.NAVIGATABLE_ARRAY.name       -> // need this to be able to open files in toolwindow on double-click/enter
                TreeUtil.collectSelectedObjectsOfType(this, FileNodeDescriptor::class.java)
                    .map { OpenFileDescriptor(project, it.element.file) }
                    .toTypedArray()
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
