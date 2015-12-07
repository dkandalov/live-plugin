package liveplugin.implementation
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.NotNull

import java.lang.reflect.Method

class ActionWrapper {
	private static final Logger log = Logger.getInstance(ActionWrapper.class)


	static AnAction wrapAction(String actionId, List<String> actionGroups = [], Closure callback) {
		AnAction action = ActionManager.instance.getAction(actionId)
		if (action == null) throw new IllegalStateException("Couldn't wrap action '" + actionId + "' because it was not found")

		if (isWrapped(action)) {
			unwrapAction(actionId, actionGroups)
		}
		doWrapAction(actionId, actionGroups, callback)
	}

	static AnAction doWrapAction(String actionId, List<String> actionGroups = [], Closure callback) {
		AnAction action = ActionManager.instance.getAction(actionId)
		if (action == null) throw new IllegalStateException("Couldn't wrap action '" + actionId + "' because it was not found")

		AnAction wrapperAction
		if (action instanceof EditorAction) {
			wrapperAction = new WrappedEditorAction(new WrappedEditorActionHandler(action, callback as Listener)).init(action)
		} else if (action instanceof DumbAware || action instanceof PossiblyDumbAware) {
			wrapperAction = new WrappedActionPossiblyDumbAware(action, callback as Listener)
		} else {
			wrapperAction = new WrappedAction(action, callback as Listener)
		}
		wrapperAction.getTemplatePresentation().setText(action.getTemplatePresentation().getText())
		wrapperAction.getTemplatePresentation().setIcon(action.getTemplatePresentation().getIcon())
		wrapperAction.getTemplatePresentation().setHoveredIcon(action.getTemplatePresentation().getHoveredIcon())
		wrapperAction.getTemplatePresentation().setDescription(action.getTemplatePresentation().getDescription())
		wrapperAction.copyShortcutFrom(action)

		ActionManager.instance.unregisterAction(actionId)
		ActionManager.instance.registerAction(actionId, wrapperAction)
		log.info("Wrapped action " + actionId)

		actionGroups.each { replaceActionInGroup(it, actionId, wrapperAction) }

		wrapperAction
	}

	static AnAction unwrapAction(String actionId, List<String> actionGroups = []) {
		ActionManager actionManager = ActionManager.instance
		AnAction action = actionManager.getAction(actionId)
		if (action == null) throw new IllegalStateException("Couldn't unwrap action '" + actionId + "' because it was not found")
		if (!isWrapped(action)) throw new IllegalStateException("Action '" + actionId + "' is not wrapped")

		def originalAction = originalActionOf(action)
		actionManager.unregisterAction(actionId)
		actionManager.registerAction(actionId, originalAction)
		log.info("Unwrapped action '" + actionId + "'")

		actionGroups.each { replaceActionInGroup(it, actionId, originalAction) }

		originalAction
	}

	static AnAction doUnwrapAction(String actionId, List<String> actionGroups = []) {
		def lastAction = unwrapAction(actionId)
		def action = unwrapAction(actionId)
		while (lastAction != action) {
			lastAction = action
			action = unwrapAction(actionId, actionGroups)
		}
		action
	}


	private static AnAction originalActionOf(AnAction wrappedAction) {
		def lastException = null
		for (Method method : wrappedAction.class.methods) {
			if (method.name.equals(DelegatesToAction.methodName)) {
				try {
					method.accessible = true
					return (AnAction) method.invoke(wrappedAction)
				} catch (Exception e) {
					lastException = e
				}
			}
		}
		if (lastException != null) throw lastException
		else throw new IllegalStateException()
	}

	private static boolean isWrapped(AnAction wrappedAction) {
		for (Class<?> aClass : wrappedAction.class.interfaces) {
			if (aClass.canonicalName == DelegatesToAction.class.canonicalName) {
				return true
			}
		}
		false
	}

	private static replaceActionInGroup(String actionGroupId, String actionId, AnAction newAction) {
		def group = ActionManager.instance.getAction(actionGroupId)
		if (!group instanceof DefaultActionGroup) {
			throw new IllegalStateException("'${actionGroupId}' has type '${actionGroupId.class}' which is not supported")
		}

		Misc.accessField(group, "mySortedChildren") { List<AnAction> actions ->
			def actionIndex = actions.findIndexOf {
				if (it == null) return
				def id = ActionManager.instance.getId(it)
				id == actionId
			}
			if (actionIndex >= 0) {
				actions.set(actionIndex, newAction)
			}
		}
		Misc.accessField(group, "myPairs") { List<Pair<AnAction, Constraints>> pairs ->
			def pairIndex = pairs.findIndexOf {
				if (it == null || it.first == null) return
				def id = ActionManager.instance.getId(it.first)
				id == actionId
			}
			if (pairIndex >= 0) {
				def oldPair = pairs.get(pairIndex)
				pairs.set(pairIndex, new Pair(newAction, oldPair.second))
			}
		}
	}


	private static interface Listener {
		void onAction(AnActionEvent event, Closure originalActionCallback)
	}


	private static interface DelegatesToAction {
		static final methodName = "originalAction"

		@SuppressWarnings([ "GroovyUnusedDeclaration" ]) // used via reflection
		AnAction originalAction()
	}


	private static class WrappedAction extends AnAction implements DelegatesToAction {
		private final Listener listener
		private final AnAction originalAction

		WrappedAction(AnAction originalAction, Listener listener) {
			this.listener = listener
			this.originalAction = originalAction
		}

		@Override void actionPerformed(@NotNull AnActionEvent event) {
			listener.onAction(event) { AnActionEvent event2 ->
				if (event2 != null) event = event2
				originalAction.actionPerformed(event)
			}
		}

		@Override void update(@NotNull AnActionEvent event) {
			originalAction.update(event)
		}

		@Override AnAction originalAction() {
			originalAction
		}
	}


	private static class WrappedActionPossiblyDumbAware extends WrappedAction implements PossiblyDumbAware, DelegatesToAction {
		private final AnAction originalAction

		WrappedActionPossiblyDumbAware(AnAction originalAction, Listener listener) {
			super(originalAction, listener)
			this.originalAction = originalAction
		}

		@Override boolean isDumbAware() {
			if (originalAction instanceof DumbAware) true
			else (originalAction as PossiblyDumbAware).dumbAware
		}
	}


	private static class WrappedEditorAction extends EditorAction implements DelegatesToAction {
		private EditorAction originalAction
		private final WrappedEditorActionHandler editorActionHandler

		protected WrappedEditorAction(WrappedEditorActionHandler editorActionHandler) {
			super(editorActionHandler)
			this.editorActionHandler = editorActionHandler
		}

		// use init() method to avoid anything complex in constructor,
		// otherwise groovy fails with obscure "java.lang.VerifyError ... Expecting to find object/array on stack"
		WrappedEditorAction init(EditorAction originalAction) {
			this.originalAction = originalAction

			// Force loading action handler.
			// (It seems that handlers cannot be re-wrapped without calling
			// com.intellij.openapi.editor.actionSystem.EditorAction#ensureHandlersLoaded.)
			setupHandler(getHandler())
			this
		}

		@Override void beforeActionPerformedUpdate(@NotNull AnActionEvent event) {
			super.beforeActionPerformedUpdate(event)
			editorActionHandler.event = event
		}

		@Override void update(@NotNull AnActionEvent event) {
			originalAction.update(event)
		}

		@Override AnAction originalAction() {
			originalAction
		}
	}

	private static class WrappedEditorActionHandler extends EditorActionHandler {
		private final EditorAction originalAction
		private final Listener listener
		AnActionEvent event

		WrappedEditorActionHandler(EditorAction originalAction, Listener listener) {
			this.originalAction = originalAction
			this.listener = listener
		}

		@Override protected void doExecute(Editor editor, Caret caret, DataContext dataContext) {
			listener.onAction(event) { AnActionEvent event ->
				if (event != null) dataContext = event.dataContext
				originalAction.handler.doExecute(editor, caret, dataContext)
			}
		}

		@Override protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
		    originalAction.handler.isEnabled(editor, caret, dataContext)
		}

		@Override DocCommandGroupId getCommandGroupId(Editor editor) {
			originalAction.handler.getCommandGroupId(editor)
		}

		@Override boolean runForAllCarets() {
			originalAction.handler.runForAllCarets()
		}

		@Override boolean executeInCommand(Editor editor, DataContext dataContext) {
			originalAction.handler.executeInCommand(editor, dataContext)
		}
	}
}
