package liveplugin.toolwindow.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.findAll;

public class DependenciesUtil {
	public static boolean anyModuleHasLibraryAsDependencyIn(Project project, final String libraryName) {
		List<Module> modulesWithoutDependency = findAll(ModuleManager.getInstance(project).getModules(), new Condition<Module>() {
			@Override public boolean value(Module module) {
				return dependsOn(libraryName, module);
			}
		});
		return !modulesWithoutDependency.isEmpty();
	}

	public static void removeLibraryDependencyFrom(final Project project, final String libraryName) {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			@Override public void run() {
				for (Module module : ModuleManager.getInstance(project).getModules()) {
					if (dependsOn(libraryName, module)) {
						removeLibraryDependencyFrom(module, libraryName);
					}
				}
			}
		});
	}

	private static void removeLibraryDependencyFrom(Module module, String libraryName) {
		ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
		LibraryTable libraryTable = modifiableModel.getModuleLibraryTable();

		Library library = libraryTable.getLibraryByName(libraryName);
		if (library != null) libraryTable.removeLibrary(library);

		modifiableModel.commit();
	}

	public static void addLibraryDependencyTo(final Project project, final String libraryName,
	                                          final List<Pair<String, OrderRootType>> paths) {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			@Override public void run() {
				Module[] modules = ModuleManager.getInstance(project).getModules();
				if (modules.length > 0) {
					// Add dependency to the first module because this is enough for IntelliJ too see classes
					// and adding dependency to all modules can be very slow for large projects
					// (~16 seconds with UI freeze for IntelliJ source code).
					addLibraryDependencyTo(modules[0], libraryName, paths);
				}
			}
		});
	}

	private static void addLibraryDependencyTo(Module module, String libraryName, List<Pair<String, OrderRootType>> paths) {
		ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
		LibraryTable libraryTable = modifiableModel.getModuleLibraryTable();
		if (dependsOn(libraryName, libraryTable)) return;

		Library library = libraryTable.createLibrary(libraryName);
		Library.ModifiableModel modifiableLibrary = library.getModifiableModel();
		for (Pair<String, OrderRootType> pathAndType : paths) {
			modifiableLibrary.addRoot(pathAndType.first, pathAndType.second);
		}
		modifiableLibrary.commit();

		LibraryOrderEntry libraryOrderEntry = modifiableModel.findLibraryOrderEntry(library);
		if (libraryOrderEntry != null) libraryOrderEntry.setScope(DependencyScope.PROVIDED);
		modifiableModel.commit();
	}

	private static boolean dependsOn(String libraryName, Module module) {
		ModifiableRootModel moduleRootManager = ModuleRootManager.getInstance(module).getModifiableModel();
		Library library = moduleRootManager.getModuleLibraryTable().getLibraryByName(libraryName);
        moduleRootManager.dispose();
		return library != null;
	}

	private static boolean dependsOn(String libraryName, LibraryTable libraryTable) {
		return libraryTable.getLibraryByName(libraryName) != null;
	}
}
