package ru.intellijeval.pluginexamples

import com.intellij.notification.*
import com.intellij.openapi.application.ApplicationManager

import javax.swing.SwingUtilities


static show(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION) {
    SwingUtilities.invokeLater({
        def notification = new Notification("", title, htmlBody, notificationType)
        ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
    } as Runnable)
}

show("Hello IntelliJ")
