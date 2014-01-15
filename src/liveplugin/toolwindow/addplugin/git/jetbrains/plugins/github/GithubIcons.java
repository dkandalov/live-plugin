package liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class GithubIcons {
	private static Icon load(String path) {
		return IconLoader.getIcon(path, GithubIcons.class);
	}

	public static final Icon Github_icon = load("/liveplugin/toolwindow/addplugin/git/jetbrains/plugins/github/github_icon.png"); // 16x16
}
