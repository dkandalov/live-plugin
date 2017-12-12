package liveplugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.util.IconUtil.toSize
import com.intellij.util.ui.UIUtil
import javax.swing.Icon

object Icons {
    val pluginIcon = AllIcons.Nodes.Plugin!!
    val addPluginIcon = AllIcons.General.Add!!
    val newPluginIcon = pluginIcon
    val deletePluginIcon = AllIcons.General.Remove!!
    val refreshPluginsPanelIcon = AllIcons.Actions.Refresh!!
    val pluginToolwindowIcon: Icon = toSize(
        if (UIUtil.isUnderDarcula()) AllIcons.Nodes.Plugin // Custom darker icon so that it looks ok as toolwindow icon with default (white) look-and-feel.
        else IconLoader.getIcon("/liveplugin/plugin-toolwindow-icon.png"),
        13, 13 // resize because IJ logs warning if toolwindow icon is not 13x13
    )
    val runPluginIcon = AllIcons.Actions.Execute!!
    val testPluginIcon = AllIcons.RunConfigurations.Junit!!
    val expandAllIcon = AllIcons.Actions.Expandall!!
    val collapseAllIcon = AllIcons.Actions.Collapseall!!
    val settingsIcon = AllIcons.General.ProjectSettings!!
    val helpIcon = AllIcons.Actions.Help!!
    val newFolderIcon = AllIcons.Nodes.NewFolder!!
}
