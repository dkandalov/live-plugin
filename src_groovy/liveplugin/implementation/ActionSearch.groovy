package liveplugin.implementation

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction

class ActionSearch {

	static Collection findAllActions(String s) {
		s = s.toLowerCase().trim()

		allActionIds().findResults{ String id ->
			def action = actionById(id)
			if (id.toLowerCase().contains(s) ||
					action.class.simpleName.toLowerCase().contains(s) ||
					action.templatePresentation?.text?.toLowerCase()?.contains(s)) {
				[id, action]
			} else {
				null
			}
		}
	}

	static Collection<AnAction> allActions() {
		allActionIds().collect{ actionById(it) }
	}

	static Collection<String> allActionIds() {
		ActionManager.instance.getActionIds("")
	}

	static AnAction actionById(String id) {
		ActionManager.instance.getAction(id)
	}
}
