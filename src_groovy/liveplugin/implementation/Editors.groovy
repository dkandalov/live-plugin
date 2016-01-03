package liveplugin.implementation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

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
}
