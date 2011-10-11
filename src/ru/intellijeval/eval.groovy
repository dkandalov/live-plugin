package ru.intellijeval

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.KeyStroke

def showPopup(String text) {
  def project = actionEvent.getRequiredData(PlatformDataKeys.PROJECT)
  ToolWindowManager.getInstance(project).notifyByBalloon(ToolWindowId.RUN, MessageType.INFO, text)
}
//showPopup("aaaaaaaaaaaa")

def registerAction() {
  def actionId = "myAction"

  def actionManager = ActionManager.instance
  if (actionManager.getActionIds("").toList().contains(actionId)) {
    actionManager.unregisterAction(actionId)
  }

  def action = new AnAction() {
    @Override
    void actionPerformed(AnActionEvent e) {
      showPopup("myAction !!!")
    }
  }
  KeymapManager.instance.activeKeymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke("alt shift W"), null))
  actionManager.registerAction(actionId, action)
}

try {
  registerAction()
  showPopup("evaled")
} catch (Exception e) {
  e.printStackTrace()
}
