package liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager


@Suppress("UnstableApiUsage")
fun registerWidget(
    widgetId: String,
    disposable: Disposable,
    loadingOrder: LoadingOrder = LoadingOrder.FIRST,
    presentation: WidgetPresentation
) {
    val widgetFactory = object : StatusBarWidgetFactory {
        override fun getId() = widgetId
        override fun getDisplayName() = widgetId
        override fun isAvailable(project: Project) = true
        override fun canBeEnabledOn(statusBar: StatusBar) = true
        override fun createWidget(project: Project) = object : StatusBarWidget {
            override fun ID() = widgetId
            override fun getPresentation() = presentation
            override fun install(statusBar: StatusBar) {}
            override fun dispose() {}
        }
        override fun disposeWidget(widget: StatusBarWidget) {}
    }

    StatusBarWidgetFactory.EP_NAME.point.registerExtension(widgetFactory, loadingOrder, disposable)
}

@Deprecated(
    message = "Use registerWidget() without project",
    replaceWith = ReplaceWith("registerWidget(widgetId, disposable, loadingOrder, presentation")
)
fun registerWidget(
    widgetId: String,
    project: Project,
    disposable: Disposable,
    anchor: String = "before Position",
    presentation: WidgetPresentation
) {
    // TODO the line below is broken because on IDE start initial frame will not be mapped to project
    // (at least not at the point when project manager listener callback is invoked)
    val frame = WindowManager.getInstance().allProjectFrames.find { it.project == project } ?: return

    val widget = object: StatusBarWidget {
        override fun ID() = widgetId
        override fun getPresentation() = presentation
        override fun install(statusBar: StatusBar) {}
        override fun dispose() {}
    }
    @Suppress("UnstableApiUsage")
    frame.statusBar?.addWidget(widget, anchor, disposable)
    frame.statusBar?.updateWidget(widgetId)
}
