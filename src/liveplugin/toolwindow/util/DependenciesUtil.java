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
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class DependenciesUtil {
	public static boolean allModulesHasLibraryAsDependencyIn(Project project, String libraryName) {
		return findModulesWithoutLibrary(ModuleManager.getInstance(project).getModules(), libraryName).isEmpty();
	}

	public static void removeLibraryDependencyFrom(final Project project, final String libraryName) {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			@Override public void run() {
				Module[] modules = ModuleManager.getInstance(project).getModules();
				for (Module module : findModulesWithLibrary(modules, libraryName)) {

					ModifiableRootModel moduleRootManager = ModuleRootManager.getInstance(module).getModifiableModel();
					LibraryTable libraryTable = moduleRootManager.getModuleLibraryTable();

					Library library = libraryTable.getLibraryByName(libraryName);
					if (library != null) libraryTable.removeLibrary(library);
					moduleRootManager.commit();

				}
			}
		});
	}

	public static void addLibraryDependencyTo(final Project project, final String libraryName,
	                                          final List<Pair<String, OrderRootType>> paths) {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			@Override public void run() {
				Module[] modules = ModuleManager.getInstance(project).getModules();
				for (Module module : findModulesWithoutLibrary(modules, libraryName)) {

					ModifiableRootModel moduleRootManager = ModuleRootManager.getInstance(module).getModifiableModel();
					LibraryTable libraryTable = moduleRootManager.getModuleLibraryTable();

					Library library = libraryTable.createLibrary(libraryName);
					Library.ModifiableModel modifiableLibrary = library.getModifiableModel();
					for (Pair<String, OrderRootType> pathAndType : paths) {
						modifiableLibrary.addRoot(pathAndType.first, pathAndType.second);
					}
					modifiableLibrary.commit();

					LibraryOrderEntry libraryOrderEntry = moduleRootManager.findLibraryOrderEntry(library);
					if (libraryOrderEntry != null) libraryOrderEntry.setScope(DependencyScope.PROVIDED);
					moduleRootManager.commit();

				}
			}
		});
	}

	private static List<Module> findModulesWithoutLibrary(Module[] modules, final String libraryName) {
		return ContainerUtil.findAll(modules, new Condition<Module>() {
			@Override public boolean value(Module module) {
				return !dependsOn(libraryName, module);
			}
		});
	}

	private static List<Module> findModulesWithLibrary(Module[] modules, final String libraryName) {
		return ContainerUtil.findAll(modules, new Condition<Module>() {
			@Override public boolean value(Module module) {
				return dependsOn(libraryName, module);
			}
		});
	}

	private static boolean dependsOn(String libraryName, Module module) {
		ModifiableRootModel moduleRootManager = ModuleRootManager.getInstance(module).getModifiableModel();
		Library library = moduleRootManager.getModuleLibraryTable().getLibraryByName(libraryName);
		return library != null;
	}
}
