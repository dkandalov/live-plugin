import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory

import static liveplugin.PluginUtil.*

ActionGroup createActions(menuDescription, actionGroup = new DefaultActionGroup()) {
	menuDescription.each { entry ->
		if (entry.value instanceof String) {
			actionGroup.add(new AnAction(entry.key as String) {
				@Override void actionPerformed(AnActionEvent ignored) {
					show(entry.value)
				}
			})
		} else {
			def actionGroupName = entry.key.toString()
			def isPopup = true
			def subMenuDescription = entry.value
			actionGroup.add(createActions(subMenuDescription, new DefaultActionGroup(actionGroupName, isPopup)))
		}
	}
	actionGroup
}


registerAction("helloPopupAction", "ctrl alt shift P") { AnActionEvent event ->
	def popupMenuDescription = [
			"World 1": [
					"sub-world 11": "Hello sub-world 11!!",
					"sub-world 12": "hello sub-world 12",
			],
			"World 2": [
					"sub-world 21": "sub-world 21 hello",
					"sub-world 22": "sub-world hello 22",
			],
			"World 3": "Hey world 3!"
	]
	def popupTitle = "Say hello to..."
	JBPopupFactory.instance.createActionGroupPopup(
			popupTitle,
			createActions(popupMenuDescription),
			event.dataContext,
			JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
			true
	).showCenteredInCurrentWindow(event.project)
}
show("Loaded 'helloPopupAction'<br/>Use ctrl+alt+shift+P to run it")
