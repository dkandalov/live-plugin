package liveplugin;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/*
class ActionWrapperTest {
	@Test void "can wrap and unwrap actions"() {
		unwrapAction("EditorDown")
		assert !isWrapped("EditorDown")

		wrapAction("EditorDown", new ActionWrapper.Listener() {
			@Override void beforeAction() {}
		})
		assert isWrapped("EditorDown")

		unwrapAction("EditorDown")
		assert !isWrapped("EditorDown")
	}

	private static boolean isWrapped(String actionId) {
		def actionClassName = ActionManager.instance.getAction(actionId).class.name
		def actionHandlerClassName = ((EditorAction) ActionManager.instance.getAction(actionId)).handler.class.name
		actionClassName.endsWith("WrappedEditorAction") && actionHandlerClassName.contains("WrappedEditorAction")
	}
}
*/
// TODO use in PluginUtil because EditorActions needs special support
public class ActionWrapper {
    private static final Logger LOG = Logger.getInstance(ActionWrapper.class);

    public static AnAction wrapAction(String actionId, final Listener listener) {
        ActionManager actionManager = ActionManager.getInstance();
        final AnAction action = actionManager.getAction(actionId);
        if (action == null) {
            LOG.warn("Couldn't wrap action " + actionId + " because it was not found");
            return null;
        }

        AnAction newAction;
        if (action instanceof EditorAction) {
            newAction = new WrappedEditorAction(listener, (EditorAction) action);
        } else if (action instanceof DumbAware) {
            newAction = new WrappedAction(listener, action);
        } else {
            newAction = new WrappedDumbUnawareAction(listener, action);
        }
        newAction.getTemplatePresentation().setText(action.getTemplatePresentation().getText());
        newAction.getTemplatePresentation().setIcon(action.getTemplatePresentation().getIcon());
        newAction.getTemplatePresentation().setHoveredIcon(action.getTemplatePresentation().getHoveredIcon());
        newAction.getTemplatePresentation().setDescription(action.getTemplatePresentation().getDescription());
        newAction.copyShortcutFrom(action);

        actionManager.unregisterAction(actionId);
        actionManager.registerAction(actionId, newAction);
        LOG.info("Wrapped action " + actionId);

        return newAction;
    }

    public static void unwrapAction(String actionId) {
        ActionManager actionManager = ActionManager.getInstance();
        AnAction wrappedAction = actionManager.getAction(actionId);
        if (wrappedAction == null) {
            LOG.warn("Couldn't unwrap action " + actionId + " because it was not found");
            return;
        }
        if (isActionWrapper(wrappedAction)) {
            actionManager.unregisterAction(actionId);
            actionManager.registerAction(actionId, originalAction(wrappedAction));
            LOG.info("Unwrapped action " + actionId);
        } else {
            LOG.warn("Action " + actionId + " is not wrapped");
        }
    }

    private static AnAction originalAction(AnAction wrappedAction) {
        for (Method method : wrappedAction.getClass().getMethods()) {
            if (method.getName().equals("originalAction")) {
                try {
                    method.setAccessible(true);
                    return (AnAction) method.invoke(wrappedAction);
                } catch (Exception e) {
                    LOG.warn(e);
                }
            }
        }
        throw new IllegalStateException();
    }

    private static boolean isActionWrapper(AnAction wrappedAction) {
        for (Class<?> aClass : wrappedAction.getClass().getInterfaces()) {
            if (aClass.getCanonicalName().equals(DelegatesToAction.class.getCanonicalName())) {
                return true;
            }
        }
        return false;
    }


    public interface Listener {
        void beforeAction();
    }

    public static interface DelegatesToAction {
        @SuppressWarnings("UnusedDeclaration") // used via reflection
        AnAction originalAction();
    }

    private static class WrappedAction extends AnAction implements DelegatesToAction, DumbAware {
        private final Listener listener;
        private final AnAction originalAction;

        public WrappedAction(Listener listener, AnAction originalAction) {
            this.listener = listener;
            this.originalAction = originalAction;
        }

        @Override public void actionPerformed(@NotNull AnActionEvent event) {
            listener.beforeAction();
            originalAction.actionPerformed(event);
        }

        @Override public void update(@NotNull AnActionEvent event) {
            originalAction.update(event);
        }

        @Override public AnAction originalAction() {
            return originalAction;
        }
    }

    private static class WrappedDumbUnawareAction extends WrappedAction {
        public WrappedDumbUnawareAction(Listener listener, AnAction originalAction) {
            super(listener, originalAction);
        }
    }

    private static class WrappedEditorAction extends EditorAction implements DelegatesToAction {
        private final EditorAction originalAction;

        protected WrappedEditorAction(final Listener listener, final EditorAction originalAction) {
            super(new EditorActionHandler() {
                @Override protected void doExecute(Editor editor, Caret caret, DataContext dataContext) {
                    listener.beforeAction();
                    originalAction.getHandler().execute(editor, caret, dataContext);
                }

                // TODO
//                @Override protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
//                    return originalAction.getHandler().isEnabled(editor, caret, dataContext);
//                }

                @Override public DocCommandGroupId getCommandGroupId(Editor editor) {
                    return originalAction.getHandler().getCommandGroupId(editor);
                }

                @Override public boolean runForAllCarets() {
                    return originalAction.getHandler().runForAllCarets();
                }

                @Override public boolean executeInCommand(Editor editor, DataContext dataContext) {
                    return originalAction.getHandler().executeInCommand(editor, dataContext);
                }
            });
            this.originalAction = originalAction;

            // Force loading action handler.
            // (It seems that handlers cannot be re-wrapped without calling
            // com.intellij.openapi.editor.actionSystem.EditorAction#ensureHandlersLoaded.)
            setupHandler(getHandler());
        }

        @Override public void update(@NotNull AnActionEvent event) {
            originalAction.update(event);
        }

        @Override public AnAction originalAction() {
            return originalAction;
        }
    }
}
