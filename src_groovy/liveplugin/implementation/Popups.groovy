package liveplugin.implementation

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nullable

import javax.swing.JPanel

class Popups {

	static showPopupMenu(Map menuDescription, String popupTitle = "", @Nullable DataContext dataContext = null) {
		if (dataContext == null) {
			// this is to prevent createActionGroupPopup() from crashing without context component
			def dummyComponent = new JPanel()
			dataContext = new MapDataContext().put(PlatformDataKeys.CONTEXT_COMPONENT.name, dummyComponent)
		}
		JBPopupFactory.instance.createActionGroupPopup(
			popupTitle,
			createNestedActionGroup(menuDescription),
			dataContext,
			JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
			true
		).showInFocusCenter()
	}

	@Contract(pure = true)
	static ActionGroup createNestedActionGroup(Map description, actionGroup = new DefaultActionGroup()) {
		description.each { entry ->
			if (entry.value instanceof Closure) {
				actionGroup.add(new AnAction(entry.key.toString()) {
					@Override void actionPerformed(AnActionEvent event) {
						entry.value.call([key: entry.key, event: event])
					}
				})
			} else if (entry.value instanceof Map) {
				Map subMenuDescription = entry.value as Map
				def actionGroupName = entry.key.toString()
				def isPopup = true
				actionGroup.add(createNestedActionGroup(subMenuDescription, new DefaultActionGroup(actionGroupName, isPopup)))
			} else if (entry.value instanceof AnAction) {
				actionGroup.add(entry.value)
			}
		}
		actionGroup
	}
}
