package liveplugin.toolwindow.addplugin.git

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.remote.GitRememberedInputs
import liveplugin.toolwindow.addplugin.git.com.intellij.dvcs.ui.CloneDvcsDialog
import liveplugin.toolwindow.addplugin.git.com.intellij.dvcs.ui.CloneDvcsDialog.TestResult.SUCCESS
import java.io.File

class GitCloneDialog(project: Project): CloneDvcsDialog(project, GitVcs.NAME, GitUtil.DOT_GIT, null) {

    private val myGit = ServiceManager.getService(Git::class.java)

    override fun test(url: String): TestResult {
        val result = myGit.lsRemote(myProject, File("."), url)
        return if (result.success()) SUCCESS else TestResult(result.errorOutputAsJoinedString)
    }

    override fun getRememberedInputs() = GitRememberedInputs.getInstance()

    override fun getDimensionServiceKey() = "GitCloneDialog"

    override fun getHelpId() = "reference.VersionControl.Git.CloneRepository"
}