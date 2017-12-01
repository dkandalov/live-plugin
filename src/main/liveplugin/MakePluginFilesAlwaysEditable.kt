package liveplugin

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

class MakePluginFilesAlwaysEditable: NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile) =
        FileUtil.startsWith(file.path, LivePluginAppComponent.livePluginsPath)
}
