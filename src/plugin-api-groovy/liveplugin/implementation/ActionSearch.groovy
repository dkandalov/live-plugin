package liveplugin.implementation

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import org.jetbrains.annotations.Nullable

class ActionSearch {
	static Collection<AnAction> findAllActions(String s) {
		s = s.toLowerCase().trim()
		def matches = { AnAction action ->
			action.class.simpleName.toLowerCase().contains(s) ||
			action.templatePresentation?.text?.toLowerCase()?.contains(s)
		}
		allActionIds()
			.collect{ String id ->
				def action = actionById(id)
				id.toLowerCase().contains(s) || matches(action) ? action : null
			}
			.findAll{ it != null }
	}

	static Collection<AnAction> allActions() {
		allActionIds().collect{ actionById(it) }
	}

	static Collection<String> allActionIds() {
		ActionManager.instance.getActionIdList("")
	}

	@Nullable static AnAction actionById(String id) {
		ActionManager.instance.getAction(id)
	}
}
