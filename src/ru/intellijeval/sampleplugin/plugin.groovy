package ru.intellijeval.sampleplugin

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.Icon
import javax.swing.KeyStroke
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid

//import static ru.beans.Bean.*

//-- classpath: c:/work/zz_misc/groovy-beans/target/gbeans-0.0-SNAPSHOT.jar

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

  Icon ICON = IconLoader.getIcon("/fileTypes/text.png");
  def action = new AnAction() {
    @Override
    void actionPerformed(AnActionEvent e) {

      DefaultActionGroup actionGroup = new DefaultActionGroup();

      Project project = e.project();
      3.times {
        actionGroup.add(new AnAction(it.toString(), "Open " + it, ICON) {
          @Override
          public void actionPerformed(AnActionEvent e2) {
            Runtime.runtime.exec("\"c:/Program Files (x86)/putty/putty.exe\" -load \"lonhngprod04\"")
            showPopup("myaaaaaaaaaaaaaaaaaaaaaaaaaaaAction")
          }
        });
      }
      def group2 = new DefaultActionGroup("aa", true);
      group2.add(new AnAction("!!!!!!") {
        @Override
        void actionPerformed(AnActionEvent aae) {
        }
      })
      actionGroup.add(group2)

      JBPopupFactory.instance.createActionGroupPopup("pupop", actionGroup, e.dataContext, ActionSelectionAid.SPEEDSEARCH, true).showCenteredInCurrentWindow(project);

//      showPopup("myAction !!! ${MyUtil.sayHi()} ${bean([a: 123])}")
//      showPopup("myaaaaaaaaaaaaaaaaaaaaaaaaaaaAction !!! ${MyUtil.sayHi()} ${e.project()}")
    }
  }
  KeymapManager.instance.activeKeymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke("alt shift W"), null))
  actionManager.registerAction(actionId, action)
}

AnActionEvent.metaClass.project = { delegate.getData(PlatformDataKeys.PROJECT) }
AnActionEvent.metaClass.editor = { delegate.getData(PlatformDataKeys.EDITOR) }
AnActionEvent.metaClass.fileText = { delegate.getData(PlatformDataKeys.FILE_TEXT) }

try {
  registerAction()
  showPopup("evaled")
} catch (Exception e) {
  e.printStackTrace()
}
