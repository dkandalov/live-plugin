package ru.intellijeval.toolwindow;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
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
import com.intellij.util.ui.tree.TreeUtil;
import ru.intellijeval.EvalData;
import ru.intellijeval.EvaluateAction;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: dima
 * Date: 11/08/2012
 */
public class PluginsToolWindow {
	private static final ImageIcon ICON = new ImageIcon(PluginsToolWindow.class.getResource("/ru/intellijeval/toolwindow/plugin.png"));
	private static final String TOOL_WINDOW_ID = "Plugins";
	private FileSystemTreeImpl myFsTree;
	private SimpleToolWindowPanel panel;

	public PluginsToolWindow init() {
		ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerListener() {
			@Override
			public void projectOpened(Project project) {
				registerWindowFor(project);
			}

			@Override
			public void projectClosed(Project project) {
				// unregister window in any case
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
		ToolWindow myToolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT);
		myToolWindow.setIcon(ICON);

		myToolWindow.getContentManager().addContent(createContent(project));
	}

	private Content createContent(Project project) {
		myFsTree = createFsTree(project);

		JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFsTree.getTree());
		panel = new SimpleToolWindowPanel(true).setProvideQuickActions(false);
		panel.add(scrollPane);

		panel.setToolbar(createToolBar());

		return ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
	}

	private FileSystemTreeImpl createFsTree(Project project) {
		FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, true);
		descriptor.setShowFileSystemRoots(true);
		descriptor.setIsTreeRootVisible(false);

		List<VirtualFile> roots = currentPluginRoots();
		descriptor.setRoots(roots);

		MyTree myTree = new MyTree(project);
		EditSourceOnDoubleClickHandler.install(myTree); // handlers must be installed before adding myTree to FileSystemTreeImpl
		EditSourceOnEnterKeyHandler.install(myTree);

		return new FileSystemTreeImpl(project, descriptor, myTree, null, null, null);
	}

	private void reloadPluginRoots(Project project) {
		myFsTree = createFsTree(project);
		JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFsTree.getTree());
		panel.remove(0);
		panel.add(scrollPane, 0);
	}

	private List<VirtualFile> currentPluginRoots() {
		List<VirtualFile> roots = new ArrayList<VirtualFile>();
		EvalData evalData = EvalData.getInstance();
		for (String pluginPath : evalData.getPluginPaths().values()) {
			VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://" + pluginPath);
			roots.add(virtualFile);
		}
		return roots;
	}

	private JComponent createToolBar() {
		JPanel toolBarPanel = new JPanel(new GridLayout());
		DefaultActionGroup actionGroup = new DefaultActionGroup();

		actionGroup.add(new AddPluginAction(this));
		actionGroup.add(new RemovePluginAction(this, myFsTree));
		actionGroup.add(new EvaluateAction());

		toolBarPanel.add(ActionManager.getInstance().createActionToolbar(TOOL_WINDOW_ID, actionGroup, true).getComponent());
		return toolBarPanel;
	}

	private void unregisterWindowFrom(Project project) {
		ToolWindowManager.getInstance(project).unregisterToolWindow(TOOL_WINDOW_ID);
	}

	private static class MyTree extends Tree implements TypeSafeDataProvider {
		private final Project project;

		private MyTree(Project project) {
			this.project = project;
		}

		@Override public void calcData(DataKey key, DataSink sink) {
			if (PlatformDataKeys.NAVIGATABLE_ARRAY.equals(key)) {
				List<FileNodeDescriptor> nodeDescriptors = TreeUtil.collectSelectedObjectsOfType(this, FileNodeDescriptor.class);
				List<Navigatable> navigatables = new ArrayList<Navigatable>();
				for (FileNodeDescriptor nodeDescriptor : nodeDescriptors) {
					navigatables.add(new OpenFileDescriptor(project, nodeDescriptor.getElement().getFile()));
				}
				sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, navigatables.toArray(new Navigatable[navigatables.size()]));
			}
		}
	}

	private static class AddPluginAction extends AnAction {
		private static final ImageIcon ICON = new ImageIcon(PluginsToolWindow.class.getResource("/ru/intellijeval/toolwindow/add.png"));
		private final PluginsToolWindow pluginsToolWindow;

		private AddPluginAction(PluginsToolWindow pluginsToolWindow) {
			super(ICON);
			this.pluginsToolWindow = pluginsToolWindow;
		}

		@Override public void actionPerformed(AnActionEvent event) {
			VirtualFile virtualFile = FileChooser.chooseFile(new FileChooserDescriptor(true, true, true, true, true, false), null, null);
			if (virtualFile == null) return;

			// TODO check that it's a valid plugin folder
			// TODO eval plugin?

			EvalData evalData = EvalData.getInstance();
			evalData.getPluginPaths().put(virtualFile.getPath(), virtualFile.getPath());

			pluginsToolWindow.reloadPluginRoots(event.getData(PlatformDataKeys.PROJECT));
		}
	}

	private static class RemovePluginAction extends AnAction {
		private static final ImageIcon ICON = new ImageIcon(PluginsToolWindow.class.getResource("/ru/intellijeval/toolwindow/remove.png"));
		private final PluginsToolWindow pluginsToolWindow;
		private final FileSystemTree fileSystemTree;

		private RemovePluginAction(PluginsToolWindow pluginsToolWindow, FileSystemTree fileSystemTree) {
			super(ICON);
			this.pluginsToolWindow = pluginsToolWindow;
			this.fileSystemTree = fileSystemTree;
		}

		@Override public void actionPerformed(AnActionEvent event) {
			EvalData evalData = EvalData.getInstance();
			for (VirtualFile virtualFile : fileSystemTree.getSelectedFiles()) {
				evalData.getPluginPaths().remove(virtualFile.getPath());
			}
			pluginsToolWindow.reloadPluginRoots(event.getData(PlatformDataKeys.PROJECT));
		}

		@Override public void update(AnActionEvent e) {
			EvalData evalData = EvalData.getInstance();
			boolean anyPluginPathSelected = false;
			for (VirtualFile virtualFile : fileSystemTree.getSelectedFiles()) {
				if (evalData.containsPath(virtualFile.getPath())) {
					anyPluginPathSelected = true;
					break;
				}
			}
			e.getPresentation().setEnabled(anyPluginPathSelected);
		}
	}
}
