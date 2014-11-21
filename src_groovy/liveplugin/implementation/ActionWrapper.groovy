package liveplugin.implementation

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.PossiblyDumbAware
import org.jetbrains.annotations.NotNull

import java.lang.reflect.Method

class ActionWrapper {
	private static final Logger LOG = Logger.getInstance(ActionWrapper.class)

	static class Context {
		final AnActionEvent event
		private final Closure originalActionCallback

		Context(AnActionEvent event, Closure originalActionCallback) {
			this.event = event
			this.originalActionCallback = originalActionCallback
		}

		def callOriginalAction() {
			originalActionCallback.call()
		}
	}


	static AnAction wrapAction(String actionId, Closure callback) {
		AnAction action = ActionManager.instance.getAction(actionId)
		if (action == null) {
			LOG.warn("Couldn't wrap action " + actionId + " because it was not found")
			return null
		}
		if (isWrapped(action)) {
			LOG.info("Action " + actionId + " is already wrapped")
			return null
		}
		doWrapAction(actionId, callback)
	}

	static AnAction doWrapAction(String actionId, Closure callback) {
		AnAction action = ActionManager.instance.getAction(actionId)
		if (action == null) {
			LOG.warn("Couldn't wrap action " + actionId + " because it was not found")
			return null
		}

		AnAction newAction
		if (action instanceof EditorAction) {
			newAction = new WrappedEditorAction(callback as Listener, (EditorAction) action)
		} else if (action instanceof DumbAware || action instanceof PossiblyDumbAware) {
			newAction = new WrappedActionPossiblyDumbAware(callback as Listener, action)
		} else {
			newAction = new WrappedAction(callback as Listener, action)
		}
		newAction.getTemplatePresentation().setText(action.getTemplatePresentation().getText())
		newAction.getTemplatePresentation().setIcon(action.getTemplatePresentation().getIcon())
		newAction.getTemplatePresentation().setHoveredIcon(action.getTemplatePresentation().getHoveredIcon())
		newAction.getTemplatePresentation().setDescription(action.getTemplatePresentation().getDescription())
		newAction.copyShortcutFrom(action)

		ActionManager.instance.unregisterAction(actionId)
		ActionManager.instance.registerAction(actionId, newAction)
		LOG.info("Wrapped action " + actionId)

		newAction
	}

	static void unwrapAction(String actionId) {
		ActionManager actionManager = ActionManager.instance
		AnAction action = actionManager.getAction(actionId)
		if (action == null) {
			LOG.warn("Couldn't unwrap action " + actionId + " because it was not found")
			return
		}
		if (isWrapped(action)) {
			actionManager.unregisterAction(actionId)
			actionManager.registerAction(actionId, originalActionOf(action))
			LOG.info("Unwrapped action " + actionId)
		} else {
			LOG.warn("Action " + actionId + " is not wrapped")
		}
	}

	// TODO
	static void doUnwrapAction(String actionId) {
	}

	private static AnAction originalActionOf(AnAction wrappedAction) {
		for (Method method : wrappedAction.class.methods) {
			if (method.name.equals("originalAction")) {
				try {
					method.accessible = true
					return (AnAction) method.invoke(wrappedAction)
				} catch (Exception e) {
					LOG.warn(e)
				}
			}
		}
		throw new IllegalStateException()
	}

	private static boolean isWrapped(AnAction wrappedAction) {
		for (Class<?> aClass : wrappedAction.class.interfaces) {
			if (aClass.canonicalName == DelegatesToAction.class.canonicalName) {
				return true
			}
		}
		false
	}


	private static interface Listener {
		void onAction(Context context)
	}


	static interface DelegatesToAction {
		@SuppressWarnings("UnusedDeclaration") // used via reflection
		AnAction originalAction()
	}


	private static class WrappedAction extends AnAction implements DelegatesToAction {
		private final Listener listener
		private final AnAction originalAction

		WrappedAction(Listener listener, AnAction originalAction) {
			this.listener = listener
			this.originalAction = originalAction
		}

		@Override void actionPerformed(@NotNull AnActionEvent event) {
			listener.onAction(new Context(event, {
				originalAction.actionPerformed(event)
			}))
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

		WrappedActionPossiblyDumbAware(Listener listener, AnAction originalAction) {
			super(listener, originalAction)
			this.originalAction = originalAction
		}

		@Override boolean isDumbAware() {
			if (originalAction instanceof DumbAware) true
			else (originalAction as PossiblyDumbAware).dumbAware
		}
	}


	private static class WrappedEditorAction extends EditorAction implements DelegatesToAction {
		private final EditorAction originalAction
		private AnActionEvent event

		protected WrappedEditorAction(final Listener listener, final EditorAction originalAction) {
			super(new EditorActionHandler() {
				@Override protected void doExecute(Editor editor, Caret caret, DataContext dataContext) {
					listener.onAction(new Context(event, {
						originalAction.handler.execute(editor, caret, dataContext)
					}))
				}

				// TODO
//                @Override protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
//                    return originalAction.getHandler().isEnabled(editor, caret, dataContext)
//                }

				@Override DocCommandGroupId getCommandGroupId(Editor editor) {
					originalAction.handler.getCommandGroupId(editor)
				}

				@Override boolean runForAllCarets() {
					originalAction.handler.runForAllCarets()
				}

				@Override boolean executeInCommand(Editor editor, DataContext dataContext) {
					originalAction.handler.executeInCommand(editor, dataContext)
				}
			})
			this.originalAction = originalAction

			// Force loading action handler.
			// (It seems that handlers cannot be re-wrapped without calling
			// com.intellij.openapi.editor.actionSystem.EditorAction#ensureHandlersLoaded.)
			setupHandler(handler)
		}

		@Override void beforeActionPerformedUpdate(@NotNull AnActionEvent event) {
			super.beforeActionPerformedUpdate(event)
		}

		@Override void update(@NotNull AnActionEvent event) {
			originalAction.update(event)
		}

		@Override AnAction originalAction() {
			originalAction
		}
	}
}
