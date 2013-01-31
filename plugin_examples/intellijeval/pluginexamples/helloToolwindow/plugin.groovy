import javax.swing.*

import static intellijeval.PluginUtil.registerToolWindow

static panelWithButton(JPanel panel = new JPanel()) {
	def selfReproducingButton = new JButton("Hello")
	selfReproducingButton.addActionListener({ panelWithButton(panel) } as AbstractAction)
	panel.add(selfReproducingButton)
	panel.revalidate()
	panel
}

registerToolWindow("helloToolWindow", panelWithButton())

// To remove the above toolwindow, you can run the following code.
// (Ideally tool windows should be able to remove themselves.. not done here for simplicity.)
//import com.intellij.openapi.wm.ToolWindowManager
//ToolWindowManager.getInstance(event.project).unregisterToolWindow("helloToolWindow")