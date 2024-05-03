package liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.LoadingOrder.Companion.FIRST
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.StatusBarWidgetFactory


@Suppress("UnstableApiUsage")
fun registerWidget(
    widgetId: String,
    disposable: Disposable,
    loadingOrder: LoadingOrder = FIRST,
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
