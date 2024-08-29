package liveplugin.implementation

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.InspectionWrapperUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.InspectionProjectProfileManager


fun registerInspectionIn(project: Project, disposable: Disposable = project, inspection: InspectionProfileEntry) {
    val projectProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
    projectProfile.addTool(project, InspectionWrapperUtil.wrapTool(inspection), emptyMap())
    projectProfile.enableTool(inspection.shortName, project)

    Disposer.register(disposable) {
        projectProfile.removeTool(InspectionWrapperUtil.wrapTool(inspection))
    }
}
