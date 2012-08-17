package ru.intellijeval.toolwindow;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlighingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
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
import ru.intellijeval.*;
import ru.intellijeval.toolwindow.fileChooser.FileChooser;
import ru.intellijeval.toolwindow.fileChooser.FileChooserDescriptor;
import ru.intellijeval.toolwindow.fileChooser.FileSystemTree;
import ru.intellijeval.toolwindow.fileChooser.ex.FileChooserKeys;
import ru.intellijeval.toolwindow.fileChooser.ex.FileNodeDescriptor;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: dima
 * Date: 11/08/2012
 */
public class PluginsToolWindow {
	private static final String TOOL_WINDOW_ID = "Plugins";

	private Ref<FileSystemTree> myFsTreeRef = Ref.create();
	private SimpleToolWindowPanel panel;

	public static PluginsToolWindow getInstance(Project project) {
		// TODO that's wrong
		return (PluginsToolWindow) ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
	}

	public PluginsToolWindow init() {
		ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerListener() {
			@Override
			public void projectOpened(Project project) {
				registerWindowFor(project);
			}

			@Override
			public void projectClosed(Project project) {
				unregisterWindowFrom(project);
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

	private void registerWindowFor(Project project) {
		ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
		ToolWindow toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT);
		toolWindow.setIcon(Util.PLUGIN_ICON);

		toolWindow.getContentManager().addContent(createContent(project));
	}

	private void unregisterWindowFrom(Project project) {
		ToolWindowManager.getInstance(project).unregisterToolWindow(TOOL_WINDOW_ID);
	}

	private Content createContent(Project project) {
		FileSystemTree fsTree = createFsTree(project);
		myFsTreeRef = Ref.create(fsTree);

		installPopupInto(fsTree);

		JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(fsTree.getTree());
		panel = new MySimpleToolWindowPanel(true, myFsTreeRef).setProvideQuickActions(false);
		panel.add(scrollPane);
		panel.setToolbar(createToolBar());
		return ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
	}

	private void reloadPluginRoots(Project project) {
		FileSystemTree fsTree = createFsTree(project);
		myFsTreeRef.set(fsTree);

		installPopupInto(fsTree);

		JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFsTreeRef.get().getTree());
		panel.remove(0);
		panel.add(scrollPane, 0);
	}

	private static void installPopupInto(FileSystemTree fsTree) {
		AnAction action = new NewElementPopupAction();
		KeyboardShortcut keyboardShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke("ctrl N"), null);
		action.registerCustomShortcutSet(new CustomShortcutSet(keyboardShortcut), fsTree.getTree()); // TODO use generic shortcut?
		CustomizationUtil.installPopupHandler(fsTree.getTree(), "InetlliJEval.Popup", ActionPlaces.UNKNOWN);
	}

	private FileSystemTree createFsTree(Project project) {
		Ref<FileSystemTree> myFsTreeRef = Ref.create();
		MyTree myTree = new MyTree(project);
		myTree.setRootVisible(false);

		// handlers must be installed before adding myTree to FileSystemTreeImpl
		EditSourceOnDoubleClickHandler.install(myTree, new DisableHighlightingRunnable(project, myFsTreeRef));
		EditSourceOnEnterKeyHandler.install(myTree, new DisableHighlightingRunnable(project, myFsTreeRef)); // TODO doesn't work

		FileSystemTree result = new PluginsFileSystemTree(project, createDescriptor(), myTree, null, null, null);
		myFsTreeRef.set(result);
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

		actionGroup.add(createAddActionsGroup());
		actionGroup.add(new DeletePluginAction(this, myFsTreeRef));
		actionGroup.addSeparator();
		actionGroup.add(new EvaluatePluginAction());
		actionGroup.add(new EvaluateAllPluginsAction());

		// TODO expand / collapse (all) actions

		toolBarPanel.add(ActionManager.getInstance().createActionToolbar(TOOL_WINDOW_ID, actionGroup, true).getComponent());
		return toolBarPanel;
	}

	private ActionGroup createAddActionsGroup() {
		DefaultActionGroup actionGroup = new DefaultActionGroup("Add Plugin", true);
		actionGroup.getTemplatePresentation().setIcon(AllIcons.General.Add);
		actionGroup.add(new AddNewPluginAction(this));
		actionGroup.add(new AddPluginAction(this));
		return actionGroup;
	}

	public List<String> selectedPluginIds() {
		List<VirtualFile> rootFiles = findFilesMatchingPluginRoots(myFsTreeRef.get().getSelectedFiles());
		return ContainerUtil.map(rootFiles, new Function<VirtualFile, String>() {
			@Override public String fun(VirtualFile virtualFile) {
				return virtualFile.getName();
			}
		});
	}

	private static List<VirtualFile> findFilesMatchingPluginRoots(VirtualFile[] files) {
		return ContainerUtil.filter(files, new Condition<VirtualFile>() {
			@Override public boolean value(VirtualFile file) {
				return EvalComponent.pluginToPathMap().values().contains(file.getPath());
			}
		});
	}

	private static class MySimpleToolWindowPanel extends SimpleToolWindowPanel {
		private final Ref<FileSystemTree> fileSystemTree;

		public MySimpleToolWindowPanel(boolean vertical, Ref<FileSystemTree> fileSystemTree) {
			super(vertical);
			this.fileSystemTree = fileSystemTree;
		}

		@Override public Object getData(@NonNls String dataId) {
			if (dataId.equals(FileSystemTree.DATA_KEY.getName())) return fileSystemTree.get();
			if (dataId.equals(FileChooserKeys.NEW_FILE_TYPE.getName())) return FileTypes.PLAIN_TEXT;
			if (dataId.equals(FileChooserKeys.DELETE_ACTION_AVAILABLE.getName())) return true;
			if (dataId.equals(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName())) return fileSystemTree.get().getSelectedFiles();
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
		private final PluginsToolWindow pluginsToolWindow;

		public AddNewPluginAction(PluginsToolWindow pluginsToolWindow) {
			super("Add new plugin");
			this.pluginsToolWindow = pluginsToolWindow;
		}

		@Override public void actionPerformed(AnActionEvent event) {
			String newPluginName = Messages.showInputDialog(event.getProject(), "Enter new plugin name:", "New Plugin", null);

			if (pluginExists(newPluginName)) {
				Messages.showErrorDialog(event.getProject(), "Plugin \"" + newPluginName + "\" already exists.", "New Plugin");
				return;
			}

			try {
				String text = EvalComponent.defaultPluginScript();
				FileUtil.writeToFile(new File(EvalComponent.pluginsRootPath() + newPluginName + "/" + EvalComponent.MAIN_SCRIPT), text);
			} catch (IOException e) {
				e.printStackTrace(); // TODO
			}

			// TODO open plugin.groovy?

			pluginsToolWindow.reloadPluginRoots(event.getProject());
		}

		private boolean pluginExists(String newPluginName) {
			return EvalComponent.pluginToPathMap().keySet().contains(newPluginName);
		}
	}

	private static class AddPluginAction extends AnAction {
		private final PluginsToolWindow pluginsToolWindow;

		public AddPluginAction(PluginsToolWindow pluginsToolWindow) {
			super("Add plugin from disk");
			this.pluginsToolWindow = pluginsToolWindow;
		}

		@Override public void actionPerformed(AnActionEvent event) {
			VirtualFile virtualFile = FileChooser.chooseFile(new FileChooserDescriptor(true, true, true, true, true, false), null, null);
			if (virtualFile == null) return;

			if (EvalComponent.isInvalidPluginFolder(virtualFile) &&
					userDoesNotWantToAddFolder(virtualFile, event.getProject())) return;

			try {
				File fromDir = new File(virtualFile.getPath());
				File toDir = new File(EvalComponent.pluginsRootPath() + fromDir.getName());
				FileUtil.createDirectory(toDir);
				FileUtil.copyDirContent(fromDir, toDir);
			} catch (IOException e) {
				e.printStackTrace(); // TODO
			}

			// TODO eval plugin?

			pluginsToolWindow.reloadPluginRoots(event.getData(PlatformDataKeys.PROJECT));
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
		private final PluginsToolWindow pluginsToolWindow;
		private final Ref<FileSystemTree> fileSystemTree;

		private DeletePluginAction(PluginsToolWindow pluginsToolWindow, Ref<FileSystemTree> fileSystemTree) {
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
			pluginsToolWindow.reloadPluginRoots(event.getData(PlatformDataKeys.PROJECT));
		}

		@Override public void update(AnActionEvent e) {
			Collection<String> pluginPaths = EvalComponent.pluginToPathMap().values();
			boolean anyPluginPathSelected = false;
			for (VirtualFile virtualFile : fileSystemTree.get().getSelectedFiles()) {
				if (pluginPaths.contains(virtualFile.getPath())) {
					anyPluginPathSelected = true;
					break;
				}
			}
			e.getPresentation().setEnabled(anyPluginPathSelected);
		}

		private static boolean userDoesNotWantToRemovePlugins(List<String> pluginNames, Project project) {
			String message;
			if (pluginNames.size() == 1) {
				message = "Do you want to delete plugin \"" + pluginNames.get(0) + "\"?";
			} else if (pluginNames.size() == 2) {
				message = "Do you want to delete plugin \"" + pluginNames.get(0) + "\" and \"" + pluginNames.get(1) + "\"?";
			} else {
				message = "Do you want to delete plugins \"" + StringUtil.join(pluginNames, ", ") + "\"?";
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
