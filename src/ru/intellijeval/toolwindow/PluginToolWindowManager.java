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
package ru.intellijeval.toolwindow;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlighingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.actions.CollapseAllAction;
import com.intellij.ide.actions.ExpandAllAction;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import ru.intellijeval.*;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static java.util.Arrays.asList;
import static ru.intellijeval.EvalComponent.PLUGIN_EXAMPLES_PATH;
import static ru.intellijeval.EvalComponent.pluginExists;

/**
 * User: dima
 * Date: 11/08/2012
 */
public class PluginToolWindowManager {
	private static final String TOOL_WINDOW_ID = "Plugins";

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

	private static void reloadAllToolWindows() {
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
	private static void addRoots(FileChooserDescriptor descriptor, List<VirtualFile> virtualFiles) {
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
			toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT);
			toolWindow.setIcon(Util.PLUGIN_ICON);

			toolWindow.getContentManager().addContent(createContent(project));
		}

		private void unregisterWindowFrom(Project project) {
			ToolWindowManager.getInstance(project).unregisterToolWindow(TOOL_WINDOW_ID);
		}

		private Content createContent(Project project) {
			FileSystemTree fsTree = createFsTree(project);
			myFsTreeRef = Ref.create(fsTree);

			installPopupsInto(fsTree);

			JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(fsTree.getTree());
			panel = new MySimpleToolWindowPanel(true, myFsTreeRef).setProvideQuickActions(false);
			panel.add(scrollPane);
			panel.setToolbar(createToolBar());
			return ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
		}

		private void reloadPluginRoots(Project project) {
			FileSystemTree fsTree = createFsTree(project);
			myFsTreeRef.set(fsTree);

			installPopupsInto(fsTree);

			JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFsTreeRef.get().getTree());
			panel.remove(0);
			panel.add(scrollPane, 0);
		}

		private static void installPopupsInto(FileSystemTree fsTree) {
			AnAction action = new NewElementPopupAction();
			action.registerCustomShortcutSet(new CustomShortcutSet(shortcutsOf("NewElement")), fsTree.getTree());

			CustomizationUtil.installPopupHandler(fsTree.getTree(), "InetlliJEval.Popup", ActionPlaces.UNKNOWN);
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
					if (EvalComponent.pluginToPathMap().values().contains(file.getPath())) return Util.PLUGIN_ICON;
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

			Collection<String> pluginPaths = EvalComponent.pluginToPathMap().values();
			List<VirtualFile> virtualFiles = ContainerUtil.map(pluginPaths, new Function<String, VirtualFile>() {
				@Override public VirtualFile fun(String path) {
					return VirtualFileManager.getInstance().findFileByUrl("file://" + path);
				}
			});
			addRoots(descriptor, virtualFiles);

			return descriptor;
		}

		private JComponent createToolBar() {
			JPanel toolBarPanel = new JPanel(new GridLayout());
			DefaultActionGroup actionGroup = new DefaultActionGroup();
			actionGroup.add(withIcon(Util.ADD_PLUGIN_ICON, createAddPluginsGroup()));
			actionGroup.add(new DeletePluginAction(this));
			actionGroup.add(new RefreshPluginListAction());
			actionGroup.addSeparator();
			actionGroup.add(new EvaluatePluginAction());
			actionGroup.add(new EvaluateAllPluginsAction());
			actionGroup.addSeparator();
			actionGroup.add(withIcon(Util.EXPAND_ALL_ICON, new ExpandAllAction()));
			actionGroup.add(withIcon(Util.COLLAPSE_ALL_ICON, new CollapseAllAction()));
			actionGroup.add(withIcon(Util.SETTINGS_ICON, createSettingsGroup()));

			// this is a "hack" to force drop-down box appear below button
			// (see com.intellij.openapi.actionSystem.ActionPlaces#isToolbarPlace implementation for details)
			String place = ActionPlaces.EDITOR_TOOLBAR;
			toolBarPanel.add(ActionManager.getInstance().createActionToolbar(place, actionGroup, true).getComponent());
			return toolBarPanel;
		}

		private AnAction createSettingsGroup() {
			DefaultActionGroup actionGroup = new DefaultActionGroup("Settings", true);
			actionGroup.add(new ToggleAction("Run All Plugins on IDE Start") {
				@Override public boolean isSelected(AnActionEvent event) {
					return Settings.getInstance().runAllPluginsOnIDEStartup;
				}

				@Override public void setSelected(AnActionEvent event, boolean state) {
					Settings.getInstance().runAllPluginsOnIDEStartup = state;
				}
			});
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
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/helloWorld", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/helloWorldAction", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/helloPopupAction", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/helloToolwindow", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/helloTextEditor", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/transformSelectedText", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/insertNewLineAbove", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/helloWorldInspection", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/helloFileStats", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/utilExample", asList("plugin.groovy", "util/AClass.groovy")));
			actionGroup.add(new AddExamplePluginAction(PLUGIN_EXAMPLES_PATH + "/classpathExample", asList("plugin.groovy")));
			actionGroup.addSeparator();
			actionGroup.add(new AnAction("Add All") {
				@Override public void actionPerformed(AnActionEvent e) {
					AnAction[] actions = actionGroup.getChildActionsOrStubs();
					for (AnAction action : actions) {
						if (action instanceof AddExamplePluginAction) {
							Util.runAction(action, "ADD_ALL_EXAMPLES");
						}
					}
				}
			});
			return actionGroup;
		}

		public List<String> selectedPluginIds() {
			Collection<VirtualFile> rootFiles = findFilesMatchingPluginRoots(myFsTreeRef.get().getSelectedFiles());
			return ContainerUtil.map(rootFiles, new Function<VirtualFile, String>() {
				@Override public String fun(VirtualFile virtualFile) {
					return virtualFile.getName();
				}
			});
		}

		public boolean isActive() {
			return toolWindow.isActive();
		}

		private static Collection<VirtualFile> findFilesMatchingPluginRoots(VirtualFile[] files) {
			Set<VirtualFile> selectedPluginRoots = new HashSet<VirtualFile>();
			for (VirtualFile file : files) {
				VirtualFile root = pluginFolderOf(file);
				if (root != null) selectedPluginRoots.add(root);
			}
			return selectedPluginRoots;
		}

		private static VirtualFile pluginFolderOf(VirtualFile file) {
			if (file.getParent() == null) return null;
			if (!file.getParent().getPath().equals(EvalComponent.pluginsRootPath())) return pluginFolderOf(file.getParent());
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
		 *
		 * Used by
		 * {@link com.intellij.openapi.fileChooser.actions.NewFileAction},
		 * {@link com.intellij.openapi.fileChooser.actions.NewFolderAction},
		 * {@link com.intellij.openapi.fileChooser.actions.FileDeleteAction}
		 */
		@Override public Object getData(@NonNls String dataId) {
			// this is used by create directory/file to get context in which they're executed
			// (without this they would be disabled or won't work)
			if (dataId.equals(FileSystemTree.DATA_KEY.getName())) return fileSystemTree.get();
			if (dataId.equals(FileChooserKeys.NEW_FILE_TYPE.getName())) return Util.GROOVY_FILE_TYPE;
			if (dataId.equals(FileChooserKeys.DELETE_ACTION_AVAILABLE.getName())) return true;
			if (dataId.equals(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName())) return fileSystemTree.get().getSelectedFiles();
			if (dataId.equals(PlatformDataKeys.TREE_EXPANDER.getName()))
				return new DefaultTreeExpander(fileSystemTree.get().getTree());
			return super.getData(dataId);
		}
	}

	private static class MyTree extends Tree implements TypeSafeDataProvider {
		private final Project project;

		private MyTree(Project project) {
			this.project = project;
			getEmptyText().setText("No plugins to show");
			setRootVisible(false);
		}

		@Override public void calcData(DataKey key, DataSink sink) {
			if (PlatformDataKeys.NAVIGATABLE_ARRAY.equals(key)) { // need this to be able to open files in toolwindow on double-click/enter
				List<FileNodeDescriptor> nodeDescriptors = TreeUtil.collectSelectedObjectsOfType(this, FileNodeDescriptor.class);
				List<Navigatable> navigatables = new ArrayList<Navigatable>();
				for (FileNodeDescriptor nodeDescriptor : nodeDescriptors) {
					navigatables.add(new OpenFileDescriptor(project, nodeDescriptor.getElement().getFile()));
				}
				sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, navigatables.toArray(new Navigatable[navigatables.size()]));
			}
		}
	}


	// TODO add this and similar actions to file tree popup menu (ctrl+n)
	private static class AddNewPluginAction extends AnAction {
		private static final Logger LOG = Logger.getInstance(AddNewPluginAction.class);

		public AddNewPluginAction() {
			super("New Plugin");
		}

		@Override public void actionPerformed(AnActionEvent event) {
			String newPluginId = Messages.showInputDialog(event.getProject(), "Enter new plugin name:", "New Plugin", null);

			if (newPluginId == null) return;
			if (pluginExists(newPluginId)) {
				Messages.showErrorDialog(event.getProject(), "Plugin \"" + newPluginId + "\" already exists.", "New Plugin");
				return;
			}

			try {

				String text = EvalComponent.defaultPluginScript();
				FileUtil.writeToFile(new File(EvalComponent.pluginsRootPath() + "/" + newPluginId + "/" + EvalComponent.MAIN_SCRIPT), text);

			} catch (IOException e) {
				Project project = event.getProject();
				if (project != null) {
					Util.showErrorDialog(
							project,
							"Error adding plugin \"" + newPluginId + "\" to " + EvalComponent.pluginsRootPath(),
							"Add Plugin"
					);
				}
				LOG.error(e);
			}

			new RefreshPluginListAction().actionPerformed(event);
		}
	}

	public static class ExamplePluginInstaller {
		private final String pluginPath;
		private final List<String> sampleFiles;
		private final String pluginId;

		public ExamplePluginInstaller(String pluginPath, List<String> sampleFiles) {
			this.pluginPath = pluginPath;
			this.sampleFiles = sampleFiles;
			this.pluginId = extractIdFrom(pluginPath);
		}

		public void installPlugin(Listener listener) {
			for (String file : sampleFiles) {
				try {

					String text = EvalComponent.readSampleScriptFile(pluginPath, file);
					FileUtil.writeToFile(new File(EvalComponent.pluginsRootPath() + "/" + pluginId + "/" + file), text);

				} catch (IOException e) {
					listener.onException(e, pluginPath);
				}
			}
		}

		public static String extractIdFrom(String pluginPath) {
			String[] split = pluginPath.split("/");
			return split[split.length - 1];
		}

		public interface Listener {
			void onException(Exception e, String pluginPath);
		}
	}

	private static class AddExamplePluginAction extends AnAction {
		private static final Logger LOG = Logger.getInstance(AddExamplePluginAction.class);

		private final String pluginId;
		private final ExamplePluginInstaller examplePluginInstaller;

		public AddExamplePluginAction(String pluginPath, List<String> sampleFiles) {
			examplePluginInstaller = new ExamplePluginInstaller(pluginPath, sampleFiles);
			this.pluginId = ExamplePluginInstaller.extractIdFrom(pluginPath);

			getTemplatePresentation().setText(pluginId);
		}

		@Override public void actionPerformed(final AnActionEvent event) {
			examplePluginInstaller.installPlugin(new ExamplePluginInstaller.Listener() {
				@Override public void onException(Exception e, String pluginPath) {
					logException(e, event, pluginPath);
				}
			});
			new RefreshPluginListAction().actionPerformed(event);
		}

		@Override public void update(AnActionEvent event) {
			event.getPresentation().setEnabled(!pluginExists(pluginId));
		}

		private void logException(Exception e, AnActionEvent event, String pluginPath) {
			Project project = event.getProject();
			if (project != null) {
				Util.showErrorDialog(
						project,
						"Error adding plugin \"" + pluginPath + "\" to " + EvalComponent.pluginsRootPath(),
						"Add Plugin"
				);
			}
			LOG.error(e);
		}
	}

	private static class AddPluginFromDiskAction extends AnAction {
		private static final Logger LOG = Logger.getInstance(AddPluginFromDiskAction.class);

		public AddPluginFromDiskAction() {
			super("Plugin from Disk");
		}

		@Override public void actionPerformed(AnActionEvent event) {
			FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, true, false);

			addRoots(descriptor, getFileSystemRoots());

			VirtualFile virtualFile = FileChooser.chooseFile(descriptor, event.getProject(), null);
			if (virtualFile == null) return;

			if (EvalComponent.isInvalidPluginFolder(virtualFile) &&
					userDoesNotWantToAddFolder(virtualFile, event.getProject())) return;

			File fromDir = new File(virtualFile.getPath());
			File toDir = new File(EvalComponent.pluginsRootPath() + "/" + fromDir.getName());
			try {

				boolean created = toDir.mkdirs();
				if (!created) {
					throw new IllegalStateException("Failed to create " + toDir.getPath());
				}
				FileUtil.copyDirContent(fromDir, toDir);

			} catch (IOException e) {
				Project project = event.getProject();
				if (project != null) {
					Util.showErrorDialog(
							project,
							"Error adding plugin \"" + fromDir.getName() + "\" to " + EvalComponent.pluginsRootPath(),
							"Add Plugin"
					);
				}
				LOG.error(e);
			}

			new RefreshPluginListAction().actionPerformed(event);
		}

		private static List<VirtualFile> getFileSystemRoots() {
			final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
			final Set<VirtualFile> roots = new HashSet<VirtualFile>();
			final File[] ioRoots = File.listRoots();
			if (ioRoots != null) {
				for (final File root : ioRoots) {
					final String path = FileUtil.toSystemIndependentName(root.getAbsolutePath());
					final VirtualFile file = localFileSystem.findFileByPath(path);
					if (file != null) {
						roots.add(file);
					}
				}
			}
			ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
			Collections.addAll(result, VfsUtil.toVirtualFileArray(roots));
			return result;
		}

		private boolean userDoesNotWantToAddFolder(VirtualFile virtualFile, Project project) {
			int returnValue = Messages.showOkCancelDialog(
					project,
					"Folder \"" + virtualFile.getPath() + "\" is not valid plugin folder because it does not contain \"" + EvalComponent.MAIN_SCRIPT + "\"." +
							"\nDo you want to add it anyway?",
					"Add Plugin",
					CommonBundle.getYesButtonText(),
					CommonBundle.getNoButtonText(),
					Messages.getQuestionIcon()
			);
			return returnValue != 0;
		}
	}

	// TODO wrap "delete folder" file tree action ("delete" shortcut) to check whether the folder is plugin
	private static class DeletePluginAction extends AnAction {
		private final PluginToolWindow pluginsToolWindow;

		public DeletePluginAction(PluginToolWindow pluginsToolWindow) {
			super("Delete plugin", "Delete plugin", Util.DELETE_PLUGIN_ICON);
			this.pluginsToolWindow = pluginsToolWindow;
		}

		@Override public void actionPerformed(AnActionEvent event) {
			List<String> pluginIds = pluginsToolWindow.selectedPluginIds();

			if (userDoesNotWantToRemovePlugins(pluginIds, event.getProject())) return;

			for (String pluginId : pluginIds) {
				String pluginPath = EvalComponent.pluginToPathMap().get(pluginId);
				FileUtil.delete(new File(pluginPath));
			}

			new RefreshPluginListAction().actionPerformed(event);
		}

		@Override public void update(AnActionEvent e) {
			boolean anyPluginPathSelected = !pluginsToolWindow.selectedPluginIds().isEmpty();
			e.getPresentation().setEnabled(anyPluginPathSelected);
		}

		private static boolean userDoesNotWantToRemovePlugins(List<String> pluginIds, Project project) {
			String message;
			if (pluginIds.size() == 1) {
				message = "Do you want to delete plugin \"" + pluginIds.get(0) + "\"?";
			} else if (pluginIds.size() == 2) {
				message = "Do you want to delete plugin \"" + pluginIds.get(0) + "\" and \"" + pluginIds.get(1) + "\"?";
			} else {
				message = "Do you want to delete plugins \"" + StringUtil.join(pluginIds, ", ") + "\"?";
			}
			int returnValue = Messages.showOkCancelDialog(
					project,
					message,
					"Delete",
					ApplicationBundle.message("button.delete"),
					CommonBundle.getCancelButtonText(),
					Messages.getQuestionIcon()
			);
			return returnValue != 0;
		}
	}

	public static class RefreshPluginListAction extends AnAction {

		public RefreshPluginListAction() {
			super("Refresh plugin list", "Refresh plugin list", Util.REFRESH_PLUGIN_LIST_ICON);
		}

		@Override public void actionPerformed(AnActionEvent e) {
			ApplicationManager.getApplication().runWriteAction(new Runnable() {
				@Override public void run() {
					VirtualFileManager.getInstance().refreshWithoutFileWatcher(false); // TODO this is a crude workaround to make virtual files refresh no matter what

					VirtualFile pluginsRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + EvalComponent.pluginsRootPath());
					if (pluginsRoot == null) return;

					RefreshQueue.getInstance().refresh(false, true, new Runnable() {
						@Override public void run() {
							reloadAllToolWindows();
						}
					}, pluginsRoot);
				}
			});
		}
	}


	private static class DisableHighlightingRunnable implements Runnable {
		private final Project project;
		private final Ref<FileSystemTree> myFsTree;

		public DisableHighlightingRunnable(Project project, Ref<FileSystemTree> myFsTree) {
			this.project = project;
			this.myFsTree = myFsTree;
		}

		@Override public void run() {
			VirtualFile selectedFile = myFsTree.get().getSelectedFile();
			if (selectedFile == null) return;

			PsiFile psiFile = PsiManager.getInstance(project).findFile(selectedFile);
			if (psiFile == null) return;

			disableHighlightingFor(psiFile);
		}

		private static void disableHighlightingFor(PsiFile psiFile) {
			FileViewProvider viewProvider = psiFile.getViewProvider();
			List<Language> languages = new ArrayList<Language>(viewProvider.getLanguages());
			Collections.sort(languages, PsiUtilBase.LANGUAGE_COMPARATOR);

			for (Language language : languages) {
				PsiElement root = viewProvider.getPsi(language);
				HighlightLevelUtil.forceRootHighlighting(root, FileHighlighingSetting.SKIP_HIGHLIGHTING);
			}
			DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(psiFile.getProject());
			analyzer.restart();
		}
	}

}
