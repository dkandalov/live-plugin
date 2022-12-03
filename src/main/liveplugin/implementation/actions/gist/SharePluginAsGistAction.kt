package liveplugin.implementation.actions.gist

import com.intellij.collaboration.auth.AccountManagerBase
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationListener.URL_OPENING_LISTENER
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.EMPTY_LABEL
import com.intellij.ui.dsl.builder.RowLayout.LABEL_ALIGNED
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.proxy.CommonProxy
import kotlinx.coroutines.runBlocking
import liveplugin.implementation.actions.gist.GistApi.*
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.common.IdeUtil.showError
import liveplugin.implementation.common.livePluginNotificationGroup
import liveplugin.implementation.livePlugins
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.net.URL
import javax.swing.JTextArea

class SharePluginAsGistAction : AnAction("Share as Gist", "Share as plugin files as a Gist", AllIcons.Vcs.Vendors.Github), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val livePlugin = event.livePlugins().firstOrNull() ?: return
        val account = GithubAuthenticationManager.getInstance().getSingleOrDefaultAccount(project) ?: return project.showError("Please configure Github account to share gists.")
        val authToken = runBlocking { githubAccountManager().findCredentials(account) } ?: return project.showError("Couldn't get authentication for ${account.name}")

        val dialog = GithubCreateGistDialog(project)
        if (!dialog.showAndGet()) return

        object : Task.Backgroundable(project, "Creating gist…", true, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val gist = Gist(
                        description = dialog.description,
                        public = !dialog.isSecret,
                        files = livePlugin.path.allFiles().associate { it.name to GistFile(it.readText()) }
                    )
                    val proxy = CommonProxy.getInstance().select(URL("https://api.github.com/gists")).firstOrNull()
                    val newGist = GistApiHttp(proxy).create(gist, authToken)
                    runLaterOnEdt {
                        if (dialog.isCopyURL) {
                            val stringSelection = StringSelection(newGist.htmlUrl)
                            CopyPasteManager.getInstance().setContents(stringSelection)
                        }
                        if (dialog.isOpenInBrowser) {
                            BrowserUtil.browse(newGist.htmlUrl)
                        } else {
                            @Suppress("DEPRECATION")
                            livePluginNotificationGroup.createNotification(
                                "Gist created successfully",
                                HtmlChunk.link(newGist.htmlUrl, "Your gist url").toString(),
                                INFORMATION
                            ).setListener(URL_OPENING_LISTENER).notify(project)
                        }
                    }
                } catch (e: IOException) {
                    runLaterOnEdt {
                        project.showError("Failed to share gist: ${e.message}", e)
                    }
                } catch (e: FailedRequest) {
                    runLaterOnEdt {
                        project.showError("Failed to share gist: ${e.message}", e)
                    }
                }
            }
        }.queue()
    }

    @Suppress("UNCHECKED_CAST")
    private fun githubAccountManager() =
        ApplicationManager.getApplication().getService(Class.forName("org.jetbrains.plugins.github.authentication.accounts.GHAccountManager"))
            as AccountManagerBase<GithubAccount, String>

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.livePlugins().isNotEmpty()
    }

    private class GithubCreateGistDialog(project: Project) : DialogWrapper(project, true) {
        private val descriptionField = JTextArea()
        private val secretCheckBox = JBCheckBox("Secret", false)
        private val browserCheckBox = JBCheckBox("Open in browser", true)
        private val copyLinkCheckBox = JBCheckBox("Copy URL", false)

        val description: String get() = descriptionField.text
        val isSecret: Boolean get() = secretCheckBox.isSelected
        val isOpenInBrowser: Boolean get() = browserCheckBox.isSelected
        val isCopyURL: Boolean get() = copyLinkCheckBox.isSelected

        init {
            title = "Share Plugin as Gist"
            init()
        }

        override fun createCenterPanel() = panel {
            row {
                label("Description:").verticalAlign(VerticalAlign.TOP)
                scrollCell(descriptionField).horizontalAlign(HorizontalAlign.FILL).verticalAlign(VerticalAlign.FILL)
            }.layout(LABEL_ALIGNED).resizableRow()

            row(EMPTY_LABEL) {
                cell(secretCheckBox)
                cell(browserCheckBox)
                cell(copyLinkCheckBox)
            }
        }

        override fun getDimensionServiceKey() = "LivePlugin.CreateGistDialog"
        override fun getPreferredFocusedComponent() = descriptionField
    }
}