package ru.intellijeval.toolwindow;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlighingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.actions.CollapseAllAction;
import com.intellij.ide.actions.ExpandAllAction;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
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
import fork.com.intellij.openapi.fileChooser.FileChooser;
import fork.com.intellij.openapi.fileChooser.FileChooserDescriptor;
import fork.com.intellij.openapi.fileChooser.FileSystemTree;
import fork.com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import fork.com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import git4idea.Notificator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import ru.intellijeval.EvalComponent;
import ru.intellijeval.EvaluateAllPluginsAction;
import ru.intellijeval.EvaluatePluginAction;
import ru.intellijeval.Util;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static java.util.Arrays.asList;
import static ru.intellijeval.EvalComponent.pluginExists;

/**
 * User: dima
 * Date: 11/08/2012
 */
public class PluginToolWindowManager {
	private static final String TOOL_WINDOW_ID = "Plugins";

	private static final Map<Project, PluginToolWindow> toolWindowsByProject = new HashMap<Project, PluginToolWindow>();

	@Nullable
	public static PluginToolWindow getToolWindowFor(Project project) {
		return toolWindowsByProject.get(project);
	}

	public static void reloadAllToolWindows() {
		for (Map.Entry<Project, PluginToolWindow> entry : toolWindowsByProject.entrySet()) {
			Project project = entry.getKey();
			PluginToolWindow toolWindow = entry.getValue();

			toolWindow.reloadPluginRoots(project);
		}
	}

	public static void putToolWindow(PluginToolWindow pluginToolWindow, Project project) {
		toolWindowsByProject.put(project, pluginToolWindow);
	}

	public static PluginToolWindow removeToolWindow(Project project) {
		return toolWindowsByProject.remove(project);
	}

	public PluginToolWindowManager init() {
		ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerListener() {
			@Override
			public void projectOpened(Project project) {
				PluginToolWindow pluginToolWindow = new PluginToolWindow();
				pluginToolWindow.registerWindowFor(project);

				putToolWindow(pluginToolWindow, project);
			}

			@Override
			public void projectClosed(Project project) {
				PluginToolWindow pluginToolWindow = removeToolWindow(project);
				if (pluginToolWindow != null) pluginToolWindow.unregisterWindowFrom(project);
			}

			@Override
			public boolean canCloseProject(Project project) {
				return true;
			}

			@Override
			public void projectClosing(Project project) {
			}
		});
		return this;
	}

	public static class PluginToolWindow {
		private Ref<FileSystemTree> myFsTreeRef = Ref.create();
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
			Ref<FileSystemTree> myFsTreeRef = Ref.create();
			MyTree myTree = new MyTree(project);

			// must be installed before adding tree to FileSystemTreeImpl
			EditSourceOnDoubleClickHandler.install(myTree, new DisableHighlightingRunnable(project, myFsTreeRef));

			FileSystemTree result = new PluginsFileSystemTree(project, createDescriptor(), myTree, null, null, null);
			myFsTreeRef.set(result);

			// must be installed after adding tree to FileSystemTreeImpl
			EditSourceOnEnterKeyHandler.install(myTree, new DisableHighlightingRunnable(project, myFsTreeRef));

			return result;
		}

		private static FileChooserDescriptor createDescriptor() {
			FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, true) {
				@Override public Icon getOpenIcon(VirtualFile file) {
					if (EvalComponent.pluginToPathMap().values().contains(file.getPath())) return Util.PLUGIN_ICON;
					return super.getOpenIcon(file);
				}

				@Override public Icon getClosedIcon(VirtualFile file) {
					if (EvalComponent.pluginToPathMap().values().contains(file.getPath())) return Util.PLUGIN_ICON;
					return super.getClosedIcon(file);
				}
			};
			descriptor.setShowFileSystemRoots(false);
			descriptor.setIsTreeRootVisible(false);

			Collection<String> plugPaths = EvalComponent.pluginToPathMap().values();
			List<VirtualFile> virtualFiles = ContainerUtil.map(plugPaths, new Function<String, VirtualFile>() {
				@Override public VirtualFile fun(String path) {
					return VirtualFileManager.getInstance().findFileByUrl("file://" + path);
				}
			});
			descriptor.setRoots(virtualFiles);

			return descriptor;
		}

		private JComponent createToolBar() {
			JPanel toolBarPanel = new JPanel(new GridLayout());
			DefaultActionGroup actionGroup = new DefaultActionGroup();
			actionGroup.add(createAddPluginsGroup());
			actionGroup.add(new DeletePluginAction(this, myFsTreeRef));
			actionGroup.add(new RefreshPluginListAction());
			actionGroup.addSeparator();
			actionGroup.add(new EvaluatePluginAction());
			actionGroup.add(new EvaluateAllPluginsAction());
			actionGroup.addSeparator();
			actionGroup.add(withIcon(Util.EXPAND_ALL_ICON, new ExpandAllAction()));
			actionGroup.add(withIcon(Util.COLLAPSE_ALL_ICON, new CollapseAllAction()));

			toolBarPanel.add(ActionManager.getInstance().createActionToolbar(TOOL_WINDOW_ID, actionGroup, true).getComponent());
			return toolBarPanel;
		}

		private AnAction createAddPluginsGroup() {
			DefaultActionGroup actionGroup = new DefaultActionGroup("Add Plugin", true);
			actionGroup.add(new AddNewPluginAction());
			actionGroup.add(new AddPluginFromDiskAction());
			actionGroup.add(createAddPluginsExamplesGroup());
			return withIcon(Util.ADD_PLUGIN_ICON, actionGroup);
		}

		private AnAction createAddPluginsExamplesGroup() {
			DefaultActionGroup actionGroup = new DefaultActionGroup("Example", true);
			actionGroup.add(new AddExamplePluginAction("/ru/intellijeval/exampleplugins/helloWorld", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction("/ru/intellijeval/exampleplugins/helloWorldAction", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction("/ru/intellijeval/exampleplugins/helloPopupAction", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction("/ru/intellijeval/exampleplugins/helloToolwindow", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction("/ru/intellijeval/exampleplugins/helloTextEditor", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction("/ru/intellijeval/exampleplugins/helloWorldInspection", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction("/ru/intellijeval/exampleplugins/helloFileStats", asList("plugin.groovy")));
			actionGroup.add(new AddExamplePluginAction("/ru/intellijeval/exampleplugins/utilExample", asList("plugin.groovy", "util/AClass.groovy")));
			actionGroup.add(new AddExamplePluginAction("/ru/intellijeval/exampleplugins/classpathExample", asList("plugin.groovy")));
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

		@Override public Object getData(@NonNls String dataId) {
			// this is used by actions like create directory/file
			if (dataId.equals(FileSystemTree.DATA_KEY.getName())) return fileSystemTree.get();
			if (dataId.equals(FileChooserKeys.NEW_FILE_TYPE.getName())) return Util.GROOVY_FILE_TYPE;
			if (dataId.equals(FileChooserKeys.DELETE_ACTION_AVAILABLE.getName())) return true;
			if (dataId.equals(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName())) return fileSystemTree.get().getSelectedFiles();
			if (dataId.equals(PlatformDataKeys.TREE_EXPANDER.getName()))
				return new DefaultTreeExpander(fileSystemTree.get().getTree());

//			if (dataId.equals(PlatformDataKeys.EDITOR.getName())) { // TODO needed by create gist action but affects popup; delete this
//				VirtualFile selectedFile = fileSystemTree.get().getSelectedFile();
//				if (selectedFile != null) {
//					EditorFactory editorFactory = EditorFactory.getInstance();
//					if (editorFactory != null) {
//						return editorFactory.createEditor(editorFactory.createDocument(""));
//					}
//				}
//			}
			return super.getData(dataId);
		}

	}

	private static class MyTree extends Tree implements TypeSafeDataProvider {
		private final Project project;

		private MyTree(Project project) {
			this.project = project;
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


	private static class AddNewPluginAction extends AnAction {
		private static final Logger LOG = Logger.getInstance(AddNewPluginAction.class);

		public AddNewPluginAction() {
			super("New plugin");
		}

		@Override public void actionPerformed(AnActionEvent event) {
			String newPluginId = Messages.showInputDialog(event.getProject(), "Enter new plugin name:", "New Plugin", null);

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
					Notificator.getInstance(project).notifyError(
							"Error adding plugin \"" + newPluginId + "\" to " + EvalComponent.pluginsRootPath(),
							"Add Plugin"
					);
				}
				LOG.error(e);
			}

			reloadAllToolWindows();
		}
	}

	private static class AddExamplePluginAction extends AnAction {
		private static final Logger LOG = Logger.getInstance(AddExamplePluginAction.class);

		private final String pluginPath;
		private final List<String> sampleFiles;
		private final String pluginId;

		public AddExamplePluginAction(String pluginPath, List<String> sampleFiles) {
			this.pluginPath = pluginPath;
			this.sampleFiles = sampleFiles;
			this.pluginId = extractIdFrom(pluginPath);

			getTemplatePresentation().setText(pluginId);
		}

		@Override public void actionPerformed(AnActionEvent event) {
			for (String file : sampleFiles) {
				try {

					String text = EvalComponent.readSampleScriptFile(pluginPath, file);
					FileUtil.writeToFile(new File(EvalComponent.pluginsRootPath() + "/" + pluginId + "/" + file), text);

				} catch (IOException e) {
					logException(e, event);
				}
			}

			reloadAllToolWindows();
		}

		@Override public void update(AnActionEvent event) {
			event.getPresentation().setEnabled(!pluginExists(pluginId));
		}

		private static String extractIdFrom(String pluginPath) {
			String[] split = pluginPath.split("/");
			return split[split.length - 1];
		}

		private void logException(IOException e, AnActionEvent event) {
			Project project = event.getProject();
			if (project != null) {
				Notificator.getInstance(project).notifyError(
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
			super("Plugin from disk");
		}

		@Override public void actionPerformed(AnActionEvent event) {
			FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, true, false);
			descriptor.setRoots(getFileSystemRoots());
			VirtualFile virtualFile = FileChooser.chooseFile(descriptor, null, null);
			if (virtualFile == null) return;

			if (EvalComponent.isInvalidPluginFolder(virtualFile) &&
					userDoesNotWantToAddFolder(virtualFile, event.getProject())) return;

			File fromDir = new File(virtualFile.getPath());
			File toDir = new File(EvalComponent.pluginsRootPath() + "/" + fromDir.getName());
			try {
				FileUtil.createDirectory(toDir);
				FileUtil.copyDirContent(fromDir, toDir);
			} catch (IOException e) {
				Project project = event.getProject();
				if (project != null) {
					Notificator.getInstance(project).notifyError(
							"Error adding plugin \"" + fromDir.getName() + "\" to " + EvalComponent.pluginsRootPath(),
							"Add Plugin"
					);
				}
				LOG.error(e);
			}

			reloadAllToolWindows();
		}

		private static VirtualFile[] getFileSystemRoots() {
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
			return VfsUtil.toVirtualFileArray(roots);
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

	private static class DeletePluginAction extends AnAction {
		private final PluginToolWindow pluginsToolWindow;
		private final Ref<FileSystemTree> fileSystemTree;

		public DeletePluginAction(PluginToolWindow pluginsToolWindow, Ref<FileSystemTree> fileSystemTree) {
			super("Delete plugin", "Delete plugin", Util.DELETE_PLUGIN_ICON);
			this.pluginsToolWindow = pluginsToolWindow;
			this.fileSystemTree = fileSystemTree;
		}

		@Override public void actionPerformed(AnActionEvent event) {
			List<String> pluginIds = pluginsToolWindow.selectedPluginIds();

			if (userDoesNotWantToRemovePlugins(pluginIds, event.getProject())) return;

			for (VirtualFile virtualFile : fileSystemTree.get().getSelectedFiles()) {
				String pluginPath = virtualFile.getPath();
				FileUtil.delete(new File(pluginPath));
			}

			reloadAllToolWindows();
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

	private static class RefreshPluginListAction extends AnAction {

		public RefreshPluginListAction() {
			super("Refresh plugin list", "Refresh plugin list", Util.REFRESH_PLUGIN_LIST_ICON);
		}

		@Override public void actionPerformed(AnActionEvent e) {
			VirtualFile pluginsRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + EvalComponent.pluginsRootPath());
			if (pluginsRoot == null) return;

			RefreshQueue.getInstance().refresh(true, true, new Runnable() {
				@Override public void run() {
					reloadAllToolWindows();
				}
			}, pluginsRoot);
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
