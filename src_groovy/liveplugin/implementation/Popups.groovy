package liveplugin.implementation

import com.intellij.ide.util.gotoByName.ChooseByNameBase
import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent
import com.intellij.ide.util.gotoByName.SimpleChooseByNameModel
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.Processor
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

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

	static showPopupSearch(String prompt, Project project, String initialText = "", Collection items, Closure onItemChosen) {
		Closure<Collection> itemProvider = { String pattern, ProgressIndicator cancelled ->
			pattern = "*" + pattern.chars.toList().join("*")
			def matcher = new MinusculeMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE)
			items.findAll{ matcher.matches(it.toString()) }
		}
		showPopupSearch(prompt, project, initialText, itemProvider, onItemChosen)
	}

	static showPopupSearch(String prompt, Project project, String initialText = "",
	                       Closure<Collection> itemProvider, Closure onItemChosen) {
		def model = new SimpleChooseByNameModel(project, prompt, null) {
			@Override ListCellRenderer getListCellRenderer() {
				new ColoredListCellRenderer() {
					@Override protected void customizeCellRenderer(JList list, Object value, int index,
					                                               boolean selected, boolean hasFocus) {
						append(value.toString())
					}
				}
			}
			@Override String getElementName(Object element) {
				element.toString()
			}
			@Override String[] getNames() {
				// never called (can only be called from ChooseByNameBase)
				[].toArray()
			}
			@Override protected Object[] getElementsByName(String name, String pattern) {
				// never called (can only be called from ChooseByNameBase)
				[].toArray()
			}
		}

		def chooseByNameItemProvider = new ChooseByNameItemProvider() {
			@Override boolean filterElements(@NotNull ChooseByNameBase base, @NotNull String pattern, boolean everywhere,
			                                 @NotNull ProgressIndicator cancelled, @NotNull Processor<Object> consumer) {
				def items = itemProvider.call(pattern, cancelled)
				items.each{ consumer.process(it) }
				!items.empty
			}
			@Override List<String> filterNames(@NotNull ChooseByNameBase base, @NotNull String[] names, @NotNull String pattern) {
				// never called (can only be called from ChooseByNameBase)
				names.toList()
			}
		}

		ChooseByNamePopup.createPopup(project, model, chooseByNameItemProvider, initialText).invoke(new ChooseByNamePopupComponent.Callback() {
			@Override void elementChosen(Object element) {
				onItemChosen(element)
			}
		}, ModalityState.NON_MODAL, true)
	}

}
