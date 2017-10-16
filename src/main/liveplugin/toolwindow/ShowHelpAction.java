package liveplugin.toolwindow;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

class ShowHelpAction extends AnAction implements DumbAware {
    public ShowHelpAction() {
        super("Show help on GitHub");
    }

    @Override public void actionPerformed(@NotNull AnActionEvent e) {
        BrowserUtil.open("https://github.com/dkandalov/live-plugin#liveplugin");
    }
}
