package ru.intellijeval.pluginexamples.helloEditorStringManipulation

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapManager
import ru.intellijeval.AbstractStringManipAction

import javax.swing.*

static show(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION) {
    SwingUtilities.invokeLater({
        def notification = new Notification("", title, htmlBody, notificationType)
        ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
    } as Runnable)
}

class MyEditorAction extends AbstractStringManipAction {
    private Closure closure

    public MyEditorAction(Closure closure) {
        this.closure = closure
    }

    @Override
    String transform(String s) {
        return closure.call(s)
    }
}

static registerTextEditorAction(String actionId, String keyStroke = "", Closure closure) {
    def actionManager = ActionManager.instance
    def keymap = KeymapManager.instance.activeKeymap

    def alreadyRegistered = (actionManager.getAction(actionId) != null)
    if (alreadyRegistered) {
        keymap.removeAllActionShortcuts(actionId)
        actionManager.unregisterAction(actionId)
    }

    if (!keyStroke.empty) keymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke(keyStroke), null))

    actionManager.registerAction(actionId, new MyEditorAction(closure))

    show("Loaded '${actionId}'<br/>Select text in editor and press shift+alt+Q to run it")
}

registerTextEditorAction("HelloTextEditorReplace", "alt shift Q", { String s ->
    return s.toUpperCase()
})
