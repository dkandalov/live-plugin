package liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.WindowManager


fun registerWidget(
    widgetId: String,
    disposable: Disposable,
    anchor: String = "before Position",
    presentation: WidgetPresentation
) {
    registerProjectOpenListener(disposable) { project ->
        registerWidget(widgetId, project, disposable, anchor, presentation)
    }
}

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
