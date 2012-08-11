package ru.intellijeval;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
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

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: dima
 * Date: 11/08/2012
 */
public class PluginsToolWindow {

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
		ToolWindow myToolWindow = toolWindowManager.registerToolWindow("Plugins", false, ToolWindowAnchor.RIGHT);

		myToolWindow.getContentManager().addContent(createContent(project));
	}

	private Content createContent(Project project) {
		FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, true);
		descriptor.setShowFileSystemRoots(true);
		descriptor.setIsTreeRootVisible(false);

		List<VirtualFile> roots = new ArrayList<VirtualFile>();
		EvalData evalData = EvalData.getInstance();
		for (String pluginPath : evalData.getPluginPaths().values()) {
			VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://" + pluginPath);
			roots.add(virtualFile);
		}
		descriptor.setRoots(roots);

		MyTree myTree = new MyTree(project);
		EditSourceOnDoubleClickHandler.install(myTree); // handlers must be installed before adding myTree to FileSystemTreeImpl
		EditSourceOnEnterKeyHandler.install(myTree);

		FileSystemTreeImpl myFsTree = new FileSystemTreeImpl(project, descriptor, myTree, null, null, null);

		JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFsTree.getTree());
		SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true).setProvideQuickActions(false);
		panel.add(scrollPane);
		return ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
	}

	private void unregisterWindowFrom(Project project) {
		ToolWindowManager.getInstance(project).unregisterToolWindow("Plugins");
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
}
