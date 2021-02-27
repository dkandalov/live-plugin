import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.annotations.NotNull

import static liveplugin.PluginUtil.registerIntention
import static liveplugin.PluginUtil.show

def javaIsSupportedByIde = Language.findLanguageByID("JAVA") != null
if (javaIsSupportedByIde) {
	registerIntention(pluginDisposable, new JavaFinalFieldIntention())
	if (!isIdeStartup) show("Reloaded 'Finalize Java Fields' plugin")
} else {
	if (!isIdeStartup) show("IDE doesn't support Java")
}

/**
 * See also in IntelliJ sources com.siyeh.ig.fixes.MakeFieldFinalFix.
 */
class JavaFinalFieldIntention extends PsiElementBaseIntentionAction {
	private boolean isFinal

	@Override boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
		if (!isInJavaFile(element)) return false
		def field = findParent(PsiField, element)
		if (field == null) false

		isFinal = field.hasModifierProperty("final")
		text = isFinal ? "Make 'non-final'" : "Make 'final'"
		true
	}

	@Override void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement psiElement) {
		def modifiers = findParent(PsiField, psiElement)?.modifierList
		if (modifiers == null) return
		modifiers.setModifierProperty("final", !isFinal)
	}

	@Override String getFamilyName() {
		"Make Java Field (Non-)Final"
	}

	private static <T> T findParent(Class<T> aClass, PsiElement element) {
		if (element == null) null
		else if (aClass.isAssignableFrom(element.class)) element as T
		else findParent(aClass, element.parent)
	}

	private static isInJavaFile(PsiElement element) {
		def fileType = element.containingFile.fileType
		fileType instanceof LanguageFileType && fileType.language.ID == "JAVA"
	}
}

