import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

import static liveplugin.PluginUtil.show

if (isIdeStartup) return

def fileStats = FileTypeManager.instance.registeredFileTypes.inject([:]) { Map stats, FileType fileType ->
	def scope = GlobalSearchScope.projectScope(project)
	int fileCount = FileBasedIndex.instance.getContainingFiles(FileTypeIndex.NAME, fileType, scope).size()
	if (fileCount > 0) stats.put("'$fileType.defaultExtension'", fileCount)
	stats
}.sort{ -it.value }

show("File count by type:<br/>" + fileStats.entrySet().join("<br/>"))

