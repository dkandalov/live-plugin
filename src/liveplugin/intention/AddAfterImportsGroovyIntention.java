package liveplugin.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import liveplugin.pluginrunner.GroovyPluginRunner;
import org.jetbrains.annotations.NotNull;

public class AddAfterImportsGroovyIntention implements IntentionAction, DumbAware {
	private final String stringToInsert;
	private final String modificationName;
	private final String popupText;

	public AddAfterImportsGroovyIntention(String stringToInsert, String modificationName, String popupText) {
		this.stringToInsert = stringToInsert;
		this.modificationName = modificationName;
		this.popupText = popupText;
	}

	private static boolean linesAboveCurrentAreImportOrPackage(Editor editor) {
		Document document = editor.getDocument();
		int offset = editor.getCaretModel().getOffset();

		int lineNumberBeforeCurrent = document.getLineNumber(offset);

		for (int lineNumber = 0; lineNumber < lineNumberBeforeCurrent; lineNumber++) {
			String line = lineTextIn(document, lineNumber).trim();
			if (!line.isEmpty() && !line.startsWith("//") &&!line.startsWith("/*") &&
				!line.startsWith("import") && !line.startsWith("package")) {
				return false;
			}
		}
		return true;
	}

	private static boolean isGroovyPluginScript(PsiFile file) {
		VirtualFile virtualFile = file.getVirtualFile();
		return virtualFile != null && (
				virtualFile.getName().equals(GroovyPluginRunner.mainScript) ||
				virtualFile.getName().equals(GroovyPluginRunner.testScript)
		);
	}

	private static String lineTextIn(Document document, int lineNumber) {
		int lineStartOffset = document.getLineStartOffset(lineNumber);
		int lineEndOffset = document.getLineEndOffset(lineNumber);
		return document.getText(new TextRange(lineStartOffset, lineEndOffset));
	}

	@Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
		return isGroovyPluginScript(file) && linesAboveCurrentAreImportOrPackage(editor);
	}

	@Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
		final Document document = editor.getDocument();
		final CaretModel caretModel = editor.getCaretModel();

		CommandProcessor.getInstance().executeCommand(project, () -> {
			int lineNumber = document.getLineNumber(caretModel.getOffset());
			int lineStartOffset = document.getLineStartOffset(lineNumber);
			document.insertString(lineStartOffset, stringToInsert);
			caretModel.moveToOffset(lineStartOffset + stringToInsert.length() - 1);
		}, modificationName, "LivePlugin", UndoConfirmationPolicy.DEFAULT, document);
	}

	@Override public boolean startInWriteAction() {
		return true;
	}

	@NotNull @Override public String getText() {
		return popupText;
	}

	@NotNull @Override public String getFamilyName() {
		return modificationName;
	}
}
