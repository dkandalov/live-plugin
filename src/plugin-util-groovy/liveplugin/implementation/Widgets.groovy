package liveplugin.implementation

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import static com.intellij.openapi.wm.StatusBarWidget.*

class Widgets {
	static registerWidget(String widgetId, Disposable disposable,
                          LoadingOrder loadingOrder = LoadingOrder.FIRST, WidgetPresentation presentation) {
        def widgetFactory = new StatusBarWidgetFactory() {
            @Override String getId() { widgetId }
            @Override String getDisplayName() { widgetId }
            @Override boolean isAvailable(Project project) { true }
            @Override boolean canBeEnabledOn(StatusBar statusBar) { true }
            @Override StatusBarWidget createWidget(Project project) {
                new StatusBarWidget() {
                    @Override String ID() { widgetId }
                    @Override WidgetPresentation getPresentation() { presentation }
                    @Override def install(StatusBar statusBar) {}
                    @Override def dispose() {}
                }
            }
            @Override def disposeWidget(StatusBarWidget widget) {}
        }
        StatusBarWidgetFactory.EP_NAME.point.registerExtension(widgetFactory, loadingOrder, disposable)
	}

    @Deprecated // Use registerWidget() without project
	static registerWidget(String widgetId, Project project, Disposable disposable = project,
	                      String anchor = "before Position", WidgetPresentation presentation) {
		// TODO the line below is broken because on IDE start initial frame will not be mapped to project
		// (at least not at the point when project manager listener callback is invoked)
		def frame = WindowManager.instance.allProjectFrames.find{ it.project == project }
		if (frame == null) return

		def widget = new StatusBarWidget() {
			@Override String ID() { widgetId }
			@Override WidgetPresentation getPresentation() { presentation }
			@Override void install(@NotNull StatusBar statusBar) {}
			@Override void dispose() {}
		}
		frame.statusBar.addWidget(widget, anchor, disposable)
		frame.statusBar.updateWidget(widgetId)
	}

	static updateWidget(String widgetId) {
		WindowManager.instance.allProjectFrames*.statusBar*.updateWidget(widgetId)
	}

	static unregisterWidget(String widgetId) {
		WindowManager.instance.allProjectFrames*.statusBar*.removeWidget(widgetId)
	}

	@Nullable static StatusBarWidget findWidget(String widgetId, Project project) {
		def frame = WindowManager.instance.allProjectFrames.find{ it.project == project }
		if (frame == null) return null
		frame.statusBar.getWidget(widgetId)
	}
}
