import javax.swing.*

import static liveplugin.PluginUtil.registerToolWindow

static createPanelWithButton(JPanel panel = new JPanel()) {
	def selfReproducingButton = new JButton("Hello")
	selfReproducingButton.addActionListener({ createPanelWithButton(panel) } as AbstractAction)
	panel.add(selfReproducingButton)
	panel.revalidate()
	panel
}

registerToolWindow("HelloToolWindow", pluginDisposable) {
	createPanelWithButton()
}

// You can run the following statement to remove the tool window
//unregisterToolWindow("helloToolWindow")

// Or you can comment out the registration code and reload the plugin.
// This will cause "pluginDisposable" to be disposed what will unregister the tool window.
