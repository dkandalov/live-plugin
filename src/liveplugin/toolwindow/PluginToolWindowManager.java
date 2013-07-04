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
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.PathManager;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Pair;
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
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.tree.TreeUtil;
import liveplugin.IdeUtil;
import liveplugin.LivePluginAppComponent;
import liveplugin.pluginrunner.RunPluginAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.Pair.create;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Arrays.asList;
import static liveplugin.IdeUtil.*;
import static liveplugin.LivePluginAppComponent.*;
import static liveplugin.MyFileUtil.fileNamesMatching;

/**
 * User: dima
 * Date: 11/08/2012
 */
public class PluginToolWindowManager {

	private static final String PLUGINS_TOOL_WINDOW_ID = "Plugins";

	private static final Map<Project, PluginToolWindow> toolWindowsByProject = new HashMap<Project, PluginToolWindow>();

	public static AnAction addFromGitHubAction; // TODO refactor this!!!


	public PluginToolWindowManager init() {
		ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerListener() {
			@Override public void projectOpened(Project project) {
				PluginToolWindow pluginToolWindow = new PluginToolWindow();
				pluginToolWindow.registerWindowFor(project);

				putToolWindow(pluginToolWindow, project);
			}

			@Override public void projectClosed(Project project) {
				PluginToolWindow pluginToolWindow = removeToolWindow(project);
				if (pluginToolWindow != null) pluginToolWindow.unregisterWindowFrom(project);
			}

			@Override public boolean canCloseProject(Project project) {
				return true;
			}

			@Override public void projectClosing(Project project) {
			}
		});
		return this;
	}

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

	@SuppressWarnings("deprecation") // this is to make it compatible with as old intellij versions as possible
	static void addRoots(FileChooserDescriptor descriptor, List<VirtualFile> virtualFiles) {
		if (virtualFiles.isEmpty()) {
			descriptor.setRoot(null);
		} else {
			descriptor.setRoot(virtualFiles.remove(0));
			for (VirtualFile virtualFile : virtualFiles) {
				if (virtualFile != null)
					descriptor.addRoot(virtualFile);
			}
			// adding "null" is a hack to suppress size == 1 checks in com.intellij.openapi.fileChooser.ex.RootFileElement
			// (if there is only one plugin, this forces tree show it as a package)
			descriptor.addRoot(null);
		}
	}

	public static class PluginToolWindow {
		private Ref<FileSystemTree> myFsTreeRef = new Ref<FileSystemTree>();
		private SimpleToolWindowPanel panel;
		private ToolWindow toolWindow;

		public void registerWindowFor(Project project) {
			ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
			toolWindow = toolWindowManager.registerToolWindow(PLUGINS_TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT);
			// TODO resize at runtime? get in intellij log: "WARN - openapi.wm.impl.ToolWindowImpl - ToolWindow icons should be 13x13"
			toolWindow.setIcon(IdeUtil.PLUGIN_ICON);

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
			panel = new MySimpleToolWindowPanel(true, myFsTreeRef).setProvideQuickActions(false);
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

		private static void installPopupMenuInto(FileSystemTree fsTree) {
			AnAction action = new NewElementPopupAction();
			action.registerCustomShortcutSet(new CustomShortcutSet(shortcutsOf("NewElement")), fsTree.getTree());

			CustomizationUtil.installPopupHandler(fsTree.getTree(), "LivePlugin.Popup", ActionPlaces.UNKNOWN);
		}

		private static Shortcut[] shortcutsOf(String actionId) {
			return KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
		}

		private FileSystemTree createFsTree(Project project) {
			Ref<FileSystemTree> myFsTreeRef = new Ref<FileSystemTree>();
			MyTree myTree = new MyTree(project);

			// must be installed before adding tree to FileSystemTreeImpl
			EditSourceOnDoubleClickHandler.install(myTree, new DisableHighlightingRunnable(project, myFsTreeRef));

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
			myFsTreeRef.set(result);

			// must be installed after adding tree to FileSystemTreeImpl
			EditSourceOnEnterKeyHandler.install(myTree, new DisableHighlightingRunnable(project, myFsTreeRef));

			return result;
		}

		private static FileChooserDescriptor createFileChooserDescriptor() {
			FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, true) {
				@Override public Icon getIcon(VirtualFile file) {
					if (LivePluginAppComponent.pluginIdToPathMap().values().contains(file.getPath())) return IdeUtil.PLUGIN_ICON;
					return super.getIcon(file);
				}

				@Override public String getName(VirtualFile virtualFile) {
					return virtualFile.getName();
				}

				@Nullable @Override public String getComment(VirtualFile virtualFile) {
					return "";
				}
			};
			descriptor.setShowFileSystemRoots(false);
			descriptor.setIsTreeRootVisible(false);

			Collection<String> pluginPaths = LivePluginAppComponent.pluginIdToPathMap().values();
			List<VirtualFile> virtualFiles = map(pluginPaths, new Function<String, VirtualFile>() {
				@Override public VirtualFile fun(String path) {
					return VirtualFileManager.getInstance().findFileByUrl("file://" + path);
				}
			});
			addRoots(descriptor, virtualFiles);

			return descriptor;
		}

		private JComponent createToolBar() {
			DefaultActionGroup actionGroup = new DefaultActionGroup();
			actionGroup.add(withIcon(IdeUtil.ADD_PLUGIN_ICON, createAddPluginsGroup()));
			actionGroup.add(new DeletePluginAction());
			actionGroup.add(new RunPluginAction());
			actionGroup.addSeparator();
			actionGroup.add(new RefreshPluginTreeAction());
			actionGroup.add(withIcon(IdeUtil.EXPAND_ALL_ICON, new ExpandAllAction()));
			actionGroup.add(withIcon(IdeUtil.COLLAPSE_ALL_ICON, new CollapseAllAction()));
			actionGroup.addSeparator();
			actionGroup.add(withIcon(IdeUtil.SETTINGS_ICON, createSettingsGroup()));

			// this is a "hack" to force drop-down box appear below button
			// (see com.intellij.openapi.actionSystem.ActionPlaces#isToolbarPlace implementation for details)
			String place = ActionPlaces.EDITOR_TOOLBAR;

			JPanel toolBarPanel = new JPanel(new GridLayout());
			toolBarPanel.add(ActionManager.getInstance().createActionToolbar(place, actionGroup, true).getComponent());
			return toolBarPanel;
		}

		private AnAction createSettingsGroup() {
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
			actionGroup.add(new DefaultActionGroup("Languages Support", true) {{
				add(new AddScalaLibsAsDependency());
				add(new AddClojureLibsAsDependency());
				add(new DownloadScalaLibs());
				add(new DownloadClojureLibs());
			}});

			return actionGroup;
		}

		private AnAction createAddPluginsGroup() {
			DefaultActionGroup actionGroup = new DefaultActionGroup("Add Plugin", true);
			actionGroup.add(new AddNewPluginAction());
			actionGroup.add(new AddPluginFromDiskAction());
			if (addFromGitHubAction != null)
				actionGroup.add(addFromGitHubAction);
			actionGroup.add(createAddPluginsExamplesGroup());
			return actionGroup;
		}

		private AnAction createAddPluginsExamplesGroup() {
			final DefaultActionGroup actionGroup = new DefaultActionGroup("Examples", true);
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/helloWorld", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/helloWorldAction", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/helloPopupAction", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/helloToolwindow", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/helloTextEditor", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/transformSelectedText", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/insertNewLineAbove", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/helloWorldInspection", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/helloFileStats", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/utilExample", asList("plugin.groovy", "util/AClass.groovy")));
			actionGroup.add(new AddExamplePluginAction(LivePluginAppComponent.PLUGIN_EXAMPLES_PATH + "/classpathExample", asList("plugin.groovy")));
			actionGroup.addSeparator();
			actionGroup.add(new AnAction("Add All") {
				@Override public void actionPerformed(AnActionEvent e) {
					AnAction[] actions = actionGroup.getChildActionsOrStubs();
					for (AnAction action : actions) {
						if (action instanceof AddExamplePluginAction) {
							IdeUtil.runAction(action, "ADD_ALL_EXAMPLES");
						}
					}
				}
			});
			return actionGroup;
		}

		public List<String> selectedPluginIds() {
			Collection<VirtualFile> rootFiles = findPluginRootsFor(myFsTreeRef.get().getSelectedFiles());
			return map(rootFiles, new Function<VirtualFile, String>() {
				@Override public String fun(VirtualFile virtualFile) {
					return virtualFile.getName();
				}
			});
		}

		public boolean isActive() {
			return toolWindow.isActive();
		}

		static Collection<VirtualFile> findPluginRootsFor(VirtualFile[] files) {
			Set<VirtualFile> selectedPluginRoots = new HashSet<VirtualFile>();
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
			if (dataId.equals(FileChooserKeys.NEW_FILE_TYPE.getName())) return IdeUtil.GROOVY_FILE_TYPE;
			if (dataId.equals(FileChooserKeys.DELETE_ACTION_AVAILABLE.getName())) return true;
			if (dataId.equals(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName()))
				return fileSystemTree.get().getSelectedFiles();
			if (dataId.equals(PlatformDataKeys.TREE_EXPANDER.getName()))
				return new DefaultTreeExpander(fileSystemTree.get().getTree());
			return super.getData(dataId);
		}
	}

	private static class MyTree extends Tree implements TypeSafeDataProvider {
		private final Project project;
		private final DeleteProvider deleteProvider = new FileDeleteProviderWithRefresh();

		private MyTree(Project project) {
			this.project = project;
			getEmptyText().setText("No plugins to show");
			setRootVisible(false);
		}

		@Override public void calcData(DataKey key, DataSink sink) {
			if (key == PlatformDataKeys.NAVIGATABLE_ARRAY) { // need this to be able to open files in toolwindow on double-click/enter
				List<FileNodeDescriptor> nodeDescriptors = TreeUtil.collectSelectedObjectsOfType(this, FileNodeDescriptor.class);
				List<Navigatable> navigatables = new ArrayList<Navigatable>();
				for (FileNodeDescriptor nodeDescriptor : nodeDescriptors) {
					navigatables.add(new OpenFileDescriptor(project, nodeDescriptor.getElement().getFile()));
				}
				sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, navigatables.toArray(new Navigatable[navigatables.size()]));
			} else if (key == PlatformDataKeys.DELETE_ELEMENT_PROVIDER) {
				sink.put(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, deleteProvider);
			}
		}
	}

	private static class FileDeleteProviderWithRefresh implements DeleteProvider {
		private final DeleteProvider fileDeleteProvider = new VirtualFileDeleteProvider();

		@Override public void deleteElement(@NotNull DataContext dataContext) {
			fileDeleteProvider.deleteElement(dataContext);
			new RefreshPluginTreeAction().actionPerformed(null);
		}

		@Override public boolean canDeleteElement(@NotNull DataContext dataContext) {
			return fileDeleteProvider.canDeleteElement(dataContext);
		}
	}


	private static class AddScalaLibsAsDependency extends AnAction {
		private static final String LIBRARY_NAME = "LivePlugin - Scala";

		@Override public void actionPerformed(AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;

			if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, LIBRARY_NAME)) {
				DependenciesUtil.removeLibraryDependencyFrom(project, LIBRARY_NAME);
			} else {
				List<Pair<String, OrderRootType>> paths = map(fileNamesMatching(DownloadScalaLibs.LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH), new Function<String, Pair<String, OrderRootType>>() {
					@Override public Pair<String, OrderRootType> fun(String fileName) {
						return create("jar://" + LIVEPLUGIN_LIBS_PATH + fileName + "!/", CLASSES);
					}
				});
				DependenciesUtil.addLibraryDependencyTo(project, LIBRARY_NAME, paths);
			}
		}

		@Override public void update(AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;

			if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, LIBRARY_NAME)) {
				event.getPresentation().setText("Remove Scala Libraries from Project");
				event.getPresentation().setDescription("Remove Scala Libraries from Project");
			} else {
				event.getPresentation().setText("Add Scala Libraries to Project");
				event.getPresentation().setDescription("Add Scala Libraries to Project");
				event.getPresentation().setEnabled(scalaIsOnClassPath());
			}
		}
	}

	private static class AddClojureLibsAsDependency extends AnAction {
		private static final String LIBRARY_NAME = "LivePlugin - Clojure";

		@Override public void actionPerformed(AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;

			if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, LIBRARY_NAME)) {
				DependenciesUtil.removeLibraryDependencyFrom(project, LIBRARY_NAME);
			} else {
				List<Pair<String, OrderRootType>> paths = map(fileNamesMatching(DownloadClojureLibs.LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH), new Function<String, Pair<String, OrderRootType>>() {
					@Override public Pair<String, OrderRootType> fun(String fileName) {
						return create("jar://" + LIVEPLUGIN_LIBS_PATH + fileName + "!/", CLASSES);
					}
				});
				DependenciesUtil.addLibraryDependencyTo(project, LIBRARY_NAME, paths);
			}
		}

		@Override public void update(AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;

			if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, LIBRARY_NAME)) {
				event.getPresentation().setText("Remove Clojure Libraries from Project");
				event.getPresentation().setDescription("Remove Clojure Libraries from Project");
			} else {
				event.getPresentation().setText("Add Clojure Libraries to Project");
				event.getPresentation().setDescription("Add Clojure Libraries to Project");
				event.getPresentation().setEnabled(clojureIsOnClassPath());
			}
		}
	}

	private static class DownloadScalaLibs extends AnAction {
		public static final String LIB_FILES_PATTERN = "(scala-|scalap).*jar";
		private static final String APPROXIMATE_SIZE = "(~26Mb)";

		@Override public void actionPerformed(AnActionEvent event) {
			if (scalaIsOnClassPath()) {
				int answer = Messages.showYesNoDialog(event.getProject(),
						"Do you want to remove Scala libraries from plugin classpath? This action cannot be undone.", "Live Plugin", null);
				if (answer == Messages.YES) {
					for (String fileName : fileNamesMatching(LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH)) {
						FileUtil.delete(new File(LIVEPLUGIN_LIBS_PATH + fileName));
					}
					askUserIfShouldRestartIde();
				}
			} else {
				int answer = Messages.showOkCancelDialog(event.getProject(),
						"Scala libraries " + APPROXIMATE_SIZE + " will be downloaded to '" + LIVEPLUGIN_LIBS_PATH + "'." +
						"\n(If you already have scala >= 2.10, you can copy it manually and restart IDE.)", "Live Plugin", null);
				if (answer != Messages.OK) return;

				List<String> scalaLibs = asList("scala-library", "scala-compiler", "scala-reflect", "scala-swing",
						"scala-partest", "scala-actors", "scala-actors-migration", "scalap");
				List<Pair<String, String>> urlAndFileNamePairs = map(scalaLibs, new Function<String, Pair<String, String>>() {
					@Override public Pair<String, String> fun(String it) {
						return Pair.create("http://repo1.maven.org/maven2/org/scala-lang/" + it + "/2.10.2/", it + "-2.10.jar");
					}
				});

				boolean downloaded = downloadFiles(urlAndFileNamePairs, LIVEPLUGIN_LIBS_PATH);
				if (downloaded) {
					askUserIfShouldRestartIde(); // TODO load classes at runtime
				} else {
					NotificationGroup.balloonGroup("Live Plugin")
							.createNotification("Failed to download Scala libraries", NotificationType.WARNING);
				}
			}
		}

		@Override public void update(AnActionEvent event) {
			if (scalaIsOnClassPath()) {
				event.getPresentation().setText("Remove Scala from Plugin Classpath");
				event.getPresentation().setDescription("Remove Scala from Plugin Classpath");
			} else {
				event.getPresentation().setText("Download Scala to Plugin Classpath");
				event.getPresentation().setDescription("Download Scala libraries to plugin classpath to enable scala plugins support " + APPROXIMATE_SIZE);
			}
		}
	}

	private static class DownloadClojureLibs extends AnAction {
		public static final String LIB_FILES_PATTERN = "clojure-.*jar";
		private static final String APPROXIMATE_SIZE = "(~4Mb)";

		@Override public void actionPerformed(AnActionEvent event) {
			if (clojureIsOnClassPath()) {
				int answer = Messages.showYesNoDialog(event.getProject(),
						"Do you want to remove Clojure libraries from plugin classpath? This action cannot be undone.", "Live Plugin", null);
				if (answer == Messages.YES) {
					for (String fileName : fileNamesMatching(LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH)) {
						FileUtil.delete(new File(LIVEPLUGIN_LIBS_PATH + fileName));
					}
					askUserIfShouldRestartIde();
				}
			} else {
				int answer = Messages.showOkCancelDialog(event.getProject(),
						"Clojure libraries " + APPROXIMATE_SIZE + " will be downloaded to '" + LIVEPLUGIN_LIBS_PATH + "'." +
						"\n(If you already have clojure >= 1.5.1, you can copy it manually and restart IDE.)", "Live Plugin", null);
				if (answer != Messages.OK) return;

				boolean downloaded = downloadFile("http://repo1.maven.org/maven2/org/clojure/clojure/1.5.1/", "clojure-1.5.1.jar", LIVEPLUGIN_LIBS_PATH);
				if (downloaded) {
					askUserIfShouldRestartIde(); // TODO load classes at runtime
				} else {
					NotificationGroup.balloonGroup("Live Plugin")
							.createNotification("Failed to download Clojure libraries", NotificationType.WARNING);
				}
			}
		}

		@Override public void update(AnActionEvent event) {
			if (clojureIsOnClassPath()) {
				event.getPresentation().setText("Remove Clojure from Plugin Classpath");
				event.getPresentation().setDescription("Remove Clojure from Plugin Classpath");
			} else {
				event.getPresentation().setText("Download Clojure to Plugin Classpath");
				event.getPresentation().setDescription("Download Clojure libraries to plugin classpath to enable clojure plugins support " + APPROXIMATE_SIZE);
			}
		}
	}

	private static class AddPluginJarAsDependency extends AnAction {
		private static final String LIVE_PLUGIN_LIBRARY = "LivePlugin";

		@Override public void actionPerformed(AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;

			if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, LIVE_PLUGIN_LIBRARY)) {
				DependenciesUtil.removeLibraryDependencyFrom(project, LIVE_PLUGIN_LIBRARY);
			} else {
				//noinspection unchecked
				DependenciesUtil.addLibraryDependencyTo(project, LIVE_PLUGIN_LIBRARY, Arrays.asList(
						create(findPathToMyClasses(), CLASSES),
						create(findPathToMyClasses() + "src/", SOURCES)
				));
			}
		}

		private static String findPathToMyClasses() {
			String pathToMyClasses = PathUtil.getJarPathForClass(LivePluginAppComponent.class);
			// need trailing "/" because folder dependency doesn't work without it
			if (pathToMyClasses.endsWith(".jar")) {
				pathToMyClasses = "jar://" + pathToMyClasses + "!/";
			} else {
				pathToMyClasses = "file://" + pathToMyClasses + "/";
			}
			return pathToMyClasses;
		}

		@Override public void update(AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;

			if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, LIVE_PLUGIN_LIBRARY)) {
				event.getPresentation().setText("Remove LivePlugin Jar from Project");
				event.getPresentation().setDescription(
						"Remove LivePlugin jar from project dependencies. This will enable auto-complete and other IDE features for IntelliJ classes.");
			} else {
				event.getPresentation().setText("Add LivePlugin Jar to Project");
				event.getPresentation().setDescription(
						"Add LivePlugin jar to project dependencies. This will enable auto-complete and other IDE features for PluginUtil.");
			}
		}

	}

	private static class AddIDEAJarsAsDependencies extends AnAction {
		private static final String IDEA_JARS_LIBRARY = "IDEA jars";

		@Override public void actionPerformed(AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;

			if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, IDEA_JARS_LIBRARY)) {
				DependenciesUtil.removeLibraryDependencyFrom(project, IDEA_JARS_LIBRARY);
			} else {
				String ideaJarsPath = PathManager.getHomePath() + "/lib/";
				//noinspection unchecked
				DependenciesUtil.addLibraryDependencyTo(project, IDEA_JARS_LIBRARY, Arrays.asList(
						create("jar://" + ideaJarsPath + "openapi.jar!/", CLASSES),
						create("jar://" + ideaJarsPath + "idea.jar!/", CLASSES),
						create("jar://" + ideaJarsPath + "idea_rt.jar!/", CLASSES),
						create("jar://" + ideaJarsPath + "annotations.jar!/", CLASSES),
						create("jar://" + ideaJarsPath + "util.jar!/", CLASSES),
						create("jar://" + ideaJarsPath + "extensions.jar!/", CLASSES),
						create("jar://" + ideaJarsPath + findGroovyJarOn(ideaJarsPath) + "!/", CLASSES)
				));
			}
		}

		private static String findGroovyJarOn(String ideaJarsPath) {
			List<String> files = fileNamesMatching("groovy-all-.*jar", ideaJarsPath);
			if (files.isEmpty()) return "could-not-find-groovy.jar";
			else return files.get(0);
		}

		@Override public void update(AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;

			if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, IDEA_JARS_LIBRARY)) {
				event.getPresentation().setText("Remove IDEA Jars from Project");
				event.getPresentation().setDescription("Remove IDEA jars dependencies from project");
			} else {
				event.getPresentation().setText("Add IDEA Jars to Project");
				event.getPresentation().setDescription("Add IDEA jars to project as dependencies");
			}
		}
	}


}
