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

// You can run the following statement to remove toolwindow
//unregisterToolWindow("helloToolWindow")
// Or you can comment out registration code and reload plugin.
// This will cause "pluginDisposable" to be disposed what will unregister the toolwindow.
