package liveplugin.toolwindow

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.DeleteProvider
import com.intellij.ide.actions.CollapseAllAction
import com.intellij.ide.actions.ExpandAllAction
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.util.treeView.AbstractTreeBuilder
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
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
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
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
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.livePluginsPath
import liveplugin.LivePluginAppComponent.Companion.pluginIdToPathMap
import liveplugin.pluginrunner.RunPluginAction
import liveplugin.pluginrunner.RunPluginTestsAction
import liveplugin.toolwindow.addplugin.*
import liveplugin.toolwindow.popup.NewElementPopupAction
import liveplugin.toolwindow.settingsmenu.AddLivePluginAndIdeJarsAsDependencies
import liveplugin.toolwindow.settingsmenu.RunAllPluginsOnIDEStartAction
import org.jetbrains.annotations.NonNls
import java.awt.GridLayout
import java.io.File
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

class PluginToolWindowManager {

    fun init() {
        // TODO disable workaround after bumping plugin compatibility > 171
        val usePMListenerWorkaround = true
        if (usePMListenerWorkaround) {
            // Use deprecated API so that plugin is compatible with older IDE versions (in particular versions <= 171, see https://github.com/dkandalov/live-plugin/issues/77)
            @Suppress("DEPRECATION")
            ProjectManager.getInstance().addProjectManagerListener(object : ProjectManagerListener {
                override fun projectOpened(project: Project) {
                    val pluginToolWindow = PluginToolWindow()
                    pluginToolWindow.registerWindowFor(project)
                    putToolWindow(pluginToolWindow, project)
                }
                override fun projectClosed(project: Project) {
                    val pluginToolWindow = removeToolWindow(project)
                    pluginToolWindow?.unregisterWindowFrom(project)
                }
            })
        } else {
            val connection = ApplicationManager.getApplication().messageBus.connect()
            connection.subscribe(ProjectManager.TOPIC, object: ProjectManagerListener {
                override fun projectOpened(project: Project) {
                    val pluginToolWindow = PluginToolWindow()
                    pluginToolWindow.registerWindowFor(project)
                    putToolWindow(pluginToolWindow, project)
                }
                override fun projectClosed(project: Project) {
                    val pluginToolWindow = removeToolWindow(project)
                    pluginToolWindow?.unregisterWindowFrom(project)
                }
                // Keep explicit overrides for compatibility with older IDE versions.
                @Suppress("OverridingDeprecatedMember")
                override fun canCloseProject(project: Project) = true
                override fun projectClosing(project: Project) {}
            })
        }
    }

    class PluginToolWindow {
        private var myFsTreeRef = Ref<FileSystemTree>()
        private var panel: SimpleToolWindowPanel? = null
        private var toolWindow: ToolWindow? = null

        fun registerWindowFor(project: Project) {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            toolWindow = toolWindowManager.registerToolWindow(pluginsToolWindowId, false, ToolWindowAnchor.RIGHT, project, true)
            toolWindow!!.icon = Icons.pluginToolwindowIcon

            toolWindow!!.contentManager.addContent(createContent(project))
        }

        fun unregisterWindowFrom(project: Project?) {
            ToolWindowManager.getInstance(project!!).unregisterToolWindow(pluginsToolWindowId)
        }

        private fun createContent(project: Project): Content {
            val fsTree = createFsTree(project)
            myFsTreeRef = Ref.create(fsTree)

            installPopupMenuInto(fsTree)

            val scrollPane = ScrollPaneFactory.createScrollPane(fsTree.tree)
            panel = MySimpleToolWindowPanel(true, myFsTreeRef)
            panel!!.add(scrollPane)
            panel!!.setToolbar(createToolBar())
            return ContentFactory.SERVICE.getInstance().createContent(panel, "", false)
        }

        fun reloadPluginRoots(project: Project) {
            // the only reason to create new instance of tree here is that
            // I couldn't find a way to force tree to update it's roots
            val fsTree = createFsTree(project)
            myFsTreeRef.set(fsTree)

            installPopupMenuInto(fsTree)

            val scrollPane = ScrollPaneFactory.createScrollPane(myFsTreeRef.get().tree)
            panel!!.remove(0)
            panel!!.add(scrollPane, 0)
        }

        private fun createToolBar(): JComponent {
            val actionGroup = DefaultActionGroup()
            actionGroup.add(withIcon(Icons.addPluginIcon, createAddPluginsGroup()))
            actionGroup.add(DeletePluginAction())
            actionGroup.add(RunPluginAction())
            actionGroup.add(RunPluginTestsAction())
            actionGroup.addSeparator()
            actionGroup.add(RefreshPluginsPanelAction())
            actionGroup.add(withIcon(Icons.expandAllIcon, ExpandAllAction()))
            actionGroup.add(withIcon(Icons.collapseAllIcon, CollapseAllAction()))
            actionGroup.addSeparator()
            actionGroup.add(withIcon(Icons.settingsIcon, createSettingsGroup()))
            actionGroup.add(withIcon(Icons.helpIcon, ShowHelpAction()))

            // this is a "hack" to force drop-down box appear below button
            // (see com.intellij.openapi.actionSystem.ActionPlaces#isToolbarPlace implementation for details)
            val place = ActionPlaces.EDITOR_TOOLBAR

            val toolBarPanel = JPanel(GridLayout())
            toolBarPanel.add(ActionManager.getInstance().createActionToolbar(place, actionGroup, true).component)
            return toolBarPanel
        }

        companion object {

            private fun installPopupMenuInto(fsTree: FileSystemTree) {
                val action = NewElementPopupAction()
                action.registerCustomShortcutSet(CustomShortcutSet(*shortcutsOf("NewElement")), fsTree.tree)

                CustomizationUtil.installPopupHandler(fsTree.tree, "LivePlugin.Popup", ActionPlaces.UNKNOWN)
            }

            private fun shortcutsOf(actionId: String): Array<Shortcut> {
                return KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)
            }

            private fun createFileChooserDescriptor(): FileChooserDescriptor {
                val descriptor = object: FileChooserDescriptor(true, true, true, false, true, true) {
                    override fun getIcon(file: VirtualFile): Icon {
                        val isPlugin = pluginIdToPathMap().values.any { file.path.toLowerCase() == it.toLowerCase() }
                        return if (isPlugin) Icons.pluginIcon else super.getIcon(file)
                    }
                    override fun getName(virtualFile: VirtualFile) = virtualFile.name
                    override fun getComment(virtualFile: VirtualFile?) = ""
                }
                descriptor.withShowFileSystemRoots(false)
                descriptor.withTreeRootVisible(false)

                val pluginPaths = pluginIdToPathMap().values
                val virtualFiles = pluginPaths.mapNotNull { path -> VirtualFileManager.getInstance().findFileByUrl("file://" + path) }
                addRoots(descriptor, virtualFiles)

                return descriptor
            }

            internal fun findPluginRootsFor(files: Array<VirtualFile>): Collection<VirtualFile> {
                return files.mapNotNull { pluginFolderOf(it) }.toSet()
            }

            private fun pluginFolderOf(file: VirtualFile): VirtualFile? {
                if (file.parent == null) return null

                val pluginsRoot = File(livePluginsPath)
                // comparing files because string comparison was observed not work on windows (e.g. "c:/..." and "C:/...")
                return if (!FileUtil.filesEqual(File(file.parent.path), pluginsRoot)) pluginFolderOf(file.parent) else file
            }

            private fun withIcon(icon: Icon, action: AnAction): AnAction {
                action.templatePresentation.icon = icon
                return action
            }

            private fun createFsTree(project: Project): FileSystemTree {
                val myTree = MyTree(project)

                // must be installed before adding tree to FileSystemTreeImpl
                EditSourceOnDoubleClickHandler.install(myTree)

                val result = object: FileSystemTreeImpl(project, createFileChooserDescriptor(), myTree, null, null, null) {
                    override fun createTreeBuilder(tree: JTree, treeModel: DefaultTreeModel, treeStructure: AbstractTreeStructure, comparator: Comparator<NodeDescriptor<*>>, descriptor: FileChooserDescriptor, onInitialized: Runnable?): AbstractTreeBuilder {
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

            private fun createSettingsGroup(): AnAction {
                val actionGroup = object: DefaultActionGroup("Settings", true) {
                    override fun disableIfNoVisibleChildren(): Boolean {
                        // without this IntelliJ calls update() on first action in the group
                        // even if the action group is collapsed
                        return false
                    }
                }
                actionGroup.add(RunAllPluginsOnIDEStartAction())
                actionGroup.add(AddLivePluginAndIdeJarsAsDependencies())

                return actionGroup
            }

            private fun createAddPluginsGroup(): AnAction {
                val actionGroup = DefaultActionGroup("Add Plugin", true)
                actionGroup.add(AddNewGroovyPluginAction())
                actionGroup.add(AddNewKotlinPluginAction())
                actionGroup.add(AddPluginFromGistDelegateAction())
                actionGroup.add(AddPluginFromGitHubDelegateAction())
                actionGroup.add(AddExamplePluginAction.addGroovyExamplesActionGroup)
                actionGroup.add(AddExamplePluginAction.addKotlinExamplesActionGroup)
                return actionGroup
            }
        }
    }

    private class MySimpleToolWindowPanel(vertical: Boolean, private val fileSystemTree: Ref<FileSystemTree>): SimpleToolWindowPanel(vertical) {

        /**
         * Provides context for actions in plugin tree popup popup menu.
         * Without it the actions will be disabled or won't work.
         *
         * Used by
         * [com.intellij.openapi.fileChooser.actions.NewFileAction],
         * [com.intellij.openapi.fileChooser.actions.NewFolderAction],
         * [com.intellij.openapi.fileChooser.actions.FileDeleteAction]
         */
        override fun getData(@NonNls dataId: String): Any? {
            // this is used by create directory/file to get context in which they're executed
            // (without this they would be disabled or won't work)
            if (dataId == FileSystemTree.DATA_KEY.name) return fileSystemTree.get()
            if (dataId == FileChooserKeys.NEW_FILE_TYPE.name) return IdeUtil.groovyFileType
            if (dataId == FileChooserKeys.DELETE_ACTION_AVAILABLE.name) return true
            if (dataId == PlatformDataKeys.VIRTUAL_FILE_ARRAY.name) return fileSystemTree.get().selectedFiles
            return if (dataId == PlatformDataKeys.TREE_EXPANDER.name) DefaultTreeExpander(fileSystemTree.get().tree) else super.getData(dataId)
        }
    }

    private class MyTree constructor(private val project: Project): Tree(), DataProvider {
        private val deleteProvider = FileDeleteProviderWithRefresh()

        init {
            emptyText.text = "No plugins to show"
            isRootVisible = false
        }

        override fun getData(@NonNls dataId: String): Any? {
            return when {
                PlatformDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) -> // need this to be able to open files in toolwindow on double-click/enter
                    TreeUtil.collectSelectedObjectsOfType(this, FileNodeDescriptor::class.java)
                        .map { OpenFileDescriptor(project, it.element.file) }
                        .toTypedArray()
                PlatformDataKeys.DELETE_ELEMENT_PROVIDER.`is`(dataId) -> deleteProvider
                else -> null
            }
        }
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

    companion object {
        private val pluginsToolWindowId = "Plugins"
        private val toolWindowsByProject = HashMap<Project, PluginToolWindow>()

        internal fun reloadPluginTreesInAllProjects() {
            for ((project, toolWindow) in toolWindowsByProject) {
                toolWindow.reloadPluginRoots(project)
            }
        }

        private fun putToolWindow(pluginToolWindow: PluginToolWindow, project: Project) {
            toolWindowsByProject.put(project, pluginToolWindow)
        }

        private fun removeToolWindow(project: Project?): PluginToolWindow? {
            return toolWindowsByProject.remove(project)
        }

        private fun addRoots(descriptor: FileChooserDescriptor, virtualFiles: List<VirtualFile>) {
            // Adding file parent is a hack to suppress size == 1 checks in com.intellij.openapi.fileChooser.ex.RootFileElement.
            // Otherwise, if there is only one plugin, tree will show files in plugin directory instead of plugin folder.
            // (Note that this code is also used by "Copy from Path" action.)
            if (virtualFiles.size == 1) {
                val parent = virtualFiles[0].parent
                if (parent != null) {
                    descriptor.setRoots(parent)
                } else {
                    descriptor.roots = virtualFiles
                }
            } else {
                descriptor.roots = virtualFiles
            }
        }
    }
}
