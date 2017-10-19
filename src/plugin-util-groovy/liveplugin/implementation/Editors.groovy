package liveplugin.implementation
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import static liveplugin.implementation.Misc.newDisposable

class Editors {
	@Nullable static Editor currentEditorIn(@NotNull Project project) {
		((FileEditorManagerEx) FileEditorManagerEx.getInstance(project)).selectedTextEditor
	}

	@NotNull static Editor anotherOpenEditorIn(@NotNull Project project) {
		((FileEditorManagerEx) FileEditorManagerEx.getInstance(project)).with {
			if (selectedTextEditor == null) throw new IllegalStateException("There are no open editors in " + project.name)
			def editors = selectedEditors
					.findAll{it instanceof TextEditor}
					.collect{(Editor) it.editor}
					.findAll{it != selectedTextEditor}
			if (editors.size() == 0) throw new IllegalStateException("There is only one open editor in " + project.name)
			if (editors.size() > 1) throw new IllegalStateException("There are more than two open editors in " + project.name)
			editors.first()
		}
	}

	@Nullable static VirtualFile openUrlInEditor(String fileUrl, Project project) {
		// note that it has to be refreshAndFindFileByUrl (not just findFileByUrl) otherwise VirtualFile might be null
		def virtualFile = VirtualFileManager.instance.refreshAndFindFileByUrl(fileUrl)
		if (virtualFile == null) return null
		FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
		virtualFile
	}

	static registerEditorListener(Disposable disposable, FileEditorManagerListener listener) {
		Projects.registerProjectListener(disposable) { Project project ->
			registerEditorListener(project, newDisposable([disposable, project]), listener)
		}
	}

	static registerEditorListener(Project project, Disposable disposable, FileEditorManagerListener listener) {
		project.messageBus
				.connect(disposable)
				.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener)
	}
}
