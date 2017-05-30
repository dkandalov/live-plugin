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
package liveplugin.toolwindow;

import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.CollapseAllAction;
import com.intellij.ide.actions.ExpandAllAction;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileChooser.ex.RootFileElement;
import com.intellij.openapi.fileChooser.impl.FileTreeBuilder;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import liveplugin.IDEUtil;
import liveplugin.Icons;
import liveplugin.LivePluginAppComponent;
import liveplugin.pluginrunner.RunPluginAction;
import liveplugin.pluginrunner.TestPluginAction;
import liveplugin.toolwindow.addplugin.AddExamplePluginAction;
import liveplugin.toolwindow.addplugin.AddNewPluginAction;
import liveplugin.toolwindow.addplugin.AddPluginFromPathAction;
import liveplugin.toolwindow.settingsmenu.AddIDEAJarsAsDependencies;
import liveplugin.toolwindow.settingsmenu.AddPluginJarAsDependency;
import liveplugin.toolwindow.settingsmenu.RunAllPluginsOnIDEStartAction;
import liveplugin.toolwindow.settingsmenu.languages.AddClojureLibsAsDependency;
import liveplugin.toolwindow.settingsmenu.languages.AddScalaLibsAsDependency;
import liveplugin.toolwindow.settingsmenu.languages.DownloadClojureLibs;
import liveplugin.toolwindow.settingsmenu.languages.DownloadScalaLibs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.util.Condition.NOT_NULL;
import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Arrays.asList;
import static liveplugin.LivePluginAppComponent.PLUGIN_EXAMPLES_PATH;

public class PluginToolWindowManager {

	private static final String PLUGINS_TOOL_WINDOW_ID = "Plugins";

	private static final Map<Project, PluginToolWindow> toolWindowsByProject = new HashMap<>();

	public static AnAction addFromGistAction; // TODO refactor this!!!
	public static AnAction addFromGitHubAction;

	@Nullable public static PluginToolWindow getToolWindowFor(@Nullable Project project) {
		if (project == null) return null;
		return toolWindowsByProject.get(project);
	}

	static void reloadPluginTreesInAllProjects() {
		for (Map.Entry<Project, PluginToolWindow> entry : toolWindowsByProject.entrySet()) {
			Project project = entry.getKey();
			PluginToolWindow toolWindow = entry.getValue();

			toolWindow.reloadPluginRoots(project);
		}
	}

	private static void putToolWindow(PluginToolWindow pluginToolWindow, Project project) {
		toolWindowsByProject.put(project, pluginToolWindow);
	}

	private static PluginToolWindow removeToolWindow(Project project) {
		return toolWindowsByProject.remove(project);
	}

	public static void addRoots(FileChooserDescriptor descriptor, List<VirtualFile> virtualFiles) {
		virtualFiles = new ArrayList<>(filter(virtualFiles, NOT_NULL));

		// Adding file parent is a hack to suppress size == 1 checks in com.intellij.openapi.fileChooser.ex.RootFileElement.
		// Otherwise, if there is only one plugin, tree will show files in plugin directory instead of plugin folder.
		// (Note that this code is also used by "Copy from Path" action.)
		if (virtualFiles.size() == 1) {
			VirtualFile parent = virtualFiles.get(0).getParent();
			if (parent != null) {
				descriptor.setRoots(parent);
			} else {
				descriptor.setRoots(virtualFiles);
			}
		} else {
			descriptor.setRoots(virtualFiles);
		}
	}

	public void init() {
		MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
		connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
			@Override public void projectOpened(Project project) {
				if (project == null) return;
				ApplicationManager.getApplication().invokeLater(() -> {
					PluginToolWindow pluginToolWindow = new PluginToolWindow();
					pluginToolWindow.registerWindowFor(project);

					putToolWindow(pluginToolWindow, project);
				});
			}

			@Override public void projectClosed(Project project) {
				if (project == null) return;
				ApplicationManager.getApplication().invokeLater(() -> {
					PluginToolWindow pluginToolWindow = removeToolWindow(project);
					if (pluginToolWindow != null) pluginToolWindow.unregisterWindowFrom(project);
				});
			}
		});
	}

	public static class PluginToolWindow {
		private Ref<FileSystemTree> myFsTreeRef = new Ref<>();
		private SimpleToolWindowPanel panel;
		private ToolWindow toolWindow;

		private static void installPopupMenuInto(FileSystemTree fsTree) {
			AnAction action = new NewElementPopupAction();
			action.registerCustomShortcutSet(new CustomShortcutSet(shortcutsOf("NewElement")), fsTree.getTree());

			CustomizationUtil.installPopupHandler(fsTree.getTree(), "LivePlugin.Popup", ActionPlaces.UNKNOWN);
		}

		private static Shortcut[] shortcutsOf(String actionId) {
			return KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
		}

		private static FileChooserDescriptor createFileChooserDescriptor() {
			FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, true) {
				@Override public Icon getIcon(VirtualFile file) {
                    for (String path : LivePluginAppComponent.pluginIdToPathMap().values()) {
                        if (file.getPath().toLowerCase().equals(path.toLowerCase())) return Icons.PLUGIN_ICON;
                    }
					return super.getIcon(file);
				}

				@Override public String getName(VirtualFile virtualFile) {
					return virtualFile.getName();
				}

				@NotNull @Override public String getComment(VirtualFile virtualFile) {
					return "";
				}
			};
			descriptor.withShowFileSystemRoots(false);
			descriptor.withTreeRootVisible(false);

			Collection<String> pluginPaths = LivePluginAppComponent.pluginIdToPathMap().values();
			List<VirtualFile> virtualFiles = map(pluginPaths, path -> VirtualFileManager.getInstance().findFileByUrl("file://" + path));
			addRoots(descriptor, virtualFiles);

			return descriptor;
		}

		static Collection<VirtualFile> findPluginRootsFor(VirtualFile[] files) {
			Set<VirtualFile> selectedPluginRoots = new HashSet<>();
			for (VirtualFile file : files) {
				VirtualFile root = pluginFolderOf(file);
				if (root != null) selectedPluginRoots.add(root);
			}
			return selectedPluginRoots;
		}

		private static VirtualFile pluginFolderOf(VirtualFile file) {
			if (file.getParent() == null) return null;

			File pluginsRoot = new File(LivePluginAppComponent.pluginsRootPath());
			// comparing files because string comparison was observed not work on windows (e.g. "c:/..." and "C:/...")
			if (!FileUtil.filesEqual(new File(file.getParent().getPath()), pluginsRoot))
				return pluginFolderOf(file.getParent());
			else
				return file;
		}

		private static AnAction withIcon(Icon icon, AnAction action) {
			action.getTemplatePresentation().setIcon(icon);
			return action;
		}

		public void registerWindowFor(Project project) {
			ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
			toolWindow = toolWindowManager.registerToolWindow(PLUGINS_TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT, project, true);
			toolWindow.setIcon(Icons.PLUGIN_TOOLWINDOW_ICON);

			toolWindow.getContentManager().addContent(createContent(project));
		}

		public void unregisterWindowFrom(Project project) {
			ToolWindowManager.getInstance(project).unregisterToolWindow(PLUGINS_TOOL_WINDOW_ID);
		}

		private Content createContent(Project project) {
			FileSystemTree fsTree = createFsTree(project);
			myFsTreeRef = Ref.create(fsTree);

			installPopupMenuInto(fsTree);

			JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(fsTree.getTree());
			panel = new MySimpleToolWindowPanel(true, myFsTreeRef);
			panel.add(scrollPane);
			panel.setToolbar(createToolBar());
			return ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
		}

		public void reloadPluginRoots(Project project) {
			// the only reason to create new instance of tree here is that
			// I couldn't find a way to force tree to update it's roots
			FileSystemTree fsTree = createFsTree(project);
			myFsTreeRef.set(fsTree);

			installPopupMenuInto(fsTree);

			JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFsTreeRef.get().getTree());
			panel.remove(0);
			panel.add(scrollPane, 0);
		}

		private static FileSystemTree createFsTree(Project project) {
			MyTree myTree = new MyTree(project);

			// must be installed before adding tree to FileSystemTreeImpl
			EditSourceOnDoubleClickHandler.install(myTree);

			FileSystemTree result = new FileSystemTreeImpl(project, createFileChooserDescriptor(), myTree, null, null, null) {
				@Override
				protected AbstractTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure, Comparator<NodeDescriptor> comparator, FileChooserDescriptor descriptor, @Nullable Runnable onInitialized) {
					return new FileTreeBuilder(tree, treeModel, treeStructure, comparator, descriptor, onInitialized) {
						@Override protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
							return nodeDescriptor.getElement() instanceof RootFileElement;
						}
					};
				}
			};

			// must be installed after adding tree to FileSystemTreeImpl
			EditSourceOnEnterKeyHandler.install(myTree);

			return result;
		}

		private JComponent createToolBar() {
			DefaultActionGroup actionGroup = new DefaultActionGroup();
			actionGroup.add(withIcon(Icons.ADD_PLUGIN_ICON, createAddPluginsGroup()));
			actionGroup.add(new DeletePluginAction());
			actionGroup.add(new RunPluginAction());
			actionGroup.add(new TestPluginAction());
			actionGroup.addSeparator();
			actionGroup.add(new RefreshPluginsPanelAction());
			actionGroup.add(withIcon(Icons.EXPAND_ALL_ICON, new ExpandAllAction()));
			actionGroup.add(withIcon(Icons.COLLAPSE_ALL_ICON, new CollapseAllAction()));
			actionGroup.addSeparator();
			actionGroup.add(withIcon(Icons.SETTINGS_ICON, createSettingsGroup()));
            actionGroup.add(withIcon(Icons.HELP_ICON, new ShowHelpAction()));

			// this is a "hack" to force drop-down box appear below button
			// (see com.intellij.openapi.actionSystem.ActionPlaces#isToolbarPlace implementation for details)
			String place = ActionPlaces.EDITOR_TOOLBAR;

			JPanel toolBarPanel = new JPanel(new GridLayout());
			toolBarPanel.add(ActionManager.getInstance().createActionToolbar(place, actionGroup, true).getComponent());
			return toolBarPanel;
		}

        private static AnAction createSettingsGroup() {
			DefaultActionGroup actionGroup = new DefaultActionGroup("Settings", true) {
				@Override public boolean disableIfNoVisibleChildren() {
					// without this IntelliJ calls update() on first action in the group
					// even if the action group is collapsed
					return false;
				}
			};
			actionGroup.add(new AddPluginJarAsDependency());
			actionGroup.add(new AddIDEAJarsAsDependencies());
			actionGroup.add(new Separator());
			actionGroup.add(new RunAllPluginsOnIDEStartAction());
			actionGroup.add(new Separator());
            actionGroup.add(new AddScalaLibsAsDependency());
            actionGroup.add(new AddClojureLibsAsDependency());
            actionGroup.add(new DownloadScalaLibs());
            actionGroup.add(new DownloadClojureLibs());

			return actionGroup;
		}

		private static AnAction createAddPluginsGroup() {
			DefaultActionGroup actionGroup = new DefaultActionGroup("Add Plugin", true);
			actionGroup.add(new AddNewPluginAction());
			actionGroup.add(new AddPluginFromPathAction());
			if (addFromGistAction != null) {
				actionGroup.add(addFromGistAction);
			}
			if (addFromGitHubAction != null) {
				actionGroup.add(addFromGitHubAction);
			}
			actionGroup.add(createAddPluginsExamplesGroup());
			return actionGroup;
		}

		private static AnAction createAddPluginsExamplesGroup() {
			final DefaultActionGroup actionGroup = new DefaultActionGroup("Examples", true);
            actionGroup.add(new DumbAwareAction("Add All") {
                @Override public void actionPerformed(@NotNull AnActionEvent e) {
                    AnAction[] actions = actionGroup.getChildActionsOrStubs();
                    for (AnAction action : actions) {
                        if (action instanceof AddExamplePluginAction) {
                            IDEUtil.runAction(action, "ADD_ALL_EXAMPLES");
                        }
                    }
                }
            });
            actionGroup.addSeparator();
            actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "helloWorld/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "registerAction/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "popupMenu/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "popupSearch/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "toolWindow/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "toolbarWidget/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "textEditor/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "transformSelectedText/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "insertNewLineAbove/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "inspection/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "intention/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "projectFilesStats/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "miscUtil/", asList("plugin.groovy", "util/AClass.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "additionalClasspath/", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "integrationTest/", asList("plugin.groovy", "plugin-test.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "helloScala/", asList("plugin.scala")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "helloClojure/", asList("plugin.clj")));
            return actionGroup;
		}

		public List<String> selectedPluginIds() {
			Collection<VirtualFile> rootFiles = findPluginRootsFor(myFsTreeRef.get().getSelectedFiles());
			return map(rootFiles, virtualFile -> virtualFile.getName());
		}

		public boolean isActive() {
			return toolWindow.isActive();
		}

    }

	private static class MySimpleToolWindowPanel extends SimpleToolWindowPanel {
		private final Ref<FileSystemTree> fileSystemTree;

		public MySimpleToolWindowPanel(boolean vertical, Ref<FileSystemTree> fileSystemTree) {
			super(vertical);
			this.fileSystemTree = fileSystemTree;
		}

		/**
		 * Provides context for actions in plugin tree popup popup menu.
		 * Without it they would be disabled or won't work.
		 * <p/>
		 * Used by
		 * {@link com.intellij.openapi.fileChooser.actions.NewFileAction},
		 * {@link com.intellij.openapi.fileChooser.actions.NewFolderAction},
		 * {@link com.intellij.openapi.fileChooser.actions.FileDeleteAction}
		 */
		@Override public Object getData(@NonNls String dataId) {
			// this is used by create directory/file to get context in which they're executed
			// (without this they would be disabled or won't work)
			if (dataId.equals(FileSystemTree.DATA_KEY.getName())) return fileSystemTree.get();
			if (dataId.equals(FileChooserKeys.NEW_FILE_TYPE.getName())) return IDEUtil.GROOVY_FILE_TYPE;
			if (dataId.equals(FileChooserKeys.DELETE_ACTION_AVAILABLE.getName())) return true;
			if (dataId.equals(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName())) return fileSystemTree.get().getSelectedFiles();
			if (dataId.equals(PlatformDataKeys.TREE_EXPANDER.getName())) return new DefaultTreeExpander(fileSystemTree.get().getTree());
			return super.getData(dataId);
		}
	}

	private static class MyTree extends Tree implements DataProvider {
		private final Project project;
		private final DeleteProvider deleteProvider = new FileDeleteProviderWithRefresh();

		private MyTree(Project project) {
			this.project = project;
			getEmptyText().setText("No plugins to show");
			setRootVisible(false);
		}

        @Nullable @Override public Object getData(@NonNls String dataId) {
            if (PlatformDataKeys.NAVIGATABLE_ARRAY.is(dataId)) { // need this to be able to open files in toolwindow on double-click/enter
                List<FileNodeDescriptor> nodeDescriptors = TreeUtil.collectSelectedObjectsOfType(this, FileNodeDescriptor.class);
                List<Navigatable> navigatables = new ArrayList<>();
                for (FileNodeDescriptor nodeDescriptor : nodeDescriptors) {
                    navigatables.add(new OpenFileDescriptor(project, nodeDescriptor.getElement().getFile()));
                }
                return navigatables.toArray(new Navigatable[navigatables.size()]);
            } else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
                return deleteProvider;
            } else {
                return null;
            }
        }
	}

	private static class FileDeleteProviderWithRefresh implements DeleteProvider {
		private final DeleteProvider fileDeleteProvider = new VirtualFileDeleteProvider();

		@Override public void deleteElement(@NotNull DataContext dataContext) {
			fileDeleteProvider.deleteElement(dataContext);
			RefreshPluginsPanelAction.refreshPluginTree();
		}

		@Override public boolean canDeleteElement(@NotNull DataContext dataContext) {
			return fileDeleteProvider.canDeleteElement(dataContext);
		}
	}

}
