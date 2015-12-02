package liveplugin.implementation
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager

import static liveplugin.implementation.GlobalVars.changeGlobalVar

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
		unregisterInspection(project, inspection.shortName)
	}

	static unregisterInspection(Project project, String inspectionName) {
		def inspections = changeGlobalVar(livePluginInspections) { List<InspectionProfileEntry> inspections ->
			if (inspections == null) inspections = []
			inspections.removeAll{ it.shortName == inspectionName }
			inspections
		} as List<InspectionProfileEntry>
		reloadAdditionalProjectInspections(project, inspections)
	}

	// TODO registerInspection2 seems to better solution in terms of using IntelliJ API.
	// However, it doesn't really work:
	//  - causes NPE at com.intellij.codeInspection.ex.InspectionProfileImpl.serializeInto(InspectionProfileImpl.java:325)
	//  - causes NPE at com.intellij.profile.codeInspection.ui.header.InspectionToolsConfigurable$3.customize(InspectionToolsConfigurable.java:208)

	static registerInspection2(Project project, InspectionProfileEntry inspection) {
		def inspections = changeGlobalVar(livePluginInspections) { List<InspectionProfileEntry> inspections ->
			if (inspections == null) inspections = []
			inspections.removeAll{ it.shortName == inspection.shortName }
			inspections.add(inspection)
			inspections
		} as List<InspectionProfileEntry>

		def projectProfile = inspectionsProfileOf(project)
		inspections.each {
			// remove using different instance of inspection should work because inspection is looked up by shortName
			projectProfile.removeTool(InspectionToolRegistrar.wrapTool(it))
			projectProfile.addTool(project, InspectionToolRegistrar.wrapTool(it), [:])
		}
		inspections.each{ projectProfile.enableTool(it.shortName, project) }
	}

	static unregisterInspection2(Project project, String inspectionName) {
		List<InspectionProfileEntry> inspectionsToDelete = []
		changeGlobalVar(livePluginInspections) { List<InspectionProfileEntry> inspections ->
			if (inspections == null) inspections = []
			inspectionsToDelete = inspections.findAll{ it.shortName == inspectionName }
			inspections.removeAll(inspectionsToDelete)
			inspections
		}

		def projectProfile = inspectionsProfileOf(project)
		inspectionsToDelete.each {
			projectProfile.removeTool(InspectionToolRegistrar.wrapTool(it))
		}
	}

	private static def inspectionsProfileOf(Project project) {
		InspectionProjectProfileManager.getInstance(project).inspectionProfile as InspectionProfileImpl
	}

	private static void reloadAdditionalProjectInspections(Project project, List<InspectionProfileEntry> inspections) {
		def inspectionFactories = inspections.collect { inspection ->
			new Factory<InspectionToolWrapper>() {
				@Override InspectionToolWrapper create() {
					InspectionToolRegistrar.wrapTool(inspection)
				}
			}
		}
		def registrar = new InspectionToolRegistrar()
		def obfuscatedName = "b"
		["myInspectionToolFactories", obfuscatedName].each { fieldName ->
			Misc.accessField(registrar, fieldName) {
				it.addAll(inspectionFactories)
			}
		}

		def projectProfile = InspectionProjectProfileManager.getInstance(project).inspectionProfile
		def newProfile = updateProfile(projectProfile, registrar, project)
		inspections.each{ newProfile.enableTool(it.shortName, project) }
	}

	/**
	 * It seems that {@link InspectionProfile}s are not designed for reloadability and are normally constructed by reading xml config.
	 *
	 * Reasons for updating profile in this particular way:
	 *  - One of of ways to sneak in new inspection object is by specifying {@link InspectionToolRegistrar} in constructor.
	 *    => New instance of profile has to be created.
	 *  - After creation profile must be initialized using initInspectionTools(). Implementation of this method
	 *    checks baseProfile in InspectionProfileImpl#getErrorLevel() for all "tools" (aka inspections).
	 *    It will fail if base profile doesn't have new inspection.
	 *    => Need to add inspection to base profile too.
	 *    (Not sure if base profile is always the same or there can be several levels delegation to base profile.
	 *    Also it's not clear what is the difference between base and parent profiles in {@link com.intellij.codeInspection.ModifiableModel}.)
	 */
	private static InspectionProfileImpl updateProfile(InspectionProfile profile, InspectionToolRegistrar registrar, Project project) {
		def appProfileManager = InspectionProfileManager.instance

		if (profile.modifiableModel.baseProfileName == null || profile.modifiableModel.baseProfileName == profile.name) {
			def rootProfile = appProfileManager.rootProfile

			def newRootProfile = new InspectionProfileImpl(rootProfile.name, registrar, appProfileManager)
			newRootProfile.copyFrom(rootProfile)
			newRootProfile.initInspectionTools(project)

			appProfileManager.deleteProfile(rootProfile.name)
			appProfileManager.updateProfile(newRootProfile)
			newRootProfile
		} else {
			def projectProfileManager = InspectionProjectProfileManager.getInstance(project)

			def baseProfile = appProfileManager.getProfile(profile.modifiableModel.baseProfileName) as InspectionProfile
			if (baseProfile == null) baseProfile = projectProfileManager.getProfile(profile.modifiableModel.baseProfileName) as InspectionProfile
			if (baseProfile != profile) {
				updateProfile(baseProfile, registrar, project)
			}

			def projectProfile = projectProfileManager.inspectionProfile
			def projectBaseProfile = appProfileManager.getProfile(projectProfile.modifiableModel.baseProfileName) as InspectionProfile
			def newProjectProfile = new InspectionProfileImpl(projectProfile.name, registrar, projectProfileManager, projectBaseProfile)
			newProjectProfile.copyFrom(projectProfile)
			newProjectProfile.initInspectionTools(project)

			projectProfileManager.deleteProfile(projectProfile.name)
			projectProfileManager.updateProfile(newProjectProfile)
			newProjectProfile
		}
	}


}
