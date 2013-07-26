import javax.swing.*

import static liveplugin.PluginUtil.registerToolWindow

static createPanelWithButton(JPanel panel = new JPanel()) {
	def selfReproducingButton = new JButton("Hello")
	selfReproducingButton.addActionListener({ createPanelWithButton(panel) } as AbstractAction)
	panel.add(selfReproducingButton)
	panel.revalidate()
	panel
}

registerToolWindow("helloToolWindow") { createPanelWithButton() }

// To remove the above toolwindow, you can run the following code.
//unregisterToolWindow("helloToolWindow")