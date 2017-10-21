package liveplugin;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

public class Icons {
	public static final Icon pluginIcon = AllIcons.Nodes.Plugin;
	public static final Icon addPluginIcon = AllIcons.General.Add;
	public static final Icon newPluginIcon = pluginIcon;
	public static final Icon deletePluginIcon = AllIcons.General.Remove;
	public static final Icon refreshPluginsPanelIcon = AllIcons.Actions.Refresh;
	public static final Icon pluginToolwindowIcon = (
		UIUtil.isUnderDarcula() ?
			AllIcons.Nodes.Plugin :
			// Custom darker icon so that it looks ok as toolwindow icon with default (white) look-and-feel.
			IconLoader.getIcon("/liveplugin/plugin-toolwindow-icon.png")
	);
	public static final Icon runPluginIcon = AllIcons.Actions.Execute;
	public static final Icon testPluginIcon = AllIcons.RunConfigurations.Junit;
	public static final Icon expandAllIcon = AllIcons.Actions.Expandall;
	public static final Icon collapseAllIcon = AllIcons.Actions.Collapseall;
	public static final Icon settingsIcon = AllIcons.General.ProjectSettings;
	public static final Icon helpIcon = AllIcons.Actions.Help;
	public static final Icon newFolderIcon = AllIcons.Nodes.NewFolder;
}
