package liveplugin.implementation

import com.intellij.ide.BrowserUtil
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.actions.CollapseAllAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.PopupMenuPreloader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileDeleteAction
import com.intellij.openapi.fileChooser.ex.FileChooserKeys
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
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.actions.*
import liveplugin.implementation.actions.addplugin.AddGroovyExamplesActionGroup
import liveplugin.implementation.actions.addplugin.AddKotlinExamplesActionGroup
import liveplugin.implementation.actions.addplugin.AddNewGroovyPluginAction
import liveplugin.implementation.actions.addplugin.AddNewKotlinPluginAction
import liveplugin.implementation.actions.addplugin.git.AddPluginFromGistDelegateAction
import liveplugin.implementation.actions.addplugin.git.AddPluginFromGitHubDelegateAction
import liveplugin.implementation.actions.addplugin.git.SharePluginAsGistDelegateAction
import liveplugin.implementation.actions.settings.AddLivePluginAndIdeJarsAsDependencies
import liveplugin.implementation.actions.settings.RunPluginsOnIDEStartAction
import liveplugin.implementation.actions.settings.RunProjectSpecificPluginsAction
import liveplugin.implementation.actions.toolwindow.NewElementPopupAction
import liveplugin.implementation.actions.toolwindow.NewElementPopupAction.Companion.livePluginNewElementPopup
import liveplugin.implementation.actions.toolwindow.RenameFileAction
import liveplugin.implementation.common.Icons.addPluginIcon
import liveplugin.implementation.common.Icons.collapseAllIcon
import liveplugin.implementation.common.Icons.helpIcon
import liveplugin.implementation.common.Icons.pluginIcon
import liveplugin.implementation.common.Icons.settingsIcon
import liveplugin.implementation.common.Icons.sharePluginIcon
import liveplugin.implementation.common.IdeUtil.groovyFileType
import liveplugin.implementation.common.IdeUtil.livePluginActionPlace
import liveplugin.implementation.common.toFilePath
import org.jetbrains.annotations.NonNls
import java.awt.GridLayout
import java.awt.event.MouseListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree

class LivePluginToolWindowFactory: ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(PluginToolWindow(project).createContent())
    }
}

private class PluginToolWindow(project: Project) {
    private val fileSystemTree = createFileSystemTree(project)

    fun createContent(): Content {
        val panel = MySimpleToolWindowPanel(true, fileSystemTree).also {
            it.add(ScrollPaneFactory.createScrollPane(fileSystemTree.tree))
            it.toolbar = createToolBar()
        }
        return ApplicationManager.getApplication().getService(ContentFactory::class.java)
            .createContent(panel, "", false)
    }

    private fun createToolBar(): JComponent {
        fun AnAction.with(icon: Icon) = also { it.templatePresentation.icon = icon }

        val actionGroup = DefaultActionGroup().apply {
            add(DefaultActionGroup("Add Plugin", true).apply {
                add(AddNewKotlinPluginAction())
                add(AddNewGroovyPluginAction())
                add(AddPluginFromGistDelegateAction())
                add(AddPluginFromGitHubDelegateAction())
                add(AddKotlinExamplesActionGroup())
                add(AddGroovyExamplesActionGroup())
            }.with(addPluginIcon))
            add(DeletePluginAction())
            add(RunPluginAction())
            add(UnloadPluginAction())
            add(RunPluginTestsAction())
            add(DefaultActionGroup("Share Plugin", true).apply {
                add(SharePluginAsGistDelegateAction())
                add(CreateKotlinPluginZipAction())
            }.with(sharePluginIcon))
            addSeparator()
            add(CollapseAllAction().with(collapseAllIcon))
            addSeparator()
            add(DefaultActionGroup("Settings", true).apply {
                add(RunPluginsOnIDEStartAction())
                add(RunProjectSpecificPluginsAction())
                add(AddLivePluginAndIdeJarsAsDependencies())
            }.with(settingsIcon))
            add(ShowHelpAction())
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
                FileChooserKeys.NEW_FILE_TYPE.name           -> groovyFileType
                FileChooserKeys.DELETE_ACTION_AVAILABLE.name -> true
                PlatformDataKeys.VIRTUAL_FILE_ARRAY.name     -> fileSystemTree.selectedFiles
                PlatformDataKeys.TREE_EXPANDER.name          -> DefaultTreeExpander(fileSystemTree.tree)
                else                                         -> super.getData(dataId)
            }
    }

    private class ShowHelpAction: AnAction("Show Help on GitHub", "Open help page on GitHub", helpIcon), DumbAware {
        override fun actionPerformed(e: AnActionEvent) =
            BrowserUtil.browse("https://github.com/dkandalov/live-plugin#getting-started")
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
                override fun getIcon(file: VirtualFile) = if (file.toFilePath().isPluginFolder()) pluginIcon else super.getIcon(file)
                override fun getName(virtualFile: VirtualFile) = virtualFile.name
                override fun getComment(virtualFile: VirtualFile?) = ""
            }.also {
                it.withShowFileSystemRoots(false)
                it.withTreeRootVisible(false)
            }

            runWriteAction {
                descriptor.setRoots(VfsUtil.createDirectoryIfMissing(livePluginsPath.value))
            }

            return descriptor
        }

        private fun JTree.installPopupMenu() {
            fun shortcutsOf(actionId: String) = KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)

            NewElementPopupAction().registerCustomShortcutSet(CustomShortcutSet(*shortcutsOf("NewElement")), this)

            val popupActionGroup = DefaultActionGroup(
                livePluginNewElementPopup,
                RunLivePluginsGroup(),
                RenameFileAction().also { it.registerCustomShortcutSet(CustomShortcutSet(*shortcutsOf("RenameElement")), this) },
                FileDeleteAction().also { it.registerCustomShortcutSet(CustomShortcutSet(*shortcutsOf("SafeDelete")), this) },
            ).also { it.isPopup = true }

            installPopupHandler(this, popupActionGroup)
        }

        private fun installPopupHandler(component: JComponent, actionGroup: ActionGroup): MouseListener {
            val popupHandler = PopupHandler.installPopupMenu(component, actionGroup, livePluginActionPlace)
            PopupMenuPreloader.install(component, livePluginActionPlace, popupHandler) { actionGroup }
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
                        TreeUtil.collectSelectedObjectsOfType(this, FileNode::class.java)
                            .mapNotNull { node ->
                                if (node.file.isDirectory) null // Exclude directories so that they're not navigatable from the tree and EditSourceOnEnterKeyHandler expands/collapses tree nodes.
                                else OpenFileDescriptor(project, node.file)
                            }
                            .toTypedArray()
                    }
                    else                                          -> null
                }
        }
    }
}
