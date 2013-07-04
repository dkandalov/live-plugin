package liveplugin.toolwindow;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilBase;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class DisableHighlightingRunnable implements Runnable {
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
			skipHighlighting_IJ12_IJ13_compatibility_workaround(root);
		}
		DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(psiFile.getProject());
		analyzer.restart();
	}

	private static void skipHighlighting_IJ12_IJ13_compatibility_workaround(PsiElement psiElement) {
		try {
			Class enumClass;
			try {
				enumClass = Class.forName("com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting");
			} catch (ClassNotFoundException e) {
				enumClass = Class.forName("com.intellij.codeInsight.daemon.impl.analysis.FileHighlighingSetting");
			}
			Enum skipHighlighting = Enum.valueOf(enumClass, "SKIP_HIGHLIGHTING");
			Method method = HighlightLevelUtil.class.getMethod("forceRootHighlighting", PsiElement.class, enumClass);
			method.invoke(null, psiElement, skipHighlighting);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
