package liveplugin.implementation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import liveplugin.PluginUtil
import org.jetbrains.annotations.NotNull

class FileSearch {
	static PsiFile findFileByName(String filePath, @NotNull Project project, boolean searchInLibraries = false) {
		def files = findAllFilesByName(filePath, project, searchInLibraries)
		if (files.size() > 1) {
			def filePaths = files.collect { it.virtualFile.canonicalPath }.join("\n")
			throw new IllegalStateException("There are multiple files which match '${filePath}':\n ${filePaths}")
		}
		if (files.size() == 0) {
			throw new IllegalStateException("There are no files which match: '${filePath}'")
		}
		files.first()
	}

	static List<PsiFile> findAllFilesByName(String filePath, @NotNull Project project, boolean searchInLibraries = false) {
		PluginUtil.runReadAction {
			def scope = searchInLibraries ? ProjectScope.getAllScope(project) : ProjectScope.getProjectScope(project)
			def pathAndName = filePath.split("[/\\\\]").toList().findAll { !it.empty }
			def reversePath = (pathAndName.size() > 1 ? pathAndName.reverse().tail() : [])
			def name = pathAndName.last()
			FilenameIndex
				.getFilesByName(project, name, scope)
				.toList()
				.findAll { file -> matches(reversePath, file) }
		}
	}

	private static boolean matches(List<String> reversePath, PsiFileSystemItem fileSystemItem) {
		if (reversePath.empty) true
		else if (fileSystemItem.parent.name != reversePath.first()) false
		else matches(reversePath.tail(), fileSystemItem.parent)
	}
}
