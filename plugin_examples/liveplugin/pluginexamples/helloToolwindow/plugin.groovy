import javax.swing.*

import static liveplugin.PluginUtil.*

static createPanelWithButton(JPanel panel = new JPanel()) {
	def selfReproducingButton = new JButton("Hello")
	selfReproducingButton.addActionListener({ createPanelWithButton(panel) } as AbstractAction)
	panel.add(selfReproducingButton)
	panel.revalidate()
	panel
}

invokeOnEDT {
	registerToolWindow("helloToolWindow") { createPanelWithButton() }
}

// To remove the above toolwindow, you can run the following code.
//unregisterToolWindow("helloToolWindow")