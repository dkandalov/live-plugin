package liveplugin;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

public class Icons {
	public static final Icon ADD_PLUGIN_ICON = AllIcons.General.Add;
	public static final Icon NEW_PLUGIN_ICON = AllIcons.FileTypes.Text;
	public static final Icon COPY_PLUGIN_FROM_PATH_ICON = AllIcons.Actions.Copy;
	public static final Icon DELETE_PLUGIN_ICON = AllIcons.General.Remove;
	public static final Icon REFRESH_PLUGINS_PANEL_ICON = AllIcons.Actions.Refresh;
	public static final Icon PLUGIN_ICON = AllIcons.Nodes.Plugin;
	// this is resized icon because IntelliJ requires toolwindow icons to be 13x13
	public static final Icon PLUGIN_TOOLWINDOW_ICON = (
			UIUtil.isUnderDarcula() ?
				IconLoader.getIcon("/liveplugin/plugin-toolwindow-icon_dark.png") :
				IconLoader.getIcon("/liveplugin/plugin-toolwindow-icon.png")
	);
	public static final Icon RUN_PLUGIN_ICON = AllIcons.Actions.Execute;
	public static final Icon TEST_PLUGIN_ICON = AllIcons.RunConfigurations.Junit;
	public static final Icon EXPAND_ALL_ICON = AllIcons.Actions.Expandall;
	public static final Icon COLLAPSE_ALL_ICON = AllIcons.Actions.Collapseall;
	public static final Icon SETTINGS_ICON = AllIcons.General.ProjectSettings;
	public static final Icon HELP_ICON = AllIcons.Actions.Help;
}
