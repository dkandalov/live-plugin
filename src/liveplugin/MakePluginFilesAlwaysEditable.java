package liveplugin;

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class MakePluginFilesAlwaysEditable implements NonProjectFileWritingAccessExtension {
    @Override public boolean isWritable(@NotNull VirtualFile file) {
        return FileUtil.startsWith(file.getPath(), LivePluginAppComponent.pluginsRootPath());
    }
}
