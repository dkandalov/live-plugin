package liveplugin.implementation
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NotNull

import static liveplugin.implementation.GlobalVars.changeGlobalVar

class Intentions {
	static IntentionAction registerIntention(String intentionId, String text,
	                                         String familyName, Closure callback) {
		def intention = new IntentionAction() {
			@Override void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
				callback.call([checkAvailability: false, project: project, editor: editor, file: file])
			}

			@Override boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
				callback.call([checkAvailability: true, project: project, editor: editor, file: file])
			}

			@Override boolean startInWriteAction() { false }
			@Override String getFamilyName() { familyName }
			@Override String getText() { text }
		}
		registerIntention(intentionId, intention)
	}

	static IntentionAction registerIntention(String intentionId, IntentionAction intention) {
		changeGlobalVar(intentionId) { IntentionAction oldIntention ->
			if (oldIntention != null) {
				IntentionManager.instance.unregisterIntention(oldIntention)
			}
			IntentionManager.instance.addAction(intention)
			intention
		}
	}

	static IntentionAction unregisterIntention(String intentionId) {
		changeGlobalVar(intentionId) { IntentionAction oldIntention ->
			if (oldIntention != null) {
				IntentionManager.instance.unregisterIntention(oldIntention)
			}
		}
	}
}
