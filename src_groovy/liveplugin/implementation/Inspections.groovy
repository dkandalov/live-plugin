package liveplugin.implementation
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.*
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager

import static liveplugin.PluginUtil.changeGlobalVar

class Inspections {
	private static final String livePluginInspections = "LivePluginInspections"

	static registerInspection(Project project, InspectionProfileEntry inspection) {
		def inspections = changeGlobalVar(livePluginInspections) { List<InspectionProfileEntry> inspections ->
			if (inspections == null) inspections = []
			inspections.removeAll{ it.shortName == inspection.shortName }
			inspections.add(inspection)
			inspections
		} as List<InspectionProfileEntry>
		reloadAdditionalProjectInspections(project, inspections)
	}

	static unregisterInspection(Project project, InspectionProfileEntry inspection) {
		def inspections = changeGlobalVar(livePluginInspections) { List<InspectionProfileEntry> inspections ->
			if (inspections == null) inspections = []
			inspections.removeAll{ it.shortName == inspection.shortName }
			inspections
		} as List<InspectionProfileEntry>
		reloadAdditionalProjectInspections(project, inspections)
	}

	private static void reloadAdditionalProjectInspections(Project project, List<InspectionProfileEntry> inspections) {
		def registrar = new InspectionToolRegistrar()
		inspections.each { inspection ->
			registrar.registerInspectionToolFactory(new com.intellij.openapi.util.Factory<InspectionToolWrapper>() {
				@Override InspectionToolWrapper create() {
					if (inspection instanceof LocalInspectionTool) {
						new LocalInspectionToolWrapper(inspection)
					} else if (inspection instanceof GlobalInspectionTool) {
						new GlobalInspectionToolWrapper(inspection)
					} else {
						throw new IllegalStateException("Expected inspection to be local or global but was " + inspection.class.name)
					}
				}
			}, true)
		}

		def projectProfile = InspectionProjectProfileManager.getInstance(project).inspectionProfile
		updateProfile(projectProfile, registrar, project)
	}

	/**
	 * It seems that {@link InspectionProfile}s are not designed for reloadability and are normally constructer by reading xml config.
	 *
	 * Reasons for doing it in this particular way:
	 *  - One of of ways to sneak in new inspection object is by specifying {@link InspectionToolRegistrar} in constructor.
	 *    => New instance of profile has to be created.
	 *  - After creation profile must be initialized using initInspectionTools(). Implementation of this method
	 *    checks baseProfile in InspectionProfileImpl#getErrorLevel() for all "tools" (aka inspections).
	 *    It will fail if base profile doesn't have new inspection.
	 *    => Need to add inspection to base profile too.
	 *    (Not sure if base profile is always the same or there can be several levels delegation to base profile.
	 *    Also it's not clear what is the difference between base and parent profiles in {@link com.intellij.codeInspection.ModifiableModel}.)
	 */
	private static updateProfile(InspectionProfile profile, InspectionToolRegistrar registrar, Project project) {
		def profileManager = InspectionProfileManager.instance
		def projectProfileManager = InspectionProjectProfileManager.getInstance(project)

		if (profile.modifiableModel.baseProfileName == null) {
			def rootProfile = profileManager.rootProfile

			def newRootProfile = new InspectionProfileImpl(rootProfile.name, registrar, profileManager)
			newRootProfile.copyFrom(rootProfile)
			newRootProfile.baseProfile = null
			newRootProfile.initInspectionTools(project)

			profileManager.deleteProfile(rootProfile.name)
			profileManager.updateProfile(newRootProfile)
		} else {
			def baseProfile = profileManager.getProfile(profile.modifiableModel.baseProfileName) as InspectionProfile
			if (baseProfile == null) projectProfileManager.getProfile(profile.modifiableModel.baseProfileName)
			updateProfile(baseProfile, registrar, project)

			def projectProfile = projectProfileManager.inspectionProfile

			def newProjectProfile = new InspectionProfileImpl(projectProfile.name, registrar, projectProfileManager)
			newProjectProfile.copyFrom(projectProfile)
			newProjectProfile.baseProfile = profileManager.getProfile(projectProfile.modifiableModel.baseProfileName) as InspectionProfile
			newProjectProfile.initInspectionTools(project)

			projectProfileManager.deleteProfile(projectProfile.name)
			projectProfileManager.updateProfile(newProjectProfile)
		}
	}


}
