import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.unscramble.AnalyzeStacktraceUtil
import com.intellij.unscramble.UnscrambleDialog
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import static ssh.Utils.*
//import static ru.beans.Bean.*

//-- classpath: c:/work/zz_misc/groovy-beans/target/gbeans-0.0-SNAPSHOT.jar

registerInMetaClasses(actionEvent)

def registerAction() {
  def actionId = "myAction"

  def sshConnections = [
          "don't want to publish"
  ]
  def newSshAction = { text, configName ->
    new AnAction(text, text, IconLoader.getIcon("/fileTypes/text.png")) {
      @Override
      public void actionPerformed(AnActionEvent e2) {
        Runtime.runtime.exec("\"c:/Program Files (x86)/putty/putty.exe\" -load \"${configName}\"")
      }
    }
  }
  def createActions = null
  createActions = { actionGroup, connectionsTree ->
    connectionsTree.each { entry ->
      if (entry.value instanceof String) {
        actionGroup.add(newSshAction(entry.key, entry.value))
      } else {
        def subActions = createActions(new DefaultActionGroup(entry.key.toString(), true), entry.value)
        actionGroup.add(subActions)
      }
    }
    actionGroup
  }
//  def toolWindowManager = ToolWindowManager.getInstance(project())
//  if (toolWindowManager.getToolWindowIds().toList().contains(actionId)) {
//    toolWindowManager.unregisterToolWindow(actionId)
//  }
//  toolWindowManager.registerToolWindow(actionId, new JTextArea("vaspdo fasd fp9as"), ToolWindowAnchor.BOTTOM)


  def actionManager = ActionManager.instance
  if (actionManager.getActionIds("").toList().contains(actionId)) {
    actionManager.unregisterAction(actionId)
  }

  def action = new AnAction() {
    @Override
    void actionPerformed(AnActionEvent e) {
      catchingAll {
        JBPopupFactory.instance.createActionGroupPopup("Open putty", createActions(new DefaultActionGroup(), sshConnections),
                e.dataContext, ActionSelectionAid.SPEEDSEARCH, true).showCenteredInCurrentWindow(e.project());
      }
    }
  }
  KeymapManager.instance.activeKeymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke("alt shift V"), null))
  actionManager.registerAction(actionId, action)
}

catchingAll {
  registerAction()
  showPopup("evaled")
}

