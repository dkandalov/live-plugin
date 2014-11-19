package liveplugin.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import liveplugin.pluginrunner.GroovyPluginRunner;
import org.jetbrains.annotations.NotNull;

import static liveplugin.pluginrunner.GroovyPluginRunner.GROOVY_ADD_TO_CLASSPATH_KEYWORD;

public class AddToClassPathGroovyIntention implements IntentionAction {
	private static final String addToClasspathLiteral = GROOVY_ADD_TO_CLASSPATH_KEYWORD + "\n";

	@Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
		return isGroovyPluginScript(file) && linesAboveCurrentAreImportOrPackage(editor);
	}

	@Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
		final Document document = editor.getDocument();
		final CaretModel caretModel = editor.getCaretModel();

		String modificationName = "Inserted 'add-to-classpath'";
		CommandProcessor.getInstance().executeCommand(project, new Runnable() {
			@Override public void run() {
				int lineNumber = document.getLineNumber(caretModel.getOffset());
				int lineStartOffset = document.getLineStartOffset(lineNumber);
				document.insertString(lineStartOffset, addToClasspathLiteral);
				caretModel.moveToOffset(lineStartOffset + addToClasspathLiteral.length() - 1);
			}
		}, modificationName, "LivePlugin", UndoConfirmationPolicy.DEFAULT, document);
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
		return virtualFile != null && virtualFile.getName().equals(GroovyPluginRunner.MAIN_SCRIPT);
	}

	private static String lineTextIn(Document document, int lineNumber) {
		int lineStartOffset = document.getLineStartOffset(lineNumber);
		int lineEndOffset = document.getLineEndOffset(lineNumber);
		return document.getText(new TextRange(lineStartOffset, lineEndOffset));
	}

	@Override public boolean startInWriteAction() {
		return true;
	}

	@NotNull @Override public String getText() {
		return "Insert 'add-to-classpath' directive";
	}

	@NotNull @Override public String getFamilyName() {
		return "Insert 'add-to-classpath'";
	}
}
