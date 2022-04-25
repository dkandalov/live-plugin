package liveplugin.implementation

import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager

import static liveplugin.implementation.Misc.accessField
import static liveplugin.implementation.Misc.newDisposable

class Inspections {
	private static final String livePluginInspections = "LivePluginInspections"

	static registerInspection(Disposable disposable, InspectionProfileEntry inspection) {
		Projects.registerProjectListener(disposable) { Project project ->
			registerInspection(project, newDisposable([disposable, project]), inspection)
		}
	}

	static registerInspection(Project project, Disposable disposable = project, InspectionProfileEntry inspection) {
		def inspections = new GlobalVar(livePluginInspections).set { List<InspectionProfileEntry> inspections ->
			if (inspections == null) inspections = []
			inspections.removeAll { it.shortName == inspection.shortName }
			inspections.add(inspection)
			inspections
		} as List<InspectionProfileEntry>

		def projectProfile = inspectionsProfileOf(project)
		inspections.each {
			// remove using different instance of inspection should work because inspection is looked up by shortName
			projectProfile.removeTool(InspectionToolRegistrar.wrapTool(it))
			projectProfile.addTool(project, InspectionToolRegistrar.wrapTool(it), [:])
		}
		inspections.each { projectProfile.enableTool(it.shortName, project) }

		if (disposable != null) {
			Disposer.register(disposable, new Disposable() {
				@Override void dispose() {
					unregisterInspection(project, inspection)
				}
			})
		}
	}

	static unregisterInspection(Project project, InspectionProfileEntry inspection) {
		unregisterInspection(project, inspection.shortName)
	}

	static unregisterInspection(Project project, String inspectionName) {
		List<InspectionProfileEntry> inspectionsToDelete = []
		new GlobalVar(livePluginInspections).set { List<InspectionProfileEntry> inspections ->
			if (inspections == null) inspections = []
			inspectionsToDelete = inspections.findAll { it.shortName == inspectionName }
			inspections.removeAll(inspectionsToDelete)
			inspections
		}

		def projectProfile = inspectionsProfileOf(project)
		inspectionsToDelete.each {
			projectProfile.removeTool(InspectionToolRegistrar.wrapTool(it))
		}
	}

	private static inspectionsProfileOf(Project project) {
		InspectionProjectProfileManager.getInstance(project).getCurrentProfile() as InspectionProfileImpl
	}

	/**
	 * It seems that {@link InspectionProfile}s are not designed for reloadability and are normally constructed by reading xml config.
	 *
	 * Reasons for updating profile in this particular way:
	 *  - One of the ways to sneak in new inspection object is by specifying {@link InspectionToolRegistrar} in constructor.
	 *    => New instance of profile has to be created.
	 *  - After creation profile must be initialized using initInspectionTools(). Implementation of this method
	 *    checks baseProfile in InspectionProfileImpl#getErrorLevel() for all "tools" (aka inspections).
	 *    It will fail if base profile doesn't have new inspection.
	 *    => Need to add inspection to base profile too.
	 *    (Not sure if base profile is always the same or there can be several levels delegation to base profile.
	 *    Also it's not clear what is the difference between base and parent profiles in {@link com.intellij.openapi.roots.libraries.Library.ModifiableModel}.)
	 */
	private static InspectionProfileImpl updateProfile(InspectionProfile profile, InspectionToolRegistrar registrar, Project project) {
		BaseInspectionProfileManager appProfileManager = InspectionProfileManager.getInstance()

		def baseProfile = getBaseProfileOf(profile)
		if (baseProfile == null || baseProfile.name == profile.name) {
			def rootProfile = appProfileManager.getCurrentProfile()

			def newRootProfile = new InspectionProfileImpl(rootProfile.name, registrar, appProfileManager)
			newRootProfile.copyFrom(rootProfile)
			newRootProfile.initInspectionTools(project)

			appProfileManager.deleteProfile(rootProfile.name)
			appProfileManager.addProfile(newRootProfile)
			newRootProfile

		} else {
			BaseInspectionProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project)

			if (baseProfile != profile) {
				updateProfile(baseProfile, registrar, project)
			}

			def projectProfile = projectProfileManager.getCurrentProfile()
			def newProjectProfile = new InspectionProfileImpl(projectProfile.name, registrar, projectProfileManager)
			newProjectProfile.copyFrom(projectProfile)
			newProjectProfile.initInspectionTools(project)

			projectProfileManager.deleteProfile(projectProfile.name)
			projectProfileManager.addProfile(newProjectProfile)
			newProjectProfile
		}
	}

	private static getBaseProfileOf(InspectionProfileImpl profile) {
		accessField(profile, "myBaseProfile", InspectionProfileImpl)
	}
}
