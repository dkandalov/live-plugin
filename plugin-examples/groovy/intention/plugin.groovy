import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NotNull

import static liveplugin.PluginUtil.registerIntention
import static liveplugin.PluginUtil.show

def javaIsSupportedByIde = Language.findLanguageByID("JAVA") != null
if (!javaIsSupportedByIde) return

registerIntention("MakeFieldFinal", new AddRemoveFinalIntentionAction("Make 'final'", true))
registerIntention("MakeFieldNonFinal", new AddRemoveFinalIntentionAction("Make 'non-final'", false))

if (!isIdeStartup) show("Reloaded 'Finalize Java Fields' plugin")


class AddRemoveFinalIntentionAction extends PsiElementBaseIntentionAction {
	private final String text
	private final boolean addFinal

	AddRemoveFinalIntentionAction(String text, boolean addFinal) {
		this.text = text
		this.addFinal = addFinal
	}

	@Override void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement psiElement) throws IncorrectOperationException {
		def field = findParent(PsiField, psiElement)
		if (field.modifierList.hasModifierProperty("final") != addFinal) {
			field.modifierList.setModifierProperty("final", addFinal)
		}
	}

	@Override boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement psiElement) {
		def fileType = psiElement.containingFile.fileType
		if (!(fileType instanceof LanguageFileType && fileType.language.ID == "JAVA")) return false

		def field = findParent(PsiField, psiElement)
		if (field == null) false
		else field.hasModifierProperty("final") != addFinal
	}

	@Override String getFamilyName() {
		def prefix = addFinal ? "" : "Non"
		"MakeField${prefix}Final"
	}

	@Override String getText() {
		text
	}

	private static <T> T findParent(Class<T> aClass, PsiElement element) {
		if (element == null) null
		else if (aClass.isAssignableFrom(element.class)) element as T
		else findParent(aClass, element.parent)
	}
}

